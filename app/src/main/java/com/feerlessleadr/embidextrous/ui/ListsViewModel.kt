package com.feerlessleadr.embidextrous.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feerlessleadr.embidextrous.AppContainer
import com.feerlessleadr.embidextrous.data.ApiClient
import com.feerlessleadr.embidextrous.data.ApiException
import com.feerlessleadr.embidextrous.data.Catalog
import com.feerlessleadr.embidextrous.data.ListDraft
import com.feerlessleadr.embidextrous.data.RefreshStatus
import com.feerlessleadr.embidextrous.data.SmartListDoc
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.feerlessleadr.embidextrous.push.keepFresh

data class ListsUiState(
    val lists: List<SmartListDoc> = emptyList(),
    val catalog: Catalog? = null,
    val status: RefreshStatus = RefreshStatus.Empty,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    /** Lists with a call in flight: their buttons are disabled. */
    val busyIds: Set<String> = emptySet(),
    /** Lists deleted a moment ago: hidden, but still on the server until the undo time is up. */
    val hiddenIds: Set<String> = emptySet(),
    val query: String = "",
    val filter: ListFilter = ListFilter.All,
    val error: String? = null,
) {
    val visible: List<SmartListDoc> get() = visibleLists(lists, query, filter, status, hiddenIds)
}

/** How long a deleted list can still be brought back. */
const val UNDO_DELETE_MILLIS = 8_000L

class ListsViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(ListsUiState())
    val state: StateFlow<ListsUiState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _deleted = MutableSharedFlow<SmartListDoc>(extraBufferCapacity = 4)

    /** A list was deleted: the screen offers Undo for [UNDO_DELETE_MILLIS]. */
    val deleted: SharedFlow<SmartListDoc> = _deleted.asSharedFlow()

    private val pendingDeletes = mutableMapOf<String, Job>()
    private var pollJob: Job? = null

    init {
        load(Load.Initial)
        // The editor saved a list: show the change (and again shortly, once the server's first refresh has run).
        viewModelScope.launch {
            container.listsChanged.collect {
                load(Load.Silent)
                reloadSoon()
            }
        }
    }

    private enum class Load { Initial, Pull, Silent }

    fun reload() = load(Load.Pull)

    private fun load(mode: Load) {
        viewModelScope.launch {
            _state.update { it.copy(loading = mode == Load.Initial && it.lists.isEmpty(), refreshing = mode == Load.Pull, error = null) }
            val api = container.api() ?: return@launch
            try {
                val known = _state.value.catalog
                val loaded = coroutineScope {
                    val l = async { api.lists() }
                    val c = async { known ?: api.catalog() }
                    val s = async { runCatching { api.refreshStatus() }.getOrDefault(RefreshStatus.Empty) }
                    Triple(l.await(), c.await(), s.await())
                }
                _state.update {
                    it.copy(
                        lists = loaded.first.sortedBy { doc -> doc.name.lowercase() },
                        catalog = loaded.second,
                        status = loaded.third,
                        loading = false,
                        refreshing = false,
                    )
                }
                if (loaded.third.running.isNotEmpty()) pollWhileRunning()
            } catch (e: ApiException) {
                failed(e) { msg -> _state.update { it.copy(loading = false, refreshing = false, error = msg) } }
            }
        }
    }

    /** While a refresh runs on the server, look again every few seconds so its progress and result show up. */
    private fun pollWhileRunning() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            var rounds = 0
            while (_state.value.status.running.isNotEmpty() && rounds < 200) {
                delay(2_500)
                load(Load.Silent)
                rounds++
            }
        }
    }

    /**
     * Keeps the overview fresh while the Smart Lists tab is on screen. Emby pushes "library changed" when a list refresh rewrites a playlist or collection, which
     * is the cue to look again; the plugin offers no push for a refresh's progress, so that is still looked at every few seconds while one is running
     * (see pollWhileRunning). Only while the push is down is the overview asked for again by timer, every minute.
     */
    suspend fun keepFresh() = container.push.keepFresh(
        arrayOf(com.feerlessleadr.embidextrous.push.EmbyTopic.Events),
        fallbackEveryMs = 60_000,
        accept = { it.type == "LibraryChanged" },
        onPush = { load(Load.Silent) },
        refresh = { load(Load.Silent) },
    )

    fun setQuery(query: String) = _state.update { it.copy(query = query) }

    fun setFilter(filter: ListFilter) = _state.update { it.copy(filter = filter) }

    fun refreshList(doc: SmartListDoc) = act(doc.id, "Refresh started for ${doc.name}") { refresh(doc.id) }.also { reloadSoon() }

    fun refreshAll() {
        viewModelScope.launch {
            val api = container.api() ?: return@launch
            try {
                api.refreshAll()
                _messages.emit("Refresh started for all lists")
                reloadSoon()
            } catch (e: ApiException) {
                failed(e) { _messages.tryEmit(it) }
            }
        }
    }

    fun setEnabled(doc: SmartListDoc, enabled: Boolean) {
        act(doc.id, if (enabled) "${doc.name} enabled" else "${doc.name} disabled") {
            if (enabled) enable(doc.id) else disable(doc.id)
        }.also { reloadSoon() }
    }

    /**
     * Deletes a list after a short undo window: the list disappears at once, and is deleted on the server (with its
     * Emby playlist or collection) only when the time is up. Closing the app inside the window loses nothing.
     */
    fun delete(doc: SmartListDoc) {
        // A second delete ends the first one's window: the snackbar for it is replaced anyway.
        pendingDeletes.keys.toList().forEach { commitDelete(it) }
        _state.update { it.copy(hiddenIds = it.hiddenIds + doc.id) }
        pendingDeletes[doc.id] = viewModelScope.launch {
            delay(UNDO_DELETE_MILLIS + 500)
            commitDelete(doc.id)
        }
        _deleted.tryEmit(doc)
    }

    fun undoDelete(id: String) {
        pendingDeletes.remove(id)?.cancel()
        _state.update { it.copy(hiddenIds = it.hiddenIds - id) }
    }

    private fun commitDelete(id: String) {
        val job = pendingDeletes.remove(id) ?: return
        job.cancel()
        viewModelScope.launch {
            val api = container.api() ?: return@launch
            try {
                api.delete(id, deleteEmbyList = true)
            } catch (e: ApiException) {
                _state.update { it.copy(hiddenIds = it.hiddenIds - id) }
                failed(e) { _messages.tryEmit("Could not delete the list: $it") }
            }
            load(Load.Silent)
            _state.update { it.copy(hiddenIds = it.hiddenIds - id) }
        }
    }

    /** The list as JSON to hand to someone else (no ids, no users). */
    fun shareText(doc: SmartListDoc): String = ListDraft.from(doc).forSharing().toJson().toString()

    /** Runs one API call for a list, shows the outcome as a message and reloads the lists. */
    private fun act(id: String, success: String, call: suspend ApiClient.() -> Unit) {
        viewModelScope.launch {
            val api = container.api() ?: return@launch
            _state.update { it.copy(busyIds = it.busyIds + id) }
            try {
                api.call()
                _messages.emit(success)
            } catch (e: ApiException) {
                failed(e) { _messages.tryEmit(it) }
            } finally {
                _state.update { it.copy(busyIds = it.busyIds - id) }
            }
            load(Load.Silent)
        }
    }

    /** A refresh runs in the background on the server: look again after a few seconds. */
    private fun reloadSoon() {
        viewModelScope.launch {
            delay(1_500)
            load(Load.Silent)
            delay(4_500)
            load(Load.Silent)
        }
    }

    private fun failed(e: ApiException, show: (String) -> Unit) {
        if (e.isUnauthorized) {
            container.signOut()
        } else {
            show(e.message ?: "Something went wrong.")
        }
    }

    /** The cover picture of a list's Emby playlist or collection (cached), or null when it has none yet. */
    suspend fun cover(doc: SmartListDoc): android.graphics.Bitmap? {
        val itemId = doc.embyItemId ?: return null
        val api = container.api() ?: return null
        return container.covers.load(api, itemId, doc.lastRefreshed)
    }

    fun signOut() = container.signOut()
}