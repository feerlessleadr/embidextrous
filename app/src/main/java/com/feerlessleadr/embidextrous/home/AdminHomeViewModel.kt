package com.feerlessleadr.embidextrous.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feerlessleadr.embidextrous.AppContainer
import com.feerlessleadr.embidextrous.data.ApiException
import com.feerlessleadr.embidextrous.data.ServerSnapshot
import com.feerlessleadr.embidextrous.data.GhrpiGate
import com.feerlessleadr.embidextrous.data.GithubSummary
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.feerlessleadr.embidextrous.data.ServerStatusParser
import com.feerlessleadr.embidextrous.push.EmbyTopic
import com.feerlessleadr.embidextrous.push.keepFresh
import kotlinx.coroutines.launch

data class AdminHomeState(val snapshot: ServerSnapshot? = null, val loading: Boolean = true)

/** The server cards of the Home screen (administrators): Emby's version and activity, failed tasks, plugin versions. Read-only. */
class AdminHomeViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(AdminHomeState())
    val state: StateFlow<AdminHomeState> = _state.asStateFlow()

    private var loadedAtMs = 0L

    /** Looks at the server again. [force] (a pull to refresh) ignores the pause between automatic looks. */
    fun load(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - loadedAtMs < MIN_GAP_MS) return
        loadedAtMs = now
        viewModelScope.launch { loadNow() }
    }

    private suspend fun loadNow() {
        val api = container.api() ?: return
        // Each call on its own: one that fails (an older server, a missing endpoint) only blanks its own line.
        val snapshot = coroutineScope {
            val info = async { attempt { api.systemInfo() } }
            val plugins = async { attempt { api.installedPlugins() } }
            val updates = async { attempt { api.pluginUpdates() } }
            val tasks = async { attempt { api.scheduledTasks() } }
            val sessions = async { attempt { api.sessionCounts() } }
            val github = async { githubSummary(api) }
            ServerSnapshot(info.await(), plugins.await(), updates.await(), tasks.await(), sessions.await(), github.await())
        }
        _state.update { it.copy(snapshot = snapshot, loading = false) }
    }

    /**
     * Keeps the cards fresh while Home is on screen: the connected, playing and transcoding numbers and the task results follow Emby's pushes at once, and
     * the rest (version, plugins, updates) is looked at again when the server says something changed (an install, a restart). Only while the push is down
     * is the whole picture asked for again, every 30 seconds.
     */
    suspend fun keepFresh() = container.push.keepFresh(
        arrayOf(EmbyTopic.Sessions, EmbyTopic.ScheduledTasks, EmbyTopic.Events),
        fallbackEveryMs = 30_000,
        accept = { it.type in PUSHED },
        onPush = { e ->
            val text = e.data?.toString().orEmpty()
            when (e.type) {
                "Sessions" -> _state.update { s -> s.copy(snapshot = s.snapshot?.copy(sessions = ServerStatusParser.sessions(text, System.currentTimeMillis()))) }
                "ScheduledTasksInfo" -> _state.update { s -> s.copy(snapshot = s.snapshot?.copy(tasks = ServerStatusParser.tasks(text))) }
                else -> loadNow() // the server changed what it runs: look at everything again
            }
        },
        refresh = { loadNow() },
    )

    /** Asks the GitHub plugin manager how it is doing; the gate says why when it cannot be used. */
    private suspend fun githubSummary(api: com.feerlessleadr.embidextrous.data.ApiClient): GithubSummary {
        val gate = try {
            GhrpiGate.of(api.ghInfo())
        } catch (e: ApiException) {
            when (e.status) {
                404 -> GhrpiGate.NotInstalled
                403 -> GhrpiGate.Off(null)
                else -> GhrpiGate.Unreachable(e.message.orEmpty())
            }
        } catch (e: Exception) {
            GhrpiGate.Unreachable(e.message.orEmpty())
        }
        val status = if (gate is GhrpiGate.Ready) attempt { api.ghStatus() } else null
        return GithubSummary(gate, status)
    }

    private suspend fun <T> attempt(call: suspend () -> T): T? = try {
        call()
    } catch (e: ApiException) {
        if (e.isUnauthorized) container.signOut()
        null
    } catch (e: Exception) {
        null
    }

    private companion object {
        /** Automatic looks happen when the Home screen comes to the front; not more often than this. */
        const val MIN_GAP_MS = 30_000L

        /** What Emby pushes that matters here: the live numbers, and the messages that say plugins or the server itself changed. */
        val PUSHED = setOf("Sessions", "ScheduledTasksInfo", "PackageInstalling", "PackageInstallationCompleted", "PackageInstallationFailed", "PackageUninstalled", "RestartRequired", "ServerRestarting", "ServerShuttingDown")
    }
}
