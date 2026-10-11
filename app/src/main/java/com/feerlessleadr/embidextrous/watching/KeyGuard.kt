package com.feerlessleadr.embidextrous.watching

/**
 * Protects the plugin from being locked by this app. The plugin locks its public API for 5 minutes after 5 wrong keys within
 * 5 minutes, so after the plugin refuses the key the app makes no key-protected request for [BLOCK_MS]: a wrong or changed key
 * costs at most a couple of attempts every 5 minutes, never enough to trip the lockout. Saving a new key starts afresh
 * ([reset]). No Android classes: unit tested.
 */
class KeyGuard {
    @Volatile
    private var blockedUntilMs = 0L

    /** The plugin refused the key (HTTP 403 or a refused live connection) at [nowMs]. */
    fun refused(nowMs: Long) {
        blockedUntilMs = nowMs + BLOCK_MS
    }

    fun isBlocked(nowMs: Long): Boolean = nowMs < blockedUntilMs

    /** Whole minutes (at least 1) until the app tries again, for the message. */
    fun minutesLeft(nowMs: Long): Long = maxOf(1L, (blockedUntilMs - nowMs + 59_999L) / 60_000L)

    fun reset() {
        blockedUntilMs = 0L
    }

    companion object {
        const val BLOCK_MS = 5 * 60_000L
        const val MESSAGE = "The plugin refused the Watching Eye key. Check it under Settings > Watching Eye. " +
            "To keep the plugin from locking itself, the app tries again only every 5 minutes (or right after you save a new key)."
    }
}
