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
    val sessions = SessionRepository(KeystoreSecureStore(context))
    val settings = AppSettings(context.getSharedPreferences("settings", Context.MODE_PRIVATE))
    val covers = CoverCache()
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
        if (!signedIn.isAdministrator) {
            throw ApiException(403, "${signedIn.userName} is not an Emby administrator. The SmartLists API is for administrators only.")
        }

        val authed = apiFor(server, signedIn.token)
        val info = try {
            authed.info()
        } catch (e: ApiException) {
            if (e.status == 404) {
                throw ApiException(404, "The SmartLists plugin was not found on this server. Install SmartLists 0.1.9 or newer.")
            }
            throw e
        }
        if (info.apiVersion != SUPPORTED_API_VERSION) {
            val advice = if (info.apiVersion > SUPPORTED_API_VERSION) "Update the app." else "Update the SmartLists plugin."
            throw ApiException(0, "This app speaks API version $SUPPORTED_API_VERSION but the plugin speaks ${info.apiVersion}. $advice")
        }

        val session = Session(server, signedIn.token, signedIn.userId, signedIn.userName)
        sessions.save(session)
        _session.value = session
    }

    // Signs out here and, best effort, on the server too, so a copy of the token stops working.
    fun signOut() {
        val api = api()
        covers.clear()
        sessions.signOut()
        _session.value = null
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
