package com.feerlessleadr.embidextrous.labelmaker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feerlessleadr.embidextrous.AppContainer
import com.feerlessleadr.embidextrous.data.ApiClient
import com.feerlessleadr.embidextrous.data.ApiException
import com.feerlessleadr.embidextrous.data.LabelMakerParser
import com.feerlessleadr.embidextrous.data.LmAlbum
import com.feerlessleadr.embidextrous.data.LmAlbumDetail
import com.feerlessleadr.embidextrous.data.LmCandidate
import com.feerlessleadr.embidextrous.data.LmDuplicateChoice
import com.feerlessleadr.embidextrous.data.LmGate
import com.feerlessleadr.embidextrous.data.LmPreview
import com.feerlessleadr.embidextrous.data.LmStatus
import com.feerlessleadr.embidextrous.push.EmbyTopic
import com.feerlessleadr.embidextrous.push.PushEvent
import com.feerlessleadr.embidextrous.push.keepFresh
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

/** Which albums the list shows. "Needs review" is the starting point: it is the work that waits for a person. */
enum class LmFilter(val label: String, val states: List<String>) {
    Review("Needs review", listOf("NeedsReview")),
    Ready("Ready", listOf("Ready")),
    NoMatch("No match", listOf("NoMatch")),
    Waiting("Waiting", listOf("Waiting", "Applying")),
    Failed("Failed", listOf("Failed")),
    Duplicate("Already in library", listOf("Duplicate")),
    Skipped("Left alone", listOf("Skipped")),
    Done("Filed", listOf("Done")),
    All("All", emptyList()),
    ;

    /** How many albums wait in this view, from the plugin's counts. */
    fun count(status: LmStatus?): Int? = status?.let { s -> if (states.isEmpty()) s.counts.values.sum() else states.sumOf { s.count(it) } }
}

/** The album that is open, with what the plugin said about it and what the person has tried on it. */
data class LmDetailState(
    val album: LmAlbum,
    val detail: LmAlbumDetail? = null,
    val loading: Boolean = true,
    /** Editions found by a search or a pasted link; they replace the plugin's own list while they are set. */
    val found: List<LmCandidate>? = null,
    val foundMessage: String = "",
    val preview: LmPreview? = null,
    val previewChoice: LmDuplicateChoice = LmDuplicateChoice.Ask,
    val busy: Boolean = false,
)

data class LabelMakerState(
    val gate: LmGate = LmGate.Checking,
    val status: LmStatus? = null,
    val filter: LmFilter = LmFilter.Review,
    val laneId: String = "",
    val search: String = "",
    val albums: List<LmAlbum> = emptyList(),
    val total: Int = 0,
    val loading: Boolean = false,
    val detail: LmDetailState? = null,
)

/**
 * The Label Maker tab: the live status of the music ingest plugin, the list of albums with the filters that matter (starting with the ones that need a
 * person), and the review of one album (pick the edition, see what it would do, file it, or set it aside). Nothing is trusted from memory: after every
 * action the plugin is asked again. The status arrives by push (plugin 0.1.11.0 and newer), otherwise by asking every 20 seconds.
 */
class LabelMakerViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(LabelMakerState())
    val state: StateFlow<LabelMakerState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var listJob: Job? = null
    private var detailJob: Job? = null

    fun load() {
        viewModelScope.launch {
            val api = container.api() ?: return@launch
            val gate = try {
                LabelMakerParser.gate(api.lmInfo().also { info -> featuresSeen = info.features })
            } catch (e: ApiException) {
                when {
                    e.isUnauthorized -> { container.signOut(); return@launch }
                    e.status == 404 -> LmGate.NotInstalled
                    else -> LmGate.Unreachable(e.message.orEmpty())
                }
            }
            _state.update { it.copy(gate = gate) }
            if (gate is LmGate.Ready) {
                refreshStatus(api)
                reloadList()
            }
        }
    }

    private var featuresSeen: Set<String> = emptySet()

    /**
     * Keeps the tab current while it is on screen: the status is pushed when the plugin supports it, otherwise asked for every 20 seconds. Runs until
     * cancelled (the screen leaves the foreground), and gives the push topic back then.
     */
    suspend fun live() {
        while (_state.value.gate !is LmGate.Ready) delay(500)
        if ("statuspush" in featuresSeen) {
            container.push.keepFresh(
                topics = arrayOf(EmbyTopic.LabelMakerStatus),
                fallbackEveryMs = POLL_MS,
                accept = { it.type == EmbyTopic.LabelMakerStatus.message },
                onPush = ::onPushed,
                refresh = { refreshQuietly() },
            )
        } else {
            while (true) {
                delay(POLL_MS)
                refreshQuietly()
            }
        }
    }

    private suspend fun refreshQuietly() {
        val api = container.api() ?: return
        try {
            applyStatus(api.lmStatus())
        } catch (e: ApiException) {
            if (e.isUnauthorized) container.signOut()
        }
    }

    /** A pushed status (or heartbeat). Only a newer one changes anything. */
    private suspend fun onPushed(event: PushEvent) {
        val status = (event.data as? JsonObject)?.let { LabelMakerParser.status(it) } ?: return
        if (status.error != null) {
            refreshQuietly()
            return
        }
        applyStatus(status)
    }

    /** Takes a newer status in; when something changed, the list and an open album are read again. */
    private fun applyStatus(status: LmStatus) {
        val old = _state.value.status
        if (old != null && status.version == old.version && status == old) return
        _state.update { it.copy(status = status) }
        if (old == null || status.version != old.version) {
            reloadList(quiet = true)
            reloadOpenAlbum()
        }
    }

    private suspend fun refreshStatus(api: ApiClient) {
        try {
            _state.update { it.copy(status = api.lmStatus()) }
        } catch (e: ApiException) {
            failed(e)
        }
    }

    // ----- what the list shows ------------------------------------------------------------------------------------

    fun setFilter(filter: LmFilter) {
        _state.update { it.copy(filter = filter) }
        reloadList()
    }

    fun setLane(laneId: String) {
        _state.update { it.copy(laneId = laneId) }
        reloadList()
    }

    fun setSearch(text: String) {
        _state.update { it.copy(search = text) }
        reloadList(debounce = true)
    }

    private fun reloadList(debounce: Boolean = false, quiet: Boolean = false) {
        listJob?.cancel()
        listJob = viewModelScope.launch {
            if (debounce) delay(350)
            val api = container.api() ?: return@launch
            if (!quiet) _state.update { it.copy(loading = true) }
            try {
                val s = _state.value
                val shown = if (quiet) s.albums.size.coerceIn(PAGE, MAX_QUIET) else PAGE
                val (total, albums) = api.lmAlbums(s.filter.states, s.laneId, s.search.trim(), 0, shown)
                _state.update { it.copy(albums = albums, total = total, loading = false) }
            } catch (e: ApiException) {
                _state.update { it.copy(loading = false) }
                if (!quiet) failed(e)
            }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.albums.size >= s.total) return
        viewModelScope.launch {
            val api = container.api() ?: return@launch
            _state.update { it.copy(loading = true) }
            try {
                val (total, more) = api.lmAlbums(s.filter.states, s.laneId, s.search.trim(), s.albums.size, PAGE)
                _state.update { cur -> cur.copy(albums = cur.albums + more.filter { m -> cur.albums.none { it.id == m.id } }, total = total, loading = false) }
            } catch (e: ApiException) {
                _state.update { it.copy(loading = false) }
                failed(e)
            }
        }
    }

    // ----- the plugin's switches ------------------------------------------------------------------------------------

    fun checkNow() = act("Checking the inbox…") { it.lmRun() }

    fun setPaused(paused: Boolean) = act(if (paused) "Paused." else "Resumed.") { it.lmPause(paused) }

    fun setDryRun(dryRun: Boolean) = act(if (dryRun) "Dry run is on: Label Maker only reports what it would do." else "Dry run is off: Ready albums will be filed.") { it.lmDryRun(dryRun) }

    private fun act(message: String, call: suspend (ApiClient) -> Unit) {
        viewModelScope.launch {
            val api = container.api() ?: return@launch
            try {
                call(api)
                _messages.tryEmit(message)
                refreshStatus(api)
                reloadList(quiet = true)
            } catch (e: ApiException) {
                failed(e)
            }
        }
    }

    // ----- one album ----------------------------------------------------------------------------------------------

    fun openAlbum(album: LmAlbum) {
        _state.update { it.copy(detail = LmDetailState(album)) }
        loadDetail(album.id)
    }

    fun closeAlbum() {
        detailJob?.cancel()
        _state.update { it.copy(detail = null) }
    }

    private fun loadDetail(id: String) {
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            val api = container.api() ?: return@launch
            try {
                val detail = api.lmAlbum(id)
                _state.update { s -> s.detail?.takeIf { it.album.id == id }?.let { s.copy(detail = it.copy(album = detail.album, detail = detail, loading = false)) } ?: s }
            } catch (e: ApiException) {
                if (e.isUnauthorized) { container.signOut(); return@launch }
                // A 400 means the plugin no longer knows the album (it was filed and cleaned away): close the page and read the list again.
                _state.update { s -> if (s.detail?.album?.id == id) s.copy(detail = null) else s }
                _messages.tryEmit(e.message.orEmpty().ifEmpty { "That album is no longer in the list." })
                reloadList(quiet = true)
            }
        }
    }

    /** After a status change: the open album is read again, unless the person is in the middle of something with it. */
    private fun reloadOpenAlbum() {
        val d = _state.value.detail ?: return
        if (d.busy) return
        loadDetail(d.album.id)
    }

    private fun updateDetail(change: (LmDetailState) -> LmDetailState) {
        _state.update { s -> s.detail?.let { s.copy(detail = change(it)) } ?: s }
    }

    private fun detailApi(): Pair<ApiClient, LmDetailState>? {
        val api = container.api() ?: return null
        val d = _state.value.detail ?: return null
        return api to d
    }

    /** Asks what using this edition would do. [choice] is what to do about files the library already has. */
    fun preview(releaseId: String, choice: LmDuplicateChoice = LmDuplicateChoice.Ask) {
        viewModelScope.launch {
            val (api, d) = detailApi() ?: return@launch
            updateDetail { it.copy(busy = true) }
            try {
                val p = api.lmPreview(d.album.id, releaseId, choice.wire)
                updateDetail { it.copy(preview = p, previewChoice = choice, busy = false) }
                if (!p.success && p.message.isNotEmpty()) _messages.tryEmit(p.message)
            } catch (e: ApiException) {
                updateDetail { it.copy(busy = false) }
                failed(e)
            }
        }
    }

    fun clearPreview() = updateDetail { it.copy(preview = null, previewChoice = LmDuplicateChoice.Ask) }

    /** Files the album with the previewed edition. */
    fun applyPreview() {
        val p = _state.value.detail?.preview ?: return
        val choice = _state.value.detail?.previewChoice ?: LmDuplicateChoice.Ask
        viewModelScope.launch {
            val (api, d) = detailApi() ?: return@launch
            updateDetail { it.copy(busy = true) }
            try {
                val r = api.lmApply(d.album.id, p.releaseId, choice.wire)
                _messages.tryEmit(r.message.ifEmpty { if (r.success) "Done." else "That did not work." })
                updateDetail { if (r.success) it.copy(busy = false, preview = null, found = null, foundMessage = "") else it.copy(busy = false) }
                afterAction(api, d.album.id)
            } catch (e: ApiException) {
                updateDetail { it.copy(busy = false) }
                failed(e)
            }
        }
    }

    fun search(artist: String, album: String) = lookup { api, id -> api.lmSearch(id, artist.trim(), album.trim()) }

    fun resolve(text: String) = lookup { api, id -> api.lmResolve(id, text.trim()) }

    private fun lookup(call: suspend (ApiClient, String) -> com.feerlessleadr.embidextrous.data.LmSearchResult) {
        viewModelScope.launch {
            val (api, d) = detailApi() ?: return@launch
            updateDetail { it.copy(busy = true) }
            try {
                val r = call(api, d.album.id)
                updateDetail { it.copy(busy = false, found = r.candidates, foundMessage = r.message, preview = null) }
                if (!r.success && r.message.isNotEmpty()) _messages.tryEmit(r.message)
            } catch (e: ApiException) {
                updateDetail { it.copy(busy = false) }
                failed(e)
            }
        }
    }

    fun clearFound() = updateDetail { it.copy(found = null, foundMessage = "") }

    /** One of the plain actions: KeepTags, Skip, Recheck, Discard, Restore, Undo or Split. */
    fun action(action: String) {
        viewModelScope.launch {
            val (api, d) = detailApi() ?: return@launch
            updateDetail { it.copy(busy = true) }
            try {
                val r = api.lmAction(d.album.id, action)
                _messages.tryEmit(r.message.ifEmpty { if (r.success) "Done." else "That did not work." })
                // The editions the person searched for belong to the album as it was: after Restore, Check again or Undo they would be stale.
                updateDetail { if (r.success) it.copy(busy = false, found = null, foundMessage = "") else it.copy(busy = false) }
                afterAction(api, d.album.id)
            } catch (e: ApiException) {
                updateDetail { it.copy(busy = false) }
                failed(e)
            }
        }
    }

    private suspend fun afterAction(api: ApiClient, id: String) {
        refreshStatus(api)
        reloadList(quiet = true)
        loadDetail(id)
    }

    private suspend fun failed(e: ApiException) {
        if (e.isUnauthorized) {
            container.signOut()
        } else {
            _messages.tryEmit(e.message.orEmpty().ifEmpty { "Something went wrong." })
        }
    }

    companion object {
        const val PAGE = 30
        const val MAX_QUIET = 200
        const val POLL_MS = 20_000L
    }
}
