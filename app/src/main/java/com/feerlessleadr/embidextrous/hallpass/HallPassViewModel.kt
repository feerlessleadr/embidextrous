package com.feerlessleadr.embidextrous.hallpass

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feerlessleadr.embidextrous.AppContainer
import com.feerlessleadr.embidextrous.data.ApiClient
import com.feerlessleadr.embidextrous.data.ApiException
import com.feerlessleadr.embidextrous.data.HpAlbum
import com.feerlessleadr.embidextrous.data.HpArtist
import com.feerlessleadr.embidextrous.data.HpGate
import com.feerlessleadr.embidextrous.data.HpPreview
import com.feerlessleadr.embidextrous.data.HpRow
import com.feerlessleadr.embidextrous.data.HpStatus
import com.feerlessleadr.embidextrous.data.HallPassParser
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

/** The filters of the review list: which decisions to show and a search text. "Review" (needs a look) is the starting point. */
enum class HpFilter(val label: String, val decision: String) {
    Review("Needs review", "Review"),
    Explicit("Explicit", "Explicit"),
    LikelyClean("Likely clean", "LikelyClean"),
    Clean("Clean", "Clean"),
    All("All", ""),
}

/** Why a track is in review (only used with the "Needs review" filter). The plugin's words: NoEvidence, Suspected, Conflict, WeakClean. */
enum class HpReason(val label: String, val key: String) {
    Any("Any reason", ""),
    NoEvidence("No evidence", "NoEvidence"),
    Suspected("Some sign", "Suspected"),
    Conflict("Sources disagree", "Conflict"),
    WeakClean("Faint sign of clean", "WeakClean"),
}

/** What the list shows: single tracks, albums or artists (one decision covers a whole album or artist). */
enum class HpMode { Tracks, Albums, Artists }

/** The order of the list: by name, or the most played first (what the family actually listens to gets decided first). */
enum class HpSort(val label: String, val key: String) {
    Name("A to Z", ""),
    Plays("Most played", "plays"),
}

/** The album or artist the person opened from the Albums or Artists list: the track list then shows only that. A null [album] means all of the artist's albums. */
data class HpScope(val artist: String, val album: String?)

data class HallPassState(
    val gate: HpGate = HpGate.Checking,
    val status: HpStatus? = null,
    val filter: HpFilter = HpFilter.Review,
    val reason: HpReason = HpReason.Any,
    val sort: HpSort = HpSort.Name,
    val search: String = "",
    val mode: HpMode = HpMode.Tracks,
    val scope: HpScope? = null,
    val rows: List<HpRow> = emptyList(),
    val albums: List<HpAlbum> = emptyList(),
    val artists: List<HpArtist> = emptyList(),
    val total: Int = 0,
    val loading: Boolean = false,
    val preview: HpPreview? = null,
    /** True while the swipe review is open: one track at a time, swipe right for clean and left for explicit. */
    val swipe: Boolean = false,
    /** How many decisions can still be taken back in the swipe review. */
    val undoable: Int = 0,
    /** Decisions made in this swipe review. */
    val swiped: Int = 0,
) {
    /** True when the list shows groups (albums or artists) instead of tracks. */
    val grouped: Boolean get() = scope == null && mode != HpMode.Tracks
}

/**
 * The Hall Pass tab: the review of the music tracks the Hall Pass plugin could not decide, with the decisions that need a person (mark a track, a whole
 * album or a whole artist explicit or clean, one at a time by swiping, or everything in the current view at once), a scan button and the apply step.
 * Everything is asked of the plugin again after each action rather than trusted.
 */
class HallPassViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(HallPassState())
    val state: StateFlow<HallPassState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var pollJob: Job? = null
    private var listJob: Job? = null

    /** Tracks decided in the swipe review that the list may still contain (the plugin's answer can lag a moment behind). */
    private val decidedInSwipe = mutableSetOf<Long>()
    private val undoStack = ArrayDeque<Pair<HpRow, String>>()

    fun load() {
        viewModelScope.launch {
            val api = container.api() ?: return@launch
            val gate = try {
                HallPassParser.gate(api.hpInfo())
            } catch (e: ApiException) {
                when {
                    e.isUnauthorized -> { container.signOut(); return@launch }
                    e.status == 404 -> HpGate.NotInstalled
                    else -> HpGate.Unreachable(e.message.orEmpty())
                }
            }
            _state.update { it.copy(gate = gate) }
            if (gate is HpGate.Ready) {
                refreshStatus(api)
                reloadList()
            }
        }
    }

    private suspend fun refreshStatus(api: ApiClient) {
        try {
            val status = api.hpStatus()
            _state.update { it.copy(status = status) }
            if (status.scan.isRunning || status.write.isRunning) poll()
        } catch (e: ApiException) {
            failed(e)
        }
    }

    /** While a scan or an apply runs, looks at the status every 2 seconds; when it ends, the list is read again. */
    private fun poll() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            val api = container.api() ?: return@launch
            while (true) {
                delay(2000)
                val status = try {
                    api.hpStatus()
                } catch (e: ApiException) {
                    failed(e)
                    return@launch
                }
                _state.update { it.copy(status = status) }
                if (!status.scan.isRunning && !status.write.isRunning) {
                    reloadList()
                    return@launch
                }
            }
        }
    }

    // ----- what the list shows ------------------------------------------------------------------------------------

    fun setFilter(filter: HpFilter) {
        _state.update { it.copy(filter = filter, reason = HpReason.Any, scope = null) }
        reloadList()
    }

    fun setReason(reason: HpReason) {
        _state.update { it.copy(reason = reason, scope = null) }
        reloadList()
    }

    fun setSort(sort: HpSort) {
        _state.update { it.copy(sort = sort) }
        reloadList()
    }

    fun setSearch(text: String) {
        _state.update { it.copy(search = text) }
        reloadList(debounce = true)
    }

    fun setMode(mode: HpMode) {
        _state.update { it.copy(mode = mode, scope = null) }
        reloadList()
    }

    fun openAlbum(album: HpAlbum) {
        _state.update { it.copy(scope = HpScope(album.artist, album.album), mode = HpMode.Tracks) }
        reloadList()
    }

    fun openArtist(artist: HpArtist) {
        _state.update { it.copy(scope = HpScope(artist.artist, null), mode = HpMode.Tracks) }
        reloadList()
    }

    fun closeScope() {
        _state.update { it.copy(scope = null) }
        reloadList()
    }

    private suspend fun fetchRows(api: ApiClient, s: HallPassState, skip: Int, take: Int): Pair<Int, List<HpRow>> {
        val scope = s.scope
        return api.hpReview(
            s.filter.decision, s.reason.key, s.search.trim(), scope?.artist, scope?.album, scope?.album != null && scope.album.isEmpty(), s.sort.key, skip, take,
        )
    }

    private fun reloadList(debounce: Boolean = false) {
        listJob?.cancel()
        listJob = viewModelScope.launch {
            if (debounce) delay(350)
            val api = container.api() ?: return@launch
            _state.update { it.copy(loading = true) }
            try {
                val s = _state.value
                when {
                    s.grouped && s.mode == HpMode.Albums -> {
                        val (total, albums) = api.hpAlbums(s.filter.decision, s.reason.key, s.search.trim(), s.sort.key, 0, PAGE)
                        _state.update { it.copy(albums = albums, artists = emptyList(), rows = emptyList(), total = total, loading = false) }
                    }
                    s.grouped -> {
                        val (total, artists) = api.hpArtists(s.filter.decision, s.reason.key, s.search.trim(), s.sort.key, 0, PAGE)
                        _state.update { it.copy(artists = artists, albums = emptyList(), rows = emptyList(), total = total, loading = false) }
                    }
                    else -> {
                        val (total, rows) = fetchRows(api, s, 0, PAGE)
                        _state.update { it.copy(rows = rows.filter { r -> r.id !in decidedInSwipe }, albums = emptyList(), artists = emptyList(), total = total, loading = false) }
                    }
                }
            } catch (e: ApiException) {
                _state.update { it.copy(loading = false) }
                failed(e)
            }
        }
    }

    /** Adds the next page to what is shown. */
    fun loadMore() {
        val s = _state.value
        if (s.loading || s.swipe) return
        val shown = when {
            s.grouped && s.mode == HpMode.Albums -> s.albums.size
            s.grouped -> s.artists.size
            else -> s.rows.size
        }
        if (shown >= s.total) return
        viewModelScope.launch {
            val api = container.api() ?: return@launch
            _state.update { it.copy(loading = true) }
            try {
                when {
                    s.grouped && s.mode == HpMode.Albums -> {
                        val (total, more) = api.hpAlbums(s.filter.decision, s.reason.key, s.search.trim(), s.sort.key, shown, PAGE)
                        _state.update { it.copy(albums = it.albums + more, total = total, loading = false) }
                    }
                    s.grouped -> {
                        val (total, more) = api.hpArtists(s.filter.decision, s.reason.key, s.search.trim(), s.sort.key, shown, PAGE)
                        _state.update { it.copy(artists = it.artists + more, total = total, loading = false) }
                    }
                    else -> {
                        val (total, more) = fetchRows(api, s, shown, PAGE)
                        _state.update { it.copy(rows = it.rows + more.filter { r -> it.rows.none { old -> old.id == r.id } }, total = total, loading = false) }
                    }
                }
            } catch (e: ApiException) {
                _state.update { it.copy(loading = false) }
                failed(e)
            }
        }
    }

    // ----- decisions ------------------------------------------------------------------------------------------------

    /** [decision]: "explicit", "clean" or "clearOverride". */
    fun decide(row: HpRow, decision: String) {
        viewModelScope.launch {
            val api = container.api() ?: return@launch
            try {
                api.hpDecide(listOf(row.id), decision)
                _messages.tryEmit(
                    when (decision) {
                        "explicit" -> "Marked explicit: ${row.name}"
                        "clean" -> "Marked clean: ${row.name}"
                        else -> "Manual decision cleared: ${row.name}"
                    },
                )
                refreshStatus(api)
                reloadList()
            } catch (e: ApiException) {
                failed(e)
            }
        }
    }

    /** One decision for everything that matches the filters and the narrowing: an album ([album] set), an artist ([album] null) or the whole view (both null). */
    fun decideMatching(decision: String, artist: String?, album: String?) {
        viewModelScope.launch {
            val api = container.api() ?: return@launch
            val s = _state.value
            try {
                val r = api.hpDecideMatching(decision, s.filter.decision, s.reason.key, s.search.trim(), artist, album)
                _messages.tryEmit("${r.changed} track(s) marked ${if (decision == "explicit") "explicit" else "clean"}." + if (r.truncated) " That was the first ${r.matched}; do it again for the rest." else "")
                refreshStatus(api)
                reloadList()
            } catch (e: ApiException) {
                failed(e)
            }
        }
    }

    /** Everything the current list matches (the opened album or artist, the filters and the search), however many pages. */
    fun decideView(decision: String) {
        val scope = _state.value.scope
        decideMatching(decision, scope?.artist, scope?.album)
    }

    // ----- the swipe review ---------------------------------------------------------------------------------------

    fun startSwipe() {
        decidedInSwipe.clear()
        undoStack.clear()
        _state.update { it.copy(swipe = true, undoable = 0, swiped = 0) }
    }

    fun stopSwipe() {
        _state.update { it.copy(swipe = false) }
        decidedInSwipe.clear()
        undoStack.clear()
        viewModelScope.launch {
            val api = container.api() ?: return@launch
            refreshStatus(api)
            reloadList()
        }
    }

    /** Takes the first track off the list at once and tells the plugin in the background; a failure puts it back. */
    fun swipeDecide(row: HpRow, decision: String) {
        decidedInSwipe.add(row.id)
        undoStack.addLast(row to decision)
        _state.update { it.copy(rows = it.rows.filter { r -> r.id != row.id }, total = maxOf(0, it.total - 1), undoable = undoStack.size, swiped = it.swiped + 1) }
        viewModelScope.launch {
            val api = container.api() ?: return@launch
            try {
                api.hpDecide(listOf(row.id), decision)
            } catch (e: ApiException) {
                decidedInSwipe.remove(row.id)
                undoStack.removeAll { it.first.id == row.id }
                _state.update { it.copy(rows = listOf(row) + it.rows, total = it.total + 1, undoable = undoStack.size, swiped = maxOf(0, it.swiped - 1)) }
                failed(e)
            }
        }
        refillIfLow()
    }

    /** Puts the track at the end of the queue (decided later), without telling the plugin anything. */
    fun swipeSkip(row: HpRow) {
        _state.update { it.copy(rows = it.rows.filter { r -> r.id != row.id } + row) }
    }

    fun swipeUndo() {
        val last = undoStack.removeLastOrNull() ?: return
        val (row, _) = last
        decidedInSwipe.remove(row.id)
        _state.update { it.copy(rows = listOf(row) + it.rows.filter { r -> r.id != row.id }, total = it.total + 1, undoable = undoStack.size, swiped = maxOf(0, it.swiped - 1)) }
        viewModelScope.launch {
            val api = container.api() ?: return@launch
            try {
                api.hpDecide(listOf(row.id), "clearOverride")
            } catch (e: ApiException) {
                failed(e)
            }
        }
    }

    /** When only a few tracks are left in memory but the plugin has more, asks for the next ones (decided tracks are left out). */
    private fun refillIfLow() {
        val s = _state.value
        if (s.loading || s.rows.size >= 6 || s.total <= s.rows.size) return
        viewModelScope.launch {
            val api = container.api() ?: return@launch
            _state.update { it.copy(loading = true) }
            try {
                val (total, fetched) = fetchRows(api, _state.value, 0, PAGE * 2)
                _state.update { cur ->
                    val have = cur.rows.map { it.id }.toSet()
                    cur.copy(
                        rows = cur.rows + fetched.filter { r -> r.id !in have && r.id !in decidedInSwipe },
                        total = maxOf(total - decidedInSwipe.size.coerceAtMost(total), cur.rows.size),
                        loading = false,
                    )
                }
            } catch (e: ApiException) {
                _state.update { it.copy(loading = false) }
                failed(e)
            }
        }
    }

    // ----- scan and apply -------------------------------------------------------------------------------------------

    fun scan(force: Boolean) {
        viewModelScope.launch {
            val api = container.api() ?: return@launch
            try {
                api.hpScan(force)
                _messages.tryEmit(if (force) "Scanning every track again…" else "Scan started…")
                refreshStatus(api)
                poll()
            } catch (e: ApiException) {
                failed(e)
            }
        }
    }

    fun cancelScan() {
        viewModelScope.launch {
            val api = container.api() ?: return@launch
            try {
                api.hpCancelScan()
                refreshStatus(api)
            } catch (e: ApiException) {
                failed(e)
            }
        }
    }

    /** Asks what Apply would change; the screen shows it and asks to confirm. */
    fun loadPreview() {
        viewModelScope.launch {
            val api = container.api() ?: return@launch
            try {
                _state.update { it.copy(preview = api.hpPreview()) }
            } catch (e: ApiException) {
                failed(e)
            }
        }
    }

    fun clearPreview() {
        _state.update { it.copy(preview = null) }
    }

    fun apply() {
        viewModelScope.launch {
            val api = container.api() ?: return@launch
            _state.update { it.copy(preview = null) }
            try {
                api.hpApply()
                _messages.tryEmit("Writing tags…")
                refreshStatus(api)
                poll()
            } catch (e: ApiException) {
                failed(e)
            }
        }
    }

    private suspend fun failed(e: ApiException) {
        if (e.isUnauthorized) {
            container.signOut()
        } else {
            _messages.tryEmit(e.message.orEmpty().ifEmpty { "Something went wrong." })
        }
    }

    companion object {
        const val PAGE = 50
    }
}
