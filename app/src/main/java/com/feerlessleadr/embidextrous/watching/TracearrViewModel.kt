package com.feerlessleadr.embidextrous.watching

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feerlessleadr.embidextrous.AppContainer
import com.feerlessleadr.embidextrous.data.ApiException
import com.feerlessleadr.embidextrous.data.Safety
import com.feerlessleadr.embidextrous.data.TrGate
import com.feerlessleadr.embidextrous.data.TrPlay
import com.feerlessleadr.embidextrous.data.TrStreams
import com.feerlessleadr.embidextrous.data.TrUser
import com.feerlessleadr.embidextrous.data.TrUserStats
import com.feerlessleadr.embidextrous.data.TrViolation
import com.feerlessleadr.embidextrous.data.TracearrClient
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** One list loaded a page at a time, and how loading it went. */
data class Pages<T>(val items: List<T> = emptyList(), val nextCursor: String? = null, val loaded: Boolean = false, val error: String? = null, val loadingMore: Boolean = false)

data class TracearrState(
    val gate: TrGate = TrGate.Checking,
    val streams: TrStreams? = null,
    val streamsError: String? = null,
    val history: Pages<TrPlay> = Pages(),
    val violations: Pages<TrViolation> = Pages(),
    val users: List<TrUser> = emptyList(),
    val usersLoaded: Boolean = false,
    val stats: Map<String, TrUserStats> = emptyMap(),
    /** The person the history is filtered to (a Tracearr identity id), or null for everyone. */
    val person: String? = null,
)

/**
 * The Activity area of the Watching group: what Tracearr has seen on the server. Read only. Everything loads when the area opens and again on pull to
 * refresh (Tracearr limits each key to a shared 240 requests a minute, so nothing here polls quickly).
 */
class TracearrViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(TracearrState())
    val state: StateFlow<TracearrState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val posters = ConcurrentHashMap<String, Bitmap>()

    private fun client(): TracearrClient? = container.tracearrClient()

    /** Looks at the connection (and so the server list) and then loads what the Activity tab shows. */
    fun load() {
        viewModelScope.launch {
            val c = client()
            if (c == null) {
                _state.update { TracearrState(gate = TrGate.NotConfigured) }
                return@launch
            }
            _state.update { it.copy(gate = if (it.gate is TrGate.Ready) it.gate else TrGate.Checking) }
            try {
                val servers = c.servers()
                _state.update { it.copy(gate = TrGate.Ready(servers.tracearrVersion, servers.servers)) }
            } catch (e: ApiException) {
                _state.update { it.copy(gate = gateFor(e)) }
                return@launch
            }
            coroutineScope {
                launch { loadStreams(c) }
                launch { loadHistory(c, more = false) }
                launch { loadViolations(c, more = false) }
                launch { loadUsers(c) }
            }
        }
    }

    private fun gateFor(e: ApiException): TrGate = when {
        e.status == 401 || e.status == 403 -> TrGate.KeyRejected
        e.code == "RateLimited" -> TrGate.RateLimited(null)
        else -> TrGate.Unreachable(e.message.orEmpty())
    }

    private suspend fun loadStreams(c: TracearrClient) {
        try {
            _state.update { it.copy(streams = c.streams(), streamsError = null) }
        } catch (e: ApiException) {
            _state.update { it.copy(streamsError = e.message) }
        }
    }

    private suspend fun loadHistory(c: TracearrClient, more: Boolean) {
        val current = _state.value
        val cursor = if (more) current.history.nextCursor else null
        if (more && cursor == null) return
        _state.update { it.copy(history = it.history.copy(loadingMore = more, error = null)) }
        try {
            val page = c.history(cursor = cursor, pageSize = 25, userId = current.person)
            _state.update { s ->
                val items = if (more) (s.history.items + page.items).distinctBy { it.id } else page.items
                s.copy(history = Pages(items, page.nextCursor, loaded = true))
            }
        } catch (e: ApiException) {
            _state.update { it.copy(history = it.history.copy(loaded = true, loadingMore = false, error = e.message)) }
        }
    }

    private suspend fun loadViolations(c: TracearrClient, more: Boolean) {
        val cursor = if (more) _state.value.violations.nextCursor else null
        if (more && cursor == null) return
        _state.update { it.copy(violations = it.violations.copy(loadingMore = more, error = null)) }
        try {
            val page = c.violations(cursor = cursor, pageSize = 25)
            _state.update { s ->
                val items = if (more) (s.violations.items + page.items).distinctBy { it.id } else page.items
                s.copy(violations = Pages(items, page.nextCursor, loaded = true))
            }
        } catch (e: ApiException) {
            _state.update { it.copy(violations = it.violations.copy(loaded = true, loadingMore = false, error = e.message)) }
        }
    }

    private suspend fun loadUsers(c: TracearrClient) {
        try {
            val users = c.users().items.filter { u -> u.accounts.any { !it.removed } }
            _state.update { it.copy(users = users, usersLoaded = true) }
            // A person's numbers come one call each; a handful of people is a handful of calls.
            coroutineScope {
                users.take(12).map { u -> async { u.id to runCatching { c.userStats(u.id) }.getOrNull() } }.forEach { job ->
                    val (id, stats) = job.await()
                    if (stats != null) _state.update { it.copy(stats = it.stats + (id to stats)) }
                }
            }
        } catch (e: ApiException) {
            _state.update { it.copy(usersLoaded = true) }
        }
    }

    fun loadMoreHistory() {
        viewModelScope.launch { client()?.let { loadHistory(it, more = true) } }
    }

    fun loadMoreViolations() {
        viewModelScope.launch { client()?.let { loadViolations(it, more = true) } }
    }

    fun refreshStreams() {
        viewModelScope.launch { client()?.let { loadStreams(it) } }
    }

    fun choosePerson(id: String?) {
        _state.update { it.copy(person = id, history = Pages()) }
        viewModelScope.launch { client()?.let { loadHistory(it, more = false) } }
    }

    /** A poster picture, cached for the life of the screen; null when it has none or cannot be loaded. */
    suspend fun poster(path: String): Bitmap? {
        posters[path]?.let { return it }
        val c = client() ?: return null
        val bytes = try {
            c.image(path)
        } catch (e: Exception) {
            null
        } ?: return null
        val bitmap = withContext(Dispatchers.Default) { Safety.decodeBounded(bytes, 400) } ?: return null
        posters[path] = bitmap
        return bitmap
    }
}
