package com.feerlessleadr.embidextrous.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feerlessleadr.embidextrous.AppContainer
import com.feerlessleadr.embidextrous.data.ApiClient
import com.feerlessleadr.embidextrous.data.ApiException
import com.feerlessleadr.embidextrous.data.GhAvailable
import com.feerlessleadr.embidextrous.data.GhPlugin
import com.feerlessleadr.embidextrous.data.GhRegistry
import com.feerlessleadr.embidextrous.data.GhRun
import com.feerlessleadr.embidextrous.data.GhRunMode
import com.feerlessleadr.embidextrous.data.GhSettings
import com.feerlessleadr.embidextrous.data.GhStatusResponse
import com.feerlessleadr.embidextrous.data.GhrpiGate
import com.feerlessleadr.embidextrous.data.GhrpiParser
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

data class PluginManagerState(
    val gate: GhrpiGate = GhrpiGate.Checking,
    val status: GhStatusResponse? = null,
    val plugins: List<GhPlugin> = emptyList(),
    val pluginsLoaded: Boolean = false,
    val history: List<GhRun>? = null,
    val settings: GhSettings? = null,
    val registries: List<GhRegistry>? = null,
    val available: List<GhAvailable>? = null,
    val availableLoading: Boolean = false,
    val availableError: String? = null,
    /** The plugin being looked at in detail, as the single-plugin call returned it (it carries the backups). */
    val detail: GhPlugin? = null,
    /** True from the moment the person asks for a restart until the server answers again. */
    val restarting: Boolean = false,
)

/**
 * The plugin manager: asks the GitHub Repo Plugin Install plugin what it knows and tells it what to do. Long jobs (a check, an update) answer at once
 * and are followed by looking at /status every couple of seconds until the run is over; every action looks again afterwards rather than trusting
 * what it changed.
 */
class PluginManagerViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(PluginManagerState())
    val state: StateFlow<PluginManagerState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var followJob: Job? = null
    private var restartJob: Job? = null

    /** Looks at the plugin: is it there, is its API on, is it a version this app can talk to. Then loads the dashboard and the list. */
    fun load() {
        viewModelScope.launch {
            val api = container.api() ?: return@launch
            val gate = try {
                GhrpiGate.of(api.ghInfo())
            } catch (e: ApiException) {
                when {
                    e.isUnauthorized -> { container.signOut(); return@launch }
                    e.status == 404 -> GhrpiGate.NotInstalled
                    e.status == 403 -> GhrpiGate.Off(null)
                    else -> GhrpiGate.Unreachable(e.message.orEmpty())
                }
            }
            _state.update { it.copy(gate = gate) }
            if (gate is GhrpiGate.Ready) {
                refresh(api)
                if (_state.value.status?.run?.isRunning == true) follow()
            }
        }
    }

    private suspend fun refresh(api: ApiClient) {
        try {
            val status = api.ghStatus()
            val plugins = api.ghPlugins()
            _state.update { it.copy(status = status, plugins = plugins, pluginsLoaded = true) }
        } catch (e: ApiException) {
            failed(e)
        }
    }

    /** Looks again every couple of seconds while a run is going, then once more when it is over. */
    private fun follow() {
        if (followJob?.isActive == true) return
        followJob = viewModelScope.launch {
            val api = container.api() ?: return@launch
            var rounds = 0
            while (rounds < 300) {
                refresh(api)
                if (_state.value.status?.run?.isRunning != true) break
                delay(1_500)
                rounds++
            }
        }
    }

    // ----- runs ----------------------------------------------------------------------------------------------

    fun run(mode: GhRunMode, what: String) = act(what) {
        ghRun(mode)
        follow()
    }

    fun updateOne(plugin: GhPlugin) = act("Updating ${plugin.name}") {
        ghUpdate(plugin.id)
        follow()
    }

    fun checkOne(plugin: GhPlugin) = act("Checking ${plugin.name}") {
        ghCheck(plugin.id)
        follow()
    }

    // ----- one plugin ----------------------------------------------------------------------------------------

    fun openDetail(plugin: GhPlugin) {
        _state.update { it.copy(detail = plugin) }
        viewModelScope.launch {
            val api = container.api() ?: return@launch
            try {
                api.ghPlugin(plugin.id)?.let { full -> _state.update { s -> s.copy(detail = full) } }
            } catch (e: ApiException) {
                failed(e)
            }
        }
    }

    fun closeDetail() = _state.update { it.copy(detail = null) }

    private fun reloadDetail(id: String) {
        viewModelScope.launch {
            val api = container.api() ?: return@launch
            try {
                api.ghPlugin(id)?.let { full -> _state.update { s -> if (s.detail?.id == id) s.copy(detail = full) else s } }
            } catch (e: ApiException) {
                // The list was refreshed already; the detail simply keeps what it had.
            }
        }
    }

    fun setPluginSwitches(plugin: GhPlugin, autoUpdate: Boolean? = null, allowPreRelease: Boolean? = null, assetNameFilter: String? = null) =
        act("Saved ${plugin.name}'s settings") {
            ghPluginSettings(plugin.id, autoUpdate, allowPreRelease, assetNameFilter)
            reloadDetail(plugin.id)
        }

    fun restore(plugin: GhPlugin) = act("Putting back the previous version of ${plugin.name}") {
        val result = ghRestore(plugin.id)
        _messages.emit(result.message.ifEmpty { "Restored ${plugin.name}." })
        reloadDetail(plugin.id)
    }

    fun remove(plugin: GhPlugin) = act("Removed ${plugin.name}") {
        val result = ghRemove(plugin.id)
        if (result.message.isNotEmpty()) _messages.emit(result.message)
        _state.update { it.copy(detail = null) }
    }

    // ----- adding --------------------------------------------------------------------------------------------

    fun loadAvailable() {
        _state.update { it.copy(availableLoading = true, availableError = null) }
        viewModelScope.launch {
            val api = container.api() ?: return@launch
            try {
                val list = api.ghAvailable()
                _state.update { it.copy(available = list, availableLoading = false) }
            } catch (e: ApiException) {
                _state.update { it.copy(availableLoading = false, availableError = e.message) }
            }
        }
    }

    /** Adds a plugin by address. The plugin looks it up at once, which can take a few seconds, and reports the result as the plugin's own status. */
    fun add(address: String, onDone: (Boolean) -> Unit) {
        val problem = GhrpiParser.checkRepoAddress(address)
        if (problem != null) {
            _messages.tryEmit(problem)
            onDone(false)
            return
        }
        viewModelScope.launch {
            val api = container.api() ?: return@launch
            try {
                val result = api.ghAdd(address)
                _messages.emit(result.plugin?.let { "Added ${it.name}: ${it.statusText.ifEmpty { it.status.name }}" } ?: result.message.ifEmpty { "Added." })
                refresh(api)
                onDone(true)
            } catch (e: ApiException) {
                failed(e)
                onDone(false)
            }
        }
    }

    // ----- restarts ------------------------------------------------------------------------------------------

    fun dismissRestart() = act("Restart notice cleared") {
        ghDismissRestart()
    }

    /** Asks the plugin to restart Emby, then waits for the server to answer again (up to about two minutes). */
    fun restart() {
        _state.update { it.copy(restarting = true) }
        restartJob?.cancel()
        restartJob = viewModelScope.launch {
            val api = container.api() ?: return@launch
            try {
                api.ghRestart()
            } catch (e: ApiException) {
                _state.update { it.copy(restarting = false) }
                failed(e)
                return@launch
            }
            delay(8_000)
            var back = false
            for (i in 0 until 24) {
                back = try {
                    api.ghInfo()
                    true
                } catch (e: ApiException) {
                    false
                }
                if (back) break
                delay(5_000)
            }
            _state.update { it.copy(restarting = false) }
            _messages.emit(if (back) "Emby is back." else "Emby has not answered yet. Give it a little longer, then pull to refresh.")
            if (back) load()
        }
    }

    // ----- history, settings, registries ---------------------------------------------------------------------

    fun loadHistory() {
        viewModelScope.launch {
            val api = container.api() ?: return@launch
            try {
                _state.update { it.copy(history = api.ghHistory()) }
            } catch (e: ApiException) {
                failed(e)
            }
        }
    }

    fun loadSettings() {
        viewModelScope.launch {
            val api = container.api() ?: return@launch
            try {
                val settings = api.ghSettings()
                val registries = api.ghRegistries()
                _state.update { it.copy(settings = settings, registries = registries) }
            } catch (e: ApiException) {
                failed(e)
            }
        }
    }

    fun saveToken(token: String) = act("GitHub token saved") {
        val s = ghSaveToken(token.trim())
        _state.update { it.copy(settings = s) }
    }

    fun clearToken() = act("GitHub token removed") {
        val s = ghClearToken()
        _state.update { it.copy(settings = s) }
    }

    fun testToken() = act(null) {
        _messages.emit(ghTestToken().second.ifEmpty { "Done." })
    }

    fun setRestartAfterInstall(on: Boolean) = act(if (on) "Emby will restart after scheduled installs" else "Emby will not restart by itself") {
        val s = ghSetRestartAfterInstall(on)
        _state.update { it.copy(settings = s) }
    }

    fun setRegistryEnabled(registry: GhRegistry, enabled: Boolean) = act(if (enabled) "${registry.name} switched on" else "${registry.name} switched off") {
        ghSetRegistryEnabled(registry.id, enabled)
        _state.update { s -> s.copy(registries = api2Registries(s, registry.id, enabled)) }
    }

    private fun api2Registries(s: PluginManagerState, id: String, enabled: Boolean) = s.registries?.map { if (it.id == id) it.copy(enabled = enabled) else it }

    // ----- plumbing ------------------------------------------------------------------------------------------

    /** Runs one call, says [success] when it worked (when not null), looks at the dashboard again and reports a failure in words. */
    private fun act(success: String?, call: suspend ApiClient.() -> Unit) {
        viewModelScope.launch {
            val api = container.api() ?: return@launch
            try {
                api.call()
                if (success != null) _messages.emit(success)
                refresh(api)
            } catch (e: ApiException) {
                failed(e)
                // A switch that was turned off on the server since the last look: look at the gate again.
                if (e.code == "ApiDisabled" || e.code == "ManageDisabled") load()
            }
        }
    }

    private fun failed(e: ApiException) {
        if (e.isUnauthorized) container.signOut() else _messages.tryEmit(e.message ?: "Something went wrong.")
    }
}
