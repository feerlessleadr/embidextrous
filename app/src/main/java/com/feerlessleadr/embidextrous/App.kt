package com.feerlessleadr.embidextrous

import android.app.Application
import android.content.Context
import android.os.Build
import com.feerlessleadr.embidextrous.data.ApiClient
import com.feerlessleadr.embidextrous.data.ApiException
import com.feerlessleadr.embidextrous.data.AppSettings
import com.feerlessleadr.embidextrous.data.CoverCache
import com.feerlessleadr.embidextrous.data.KeystoreSecureStore
import com.feerlessleadr.embidextrous.data.SUPPORTED_API_VERSION
import com.feerlessleadr.embidextrous.data.Session
import com.feerlessleadr.embidextrous.data.SessionRepository
import com.feerlessleadr.embidextrous.data.UrlRules
import com.feerlessleadr.embidextrous.data.WatchingEyeClient
import com.feerlessleadr.embidextrous.watching.AlertService
import com.feerlessleadr.embidextrous.watching.UserAvatars
import com.feerlessleadr.embidextrous.watching.Link
import com.feerlessleadr.embidextrous.watching.WatchingEyeRepository
import com.feerlessleadr.embidextrous.watching.WidgetUpdater
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class App : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Hand-built dependencies: the session, and an API client for it. */
class AppContainer(context: Context) {
    private val appContext: Context = context.applicationContext
    val sessions = SessionRepository(KeystoreSecureStore(context))
    val settings = AppSettings(context.getSharedPreferences("settings", Context.MODE_PRIVATE))
    val covers = CoverCache()
    val avatars = UserAvatars()
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _listsChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits when a list was created or changed in the editor, so the overview reloads. */
    val listsChanged: SharedFlow<Unit> = _listsChanged

    fun notifyListsChanged() {
        _listsChanged.tryEmit(Unit)
    }
    private val http = ApiClient.defaultHttpClient()

    private val _session = MutableStateFlow(sessions.load())
    val session: StateFlow<Session?> = _session.asStateFlow()

    /** An API client for the current session, or null when signed out. */
    fun api(): ApiClient? = _session.value?.let { apiFor(it.serverUrl, it.token) }

    private fun apiFor(serverUrl: String, token: String?) = ApiClient(
        serverUrl = serverUrl,
        token = token,
        appVersion = BuildConfig.VERSION_NAME,
        deviceId = sessions.deviceId(),
        deviceName = Build.MODEL ?: "Android",
        http = http,
    )

    /**
     * Signs in and stores the session. Fails (with a message fit to show) when the address is not allowed, the
     * account is not an administrator, or the plugin is missing or speaks a different API version.
     */
    suspend fun signIn(serverInput: String, userName: String, password: String) {
        val server = when (val result = UrlRules.normalize(serverInput, allowCleartext = BuildConfig.DEBUG)) {
            is UrlRules.Result.Ok -> result.serverUrl
            is UrlRules.Result.Rejected -> throw ApiException(0, result.reason)
        }
        val anonymous = apiFor(server, null)
        val signedIn = anonymous.signIn(userName.trim(), password)
        // Any account may sign in: an administrator gets every tool, a regular account only Hall Monitor.
        val session = Session(server, signedIn.token, signedIn.userId, signedIn.userName, signedIn.isAdministrator)
        sessions.save(session)
        _session.value = session
        _smartListsProblem.value = null
        if (session.isAdministrator) {
            // A missing or mismatched SmartLists plugin does not stop the sign-in: that tab explains what is wrong.
            checkSmartLists()
        }
    }

    private val _smartListsProblem = MutableStateFlow<String?>(null)

    /** Why the Smart Lists tab cannot work (plugin missing or a different API version), or null when it can. */
    val smartListsProblem: StateFlow<String?> = _smartListsProblem.asStateFlow()

    /** Looks for the SmartLists plugin and checks its API version; the result is in [smartListsProblem]. */
    suspend fun checkSmartLists() {
        val authed = api() ?: return
        _smartListsProblem.value = try {
            val info = authed.info()
            if (info.apiVersion != SUPPORTED_API_VERSION) {
                val advice = if (info.apiVersion > SUPPORTED_API_VERSION) "Update the app." else "Update the SmartLists plugin."
                "This app speaks SmartLists API version $SUPPORTED_API_VERSION but the plugin speaks ${info.apiVersion}. $advice"
            } else {
                null
            }
        } catch (e: ApiException) {
            if (e.status == 404) "The SmartLists plugin was not found on this server. Install SmartLists 0.1.9 or newer." else e.message
        }
    }

    private val _pluginKey = MutableStateFlow(sessions.loadPluginKey())

    /** The Hall Monitor plugin's key, or null until it has been entered. */
    val pluginKey: StateFlow<String?> = _pluginKey.asStateFlow()

    fun setPluginKey(key: String) {
        sessions.savePluginKey(key)
        _pluginKey.value = sessions.loadPluginKey()
        AlertService.sync(appContext)
    }

    fun setAlertsEnabled(on: Boolean) {
        settings.setAlertsEnabled(on)
        AlertService.sync(appContext)
    }

    /** The Hall Monitor part of the app: live status, kept up to date only while a screen needs it. */
    val watchingEye: WatchingEyeRepository by lazy { WatchingEyeRepository(this, appScope, http) }

    /** A client for the Hall Monitor plugin with the held sign-in and key, or null when either is missing. */
    fun watchingEyeClient(): WatchingEyeClient? {
        val session = _session.value ?: return null
        val key = _pluginKey.value ?: return null
        return WatchingEyeClient(session.serverUrl, session.token, key, http)
    }

    init {
        // The home-screen widget follows the live data (whenever a screen or the alert service keeps it flowing) and the name setting.
        appScope.launch {
            combine(watchingEye.state, settings.widgetHideNames, settings.accent) { state, _, _ -> state }.collect { state ->
                if (state.loaded && _session.value != null) {
                    WidgetUpdater.render(appContext, state.cards.map { it.status }, live = state.link == Link.LIVE)
                }
            }
        }
    }

    /** The device id Emby shows in its device list for this app. */
    fun deviceId(): String = sessions.deviceId()

    // Signs out here and, best effort, on the server too, so a copy of the token stops working.
    fun signOut() {
        val api = api()
        covers.clear()
        avatars.clear()
        sessions.signOut()
        _pluginKey.value = null
        _smartListsProblem.value = null
        _session.value = null
        AlertService.sync(appContext)
        WidgetUpdater.showSignedOut(appContext)
        if (api != null) {
            appScope.launch {
                try {
                    api.logout()
                } catch (e: Exception) {
                    // Offline, or the token was already invalid: the local sign-out has happened either way.
                }
            }
        }
    }
}
