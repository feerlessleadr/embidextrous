package com.feerlessleadr.embidextrous.watching

import com.feerlessleadr.embidextrous.AppContainer
import com.feerlessleadr.embidextrous.data.ApiException
import com.feerlessleadr.embidextrous.data.Session
import com.feerlessleadr.embidextrous.data.UserCard
import com.feerlessleadr.embidextrous.data.UserStatus
import com.feerlessleadr.embidextrous.data.WatchingEyeClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/** What the connection looks like to the person using the screen. */
enum class Link { CONNECTING, LIVE, POLLING, OFFLINE, NEEDS_KEY }

data class WatchState(
    val cards: List<UserCard> = emptyList(),
    val link: Link = Link.CONNECTING,
    val detail: String = "",
    val lastDataMs: Long = 0L,
    /** True once a first answer has arrived (so an empty list can be told from "not loaded yet"). */
    val loaded: Boolean = false,
)

/**
 * The one source of truth for every limited user. The plugin pushes changes over the live connection; asking the server is only
 * the safety net while that connection is down. Screens call [acquire] while they are visible and [release] when they are not,
 * and the connection exists only while somebody holds it.
 */
class WatchingEyeRepository(
    private val container: AppContainer,
    private val scope: CoroutineScope,
    private val http: OkHttpClient,
) : LiveListener {

    private val _state = MutableStateFlow(WatchState())
    val state: StateFlow<WatchState> = _state.asStateFlow()

    private val lock = Any()
    private var clients = 0
    private var job: Job? = null

    private var liveState = LiveState.CONNECTING
    private var liveDetail = ""
    private var lastRestOkMs = 0L
    private var restProblem = ""
    private var credentials: Pair<Session, String>? = null

    fun acquire() {
        synchronized(lock) {
            clients++
            if (job == null) job = scope.launch { supervise() }
        }
    }

    fun release() {
        synchronized(lock) {
            if (clients > 0) clients--
            if (clients == 0) {
                job?.cancel()
                job = null
            }
        }
    }

    /** Pull-to-refresh: ask the server now (the live connection keeps running). */
    fun refreshNow() {
        scope.launch { fetchRest() }
    }

    private suspend fun supervise() {
        combine(container.session, container.pluginKey) { session, key -> session to key }
            .distinctUntilChanged()
            .collectLatest { (session, key) ->
                credentials = null
                when {
                    session == null -> _state.value = WatchState(link = Link.CONNECTING)
                    key == null -> _state.value = WatchState(link = Link.NEEDS_KEY)
                    else -> {
                        credentials = session to key
                        synchronized(lock) {
                            liveState = LiveState.CONNECTING
                            liveDetail = ""
                            lastRestOkMs = 0L
                            restProblem = ""
                        }
                        _state.value = WatchState(link = Link.CONNECTING)
                        runSession(session, key)
                    }
                }
            }
    }

    private suspend fun runSession(session: Session, key: String) = coroutineScope {
        val live = LiveConnection(session.serverUrl, session.token, key, container.deviceId(), http)
        launch { live.run(this@WatchingEyeRepository) }
        launch {
            fetchRest()
            while (isActive) {
                delay(FALLBACK_POLL_MS)
                val isLive = synchronized(lock) { liveState == LiveState.LIVE }
                if (!isLive) fetchRest()
            }
        }
    }

    // ---- live connection callbacks --------------------------------------------------------------------------

    override fun onStatus(users: List<UserStatus>, atMs: Long) {
        _state.value = compute(cards = users.map { UserCard(it, atMs) }, lastData = atMs)
    }

    override fun onState(state: LiveState, detail: String) {
        synchronized(lock) {
            liveState = state
            liveDetail = detail
        }
        _state.value = compute()
    }

    // ---- asking the server (first load, pull-to-refresh, and fallback while the live connection is down) -------

    private suspend fun fetchRest() {
        val (session, key) = credentials ?: return
        try {
            val users = WatchingEyeClient(session.serverUrl, session.token, key, http).status()
            val now = System.currentTimeMillis()
            val isLive = synchronized(lock) {
                lastRestOkMs = now
                restProblem = ""
                liveState == LiveState.LIVE
            }
            // Do not overwrite fresher live data with this answer.
            _state.value = if (isLive && _state.value.loaded) compute(lastData = now) else compute(users.map { UserCard(it, now) }, now)
        } catch (e: ApiException) {
            synchronized(lock) { restProblem = e.message.orEmpty() }
            _state.value = compute()
        } catch (e: Exception) {
            synchronized(lock) { restProblem = e.message ?: "Could not load the status." }
            _state.value = compute()
        }
    }

    private fun compute(cards: List<UserCard>? = null, lastData: Long? = null): WatchState {
        val previous = _state.value
        val nextCards = cards ?: previous.cards
        val nextLast = lastData ?: previous.lastDataMs
        val loaded = previous.loaded || cards != null
        val now = System.currentTimeMillis()
        val (link, detail) = synchronized(lock) {
            val restFresh = lastRestOkMs > 0 && now - lastRestOkMs < 2 * FALLBACK_POLL_MS
            val why = liveDetail.ifEmpty { "Live updates are not connected yet." }
            when {
                liveState == LiveState.LIVE -> Link.LIVE to ""
                restFresh -> Link.POLLING to why
                !loaded && restProblem.isEmpty() && liveState == LiveState.CONNECTING -> Link.CONNECTING to ""
                else -> Link.OFFLINE to (restProblem.ifEmpty { why })
            }
        }
        return WatchState(nextCards, link, detail, nextLast, loaded)
    }

    // ---- actions ---------------------------------------------------------------------------------------------

    /** Runs one plugin call and, when the live connection is not up to bring the change, asks the server so the screen is not stale. */
    suspend fun act(call: suspend WatchingEyeClient.() -> Unit) {
        val (session, key) = credentials ?: throw ApiException(0, "Enter the Watching Eye key in Settings first.")
        WatchingEyeClient(session.serverUrl, session.token, key, http).call()
        val isLive = synchronized(lock) { liveState == LiveState.LIVE }
        if (!isLive) fetchRest()
    }

    private companion object {
        /** While the live connection is down the app asks the server this often. */
        const val FALLBACK_POLL_MS = 20_000L
    }
}
