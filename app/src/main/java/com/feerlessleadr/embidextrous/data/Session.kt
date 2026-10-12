package com.feerlessleadr.embidextrous.data

import java.util.UUID

/** Who the app is signed in as, and where. The token is the only secret. */
data class Session(
    val serverUrl: String,
    val token: String,
    val userId: String,
    val userName: String,
    /** True for an Emby administrator: they get every tool; a regular account gets only Hall Monitor. */
    val isAdministrator: Boolean = false,
)

/** Persists the [Session] (and a stable device id Emby shows in its device list). */
class SessionRepository(private val store: SecureStore) {

    fun load(): Session? {
        val token = store.getSecret(KEY_TOKEN) ?: return null
        val server = store.get(KEY_SERVER) ?: return null
        return Session(
            serverUrl = server,
            token = token,
            userId = store.get(KEY_USER_ID).orEmpty(),
            userName = store.get(KEY_USER_NAME).orEmpty(),
            isAdministrator = store.get(KEY_IS_ADMIN) == "1",
        )
    }

    /** The Hall Monitor plugin's own key (Public API tab on the plugin page), kept encrypted like the token. */
    fun loadPluginKey(): String? = store.getSecret(KEY_PLUGIN_KEY)?.takeIf { it.isNotBlank() }

    fun savePluginKey(key: String) {
        if (key.isBlank()) store.putSecret(KEY_PLUGIN_KEY, "") else store.putSecret(KEY_PLUGIN_KEY, key.trim())
    }

    fun save(session: Session) {
        store.put(KEY_SERVER, session.serverUrl)
        store.put(KEY_USER_ID, session.userId)
        store.put(KEY_USER_NAME, session.userName)
        store.put(KEY_IS_ADMIN, if (session.isAdministrator) "1" else "0")
        store.putSecret(KEY_TOKEN, session.token)
    }

    /** Signs out but keeps the server address (and device id) so the sign-in form is pre-filled. */
    fun signOut() {
        val server = store.get(KEY_SERVER)
        val device = store.get(KEY_DEVICE_ID)
        store.clear()
        if (server != null) store.put(KEY_SERVER, server)
        if (device != null) store.put(KEY_DEVICE_ID, device)
    }

    fun lastServerUrl(): String = store.get(KEY_SERVER).orEmpty()

    fun deviceId(): String {
        store.get(KEY_DEVICE_ID)?.let { return it }
        val id = UUID.randomUUID().toString()
        store.put(KEY_DEVICE_ID, id)
        return id
    }

    private companion object {
        const val KEY_SERVER = "server"
        const val KEY_USER_ID = "userId"
        const val KEY_USER_NAME = "userName"
        const val KEY_IS_ADMIN = "isAdmin"
        const val KEY_PLUGIN_KEY = "watchingEyeKey"
        const val KEY_TOKEN = "token"
        const val KEY_DEVICE_ID = "deviceId"
    }
}
