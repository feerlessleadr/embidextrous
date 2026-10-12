package com.feerlessleadr.embidextrous.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feerlessleadr.embidextrous.AppContainer
import com.feerlessleadr.embidextrous.data.ApiException
import com.feerlessleadr.embidextrous.data.ServerSnapshot
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
        viewModelScope.launch {
            val api = container.api() ?: return@launch
            // Each call on its own: one that fails (an older server, a missing endpoint) only blanks its own line.
            val snapshot = coroutineScope {
                val info = async { attempt { api.systemInfo() } }
                val plugins = async { attempt { api.installedPlugins() } }
                val updates = async { attempt { api.pluginUpdates() } }
                val tasks = async { attempt { api.failedTasks() } }
                val sessions = async { attempt { api.sessionCounts() } }
                ServerSnapshot(info.await(), plugins.await(), updates.await(), tasks.await(), sessions.await())
            }
            _state.update { it.copy(snapshot = snapshot, loading = false) }
        }
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
    }
}
