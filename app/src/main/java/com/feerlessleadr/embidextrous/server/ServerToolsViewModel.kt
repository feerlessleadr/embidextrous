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
import com.feerlessleadr.embidextrous.data.ServerTask
import kotlinx.coroutines.async
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

    // ----- loading -----------------------------------------------------------------------------------------

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
