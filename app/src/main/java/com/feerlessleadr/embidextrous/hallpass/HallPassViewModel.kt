package com.feerlessleadr.embidextrous.hallpass

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feerlessleadr.embidextrous.AppContainer
import com.feerlessleadr.embidextrous.data.ApiClient
import com.feerlessleadr.embidextrous.data.ApiException
import com.feerlessleadr.embidextrous.data.HpAlbum
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

/** One album the person has opened from the Albums list: the review list then shows only its tracks. */
data class HpScope(val artist: String, val album: String)

data class HallPassState(
    val gate: HpGate = HpGate.Checking,
    val status: HpStatus? = null,
    val filter: HpFilter = HpFilter.Review,
    val search: String = "",
    val albumsMode: Boolean = false,
    val scope: HpScope? = null,
    val rows: List<HpRow> = emptyList(),
    val albums: List<HpAlbum> = emptyList(),
    val total: Int = 0,
    val loading: Boolean = false,
    val preview: HpPreview? = null,
)

/**
 * The Hall Pass tab: the review of the music tracks the Hall Pass plugin could not decide, with the decisions that need a person (mark a track or a
 * whole album explicit or clean), a scan button and the apply step. Everything is asked of the plugin again after each action rather than trusted.
 */
class HallPassViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(HallPassState())
    val state: StateFlow<HallPassState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var pollJob: Job? = null
    private var listJob: Job? = null

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

    fun setFilter(filter: HpFilter) {
        _state.update { it.copy(filter = filter, scope = null) }
        reloadList()
    }

    fun setSearch(text: String) {
        _state.update { it.copy(search = text) }
        reloadList(debounce = true)
    }

    fun setAlbumsMode(on: Boolean) {
        _state.update { it.copy(albumsMode = on, scope = null) }
        reloadList()
    }

    fun openAlbum(album: HpAlbum) {
        _state.update { it.copy(scope = HpScope(album.artist, album.album), albumsMode = false) }
        reloadList()
    }

    fun closeScope() {
        _state.update { it.copy(scope = null) }
        reloadList()
    }

    private fun reloadList(debounce: Boolean = false) {
        listJob?.cancel()
        listJob = viewModelScope.launch {
            if (debounce) delay(350)
            val api = container.api() ?: return@launch
            _state.update { it.copy(loading = true) }
            try {
                val s = _state.value
                if (s.albumsMode && s.scope == null) {
                    val (total, albums) = api.hpAlbums(s.filter.decision, s.search.trim(), 0, PAGE)
                    _state.update { it.copy(albums = albums, rows = emptyList(), total = total, loading = false) }
                } else {
                    val (total, rows) = api.hpReview(s.filter.decision, s.search.trim(), s.scope?.artist, s.scope?.album, s.scope != null && s.scope.album.isEmpty(), 0, PAGE)
                    _state.update { it.copy(rows = rows, albums = emptyList(), total = total, loading = false) }
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
        if (s.loading) return
        val shown = if (s.albumsMode && s.scope == null) s.albums.size else s.rows.size
        if (shown >= s.total) return
        viewModelScope.launch {
            val api = container.api() ?: return@launch
            _state.update { it.copy(loading = true) }
            try {
                if (s.albumsMode && s.scope == null) {
                    val (total, more) = api.hpAlbums(s.filter.decision, s.search.trim(), shown, PAGE)
                    _state.update { it.copy(albums = it.albums + more, total = total, loading = false) }
                } else {
                    val (total, more) = api.hpReview(s.filter.decision, s.search.trim(), s.scope?.artist, s.scope?.album, s.scope != null && s.scope.album.isEmpty(), shown, PAGE)
                    _state.update { it.copy(rows = it.rows + more.filter { r -> it.rows.none { old -> old.id == r.id } }, total = total, loading = false) }
                }
            } catch (e: ApiException) {
                _state.update { it.copy(loading = false) }
                failed(e)
            }
        }
    }

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

    fun decideAlbum(album: HpAlbum, decision: String) {
        viewModelScope.launch {
            val api = container.api() ?: return@launch
            val s = _state.value
            try {
                val r = api.hpDecideAlbum(decision, s.filter.decision, s.search.trim(), album.artist, album.album)
                _messages.tryEmit("${r.changed} track(s) marked ${if (decision == "explicit") "explicit" else "clean"}.")
                refreshStatus(api)
                reloadList()
            } catch (e: ApiException) {
                failed(e)
            }
        }
    }

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
