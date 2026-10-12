package com.feerlessleadr.embidextrous.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feerlessleadr.embidextrous.AppContainer
import com.feerlessleadr.embidextrous.data.ActivityEntry
import com.feerlessleadr.embidextrous.data.ApiClient
import com.feerlessleadr.embidextrous.data.ApiException
import com.feerlessleadr.embidextrous.data.DeviceInfo
import com.feerlessleadr.embidextrous.data.LibraryInfo
import com.feerlessleadr.embidextrous.data.LiveSession
import com.feerlessleadr.embidextrous.data.ServerAdminParser
import com.feerlessleadr.embidextrous.data.ServerTask
import com.feerlessleadr.embidextrous.push.EmbyTopic
import com.feerlessleadr.embidextrous.push.keepFresh
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One list of things from the server and how loading it went. */
data class Section<T>(val items: List<T> = emptyList(), val loaded: Boolean = false, val error: String? = null)

/** A library with its item count (null until the count has arrived or when the server would not say). */
data class LibraryRow(val library: LibraryInfo, val count: Int?)

/** How many activity entries one page holds. */
const val ACTIVITY_PAGE = 50

/** The most activity entries kept in memory (older ones are loaded again on request). */
const val MAX_ACTIVITY_KEPT = 500

/** The Server group's tools (live sessions, tasks, activity, devices) and Library's library list. Each section loads when its tab is shown; actions report back through [messages]. */
class ServerToolsViewModel(private val container: AppContainer) : ViewModel() {
    private val _live = MutableStateFlow(Section<LiveSession>())
    val live: StateFlow<Section<LiveSession>> = _live.asStateFlow()
    private val _tasks = MutableStateFlow(Section<ServerTask>())
    val tasks: StateFlow<Section<ServerTask>> = _tasks.asStateFlow()
    private val _activity = MutableStateFlow(Section<ActivityEntry>())
    val activity: StateFlow<Section<ActivityEntry>> = _activity.asStateFlow()
    private val _activityHasMore = MutableStateFlow(false)
    val activityHasMore: StateFlow<Boolean> = _activityHasMore.asStateFlow()
    private val _devices = MutableStateFlow(Section<DeviceInfo>())
    val devices: StateFlow<Section<DeviceInfo>> = _devices.asStateFlow()
    private val _libraries = MutableStateFlow(Section<LibraryRow>())
    val libraries: StateFlow<Section<LibraryRow>> = _libraries.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    // ----- staying up to date: Emby pushes, and these look again by asking only while it is not -----------------

    private val push get() = container.push
    @Volatile private var activityDirty = false
    private var activityJob: kotlinx.coroutines.Job? = null

    /** Live sessions: pushed every moment they change; asked for every 5 seconds only while the push is down. */
    suspend fun keepLiveFresh() = push.keepFresh(
        arrayOf(EmbyTopic.Sessions), fallbackEveryMs = 5_000,
        accept = { it.type == "Sessions" },
        onPush = { e -> e.data?.let { d -> _live.value = Section(ServerAdminParser.sessions(d.toString()), loaded = true) } },
        refresh = { loadLiveNow() },
    )

    /** Scheduled tasks: pushed while they run; asked for every 30 seconds only while the push is down. */
    suspend fun keepTasksFresh() = push.keepFresh(
        arrayOf(EmbyTopic.ScheduledTasks), fallbackEveryMs = 30_000,
        accept = { it.type == "ScheduledTasksInfo" },
        onPush = { e -> e.data?.let { d -> _tasks.value = Section(ServerAdminParser.tasks(d.toString()), loaded = true) } },
        refresh = { loadTasksNow() },
    )

    /** The activity log: new entries are pushed and put on top; the log is asked for again every 30 seconds only while the push is down. */
    suspend fun keepActivityFresh() = push.keepFresh(
        arrayOf(EmbyTopic.Activity), fallbackEveryMs = 30_000,
        accept = { it.type == "ActivityLogEntry" },
        onPush = { e ->
            val text = e.data?.toString().orEmpty()
            val array = if (text.trimStart().startsWith("[")) text else "[$text]"
            val fresh = ServerAdminParser.activity("""{"Items":$array}""")
            if (fresh.isNotEmpty()) {
                _activity.update { s -> Section((fresh + s.items).distinctBy { it.id }.sortedByDescending { it.id }.take(MAX_ACTIVITY_KEPT), loaded = true) }
            } else {
                // Emby says something was logged without saying what: fetch the newest entries now, and once more after a short pause if more
                // pushes arrived meanwhile (a burst of entries is one or two looks, never one per entry).
                activityDirty = true
                if (activityJob?.isActive != true) {
                    activityJob = viewModelScope.launch {
                        while (activityDirty) {
                            activityDirty = false
                            loadActivityNow()
                            delay(2_000)
                        }
                    }
                }
            }
        },
        refresh = { loadActivityNow() },
    )

    /** The device list: a sign-in shows up in the activity push, which is the cue to look again; asked for every minute only while the push is down. */
    suspend fun keepDevicesFresh() = push.keepFresh(
        arrayOf(EmbyTopic.Activity), fallbackEveryMs = 60_000,
        accept = { it.type == "ActivityLogEntry" && it.data?.toString()?.contains("user.authenticated") == true },
        onPush = { loadDevicesNow() },
        refresh = { loadDevicesNow() },
    )

    // ----- loading -----------------------------------------------------------------------------------------

    private suspend fun loadLiveNow() = loadNow(_live) { liveSessions() }
    private suspend fun loadTasksNow() = loadNow(_tasks) { allTasks() }
    private suspend fun loadDevicesNow() = loadNow(_devices) { devices() }

    private suspend fun loadActivityNow() {
        val api = container.api() ?: return
        try {
            val page = api.activity(0, ACTIVITY_PAGE)
            _activityHasMore.value = page.size >= ACTIVITY_PAGE || _activity.value.items.size > page.size
            _activity.update { s -> Section((page + s.items).distinctBy { it.id }.sortedByDescending { it.id }.take(MAX_ACTIVITY_KEPT), loaded = true) }
        } catch (e: ApiException) {
            failed(e) { m -> _activity.update { it.copy(loaded = true, error = m) } }
        }
    }

    private suspend fun <T> loadNow(target: MutableStateFlow<Section<T>>, call: suspend ApiClient.() -> List<T>) {
        val api = container.api() ?: return
        try {
            target.value = Section(api.call(), loaded = true)
        } catch (e: ApiException) {
            failed(e) { m -> target.update { it.copy(loaded = true, error = m) } }
        }
    }

    fun loadLive() = load(_live) { liveSessions() }

    fun loadTasks() = load(_tasks) { allTasks() }

    fun loadDevices() = load(_devices) { devices() }

    fun loadActivity(more: Boolean = false) {
        viewModelScope.launch {
            val api = container.api() ?: return@launch
            val start = if (more) _activity.value.items.size else 0
            try {
                val page = api.activity(start, ACTIVITY_PAGE)
                _activityHasMore.value = page.size >= ACTIVITY_PAGE
                _activity.update { Section(if (more) (it.items + page).distinctBy { e -> e.id } else page, loaded = true) }
            } catch (e: ApiException) {
                failed(e) { m -> _activity.update { it.copy(loaded = true, error = m) } }
            }
        }
    }

    fun loadLibraries() {
        viewModelScope.launch {
            val api = container.api() ?: return@launch
            try {
                val libs = api.mediaLibraries()
                val known = _libraries.value.items
                _libraries.value = Section(libs.map { lib -> LibraryRow(lib, known.firstOrNull { r -> r.library.id == lib.id }?.count) }, loaded = true)
                // Counts take a moment each, so they arrive after the list.
                coroutineScope {
                    libs.map { lib -> async { lib.id to runCatching { api.libraryCount(lib.id, lib.type) }.getOrNull() } }.forEach { job ->
                        val (id, count) = job.await()
                        _libraries.update { s -> s.copy(items = s.items.map { r -> if (r.library.id == id) r.copy(count = count) else r }) }
                    }
                }
            } catch (e: ApiException) {
                failed(e) { m -> _libraries.update { it.copy(loaded = true, error = m) } }
            }
        }
    }

    private fun <T> load(target: MutableStateFlow<Section<T>>, call: suspend ApiClient.() -> List<T>) {
        viewModelScope.launch {
            val api = container.api() ?: return@launch
            try {
                target.value = Section(api.call(), loaded = true)
            } catch (e: ApiException) {
                failed(e) { m -> target.update { it.copy(loaded = true, error = m) } }
            }
        }
    }

    // ----- actions: each says how it went and then looks again ---------------------------------------------

    fun stopPlayback(session: LiveSession) = act("Asked ${session.userName.ifEmpty { session.device }} to stop playing") {
        stopPlayback(session.id)
        loadLive()
    }

    fun runTask(task: ServerTask) = act("Started ${task.name}") {
        runTask(task.id)
        loadTasks()
    }

    fun stopTask(task: ServerTask) = act("Stopping ${task.name}") {
        stopTask(task.id)
        loadTasks()
    }

    /** The picture of a person, cached for the session; null when they have none. */
    suspend fun avatar(userId: String): android.graphics.Bitmap? = container.api()?.let { container.avatars.load(it, userId) }

    /** The poster of what someone is playing; null when it has none. */
    suspend fun poster(itemId: String): android.graphics.Bitmap? = container.api()?.let { container.covers.load(it, itemId, null) }

    suspend fun customName(device: DeviceInfo): String? = try {
        container.api()?.deviceCustomName(device.id)
    } catch (e: ApiException) {
        null
    }

    fun rename(device: DeviceInfo, name: String) = act(if (name.isBlank()) "Back to the name the device reports" else "Renamed to ${name.trim()}") {
        setDeviceCustomName(device.id, name)
        loadDevices()
    }

    fun revoke(device: DeviceInfo) = act("Signed ${device.name} out") {
        revokeDevice(device.id)
        loadDevices()
    }

    fun scanAll() = act("Scanning all libraries") { scanAllLibraries() }

    fun refresh(library: LibraryInfo) = act("Refreshing ${library.name}") { refreshLibrary(library.id) }

    fun restart() = act("Emby is restarting. It will be back in a minute or so.") { restartServer() }

    private fun act(success: String, call: suspend ApiClient.() -> Unit) {
        viewModelScope.launch {
            val api = container.api() ?: return@launch
            try {
                api.call()
                _messages.emit(success)
            } catch (e: ApiException) {
                failed(e) { _messages.tryEmit(it) }
            }
        }
    }

    private fun failed(e: ApiException, show: (String) -> Unit) {
        if (e.isUnauthorized) container.signOut() else show(e.message ?: "Something went wrong.")
    }
}
