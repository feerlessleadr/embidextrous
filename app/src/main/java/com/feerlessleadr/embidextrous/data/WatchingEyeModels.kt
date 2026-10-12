package com.feerlessleadr.embidextrous.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/** What the Hall Monitor plugin (1.9+) says a user is playing right now. */
@Serializable
data class NowPlaying(
    @SerialName("SessionId") val sessionId: String = "",
    @SerialName("ItemId") val itemId: String = "",
    @SerialName("ItemName") val itemName: String = "",
    @SerialName("ItemType") val itemType: String = "",
    @SerialName("RunTimeTicks") val runTimeTicks: Long = 0,
    @SerialName("PositionTicks") val positionTicks: Long = 0,
    @SerialName("IsPaused") val isPaused: Boolean = false,
    @SerialName("Client") val client: String = "",
    @SerialName("DeviceName") val deviceName: String = "",
)

/** One limited user as the plugin reports it (a row of `Public/Status`, or of the live push). Newer fields are optional. */
@Serializable
data class UserStatus(
    @SerialName("UserId") val userId: String = "",
    @SerialName("Username") val username: String = "",
    @SerialName("DailyLimitMinutes") val dailyLimitMinutes: Int = 0,
    @SerialName("SecondsWatchedDaily") val secondsWatchedDaily: Double = 0.0,
    @SerialName("SecondsWatchedWeekly") val secondsWatchedWeekly: Double = 0.0,
    @SerialName("SecondsWatchedMonthly") val secondsWatchedMonthly: Double = 0.0,
    @SerialName("CreditSecondsDaily") val creditSecondsDaily: Double = 0.0,
    @SerialName("TimeOutUntil") val timeOutUntil: String? = null,
    @SerialName("EffectiveDailyLimitMinutes") val effectiveDailyLimitMinutes: Int? = null,
    @SerialName("IsEnabled") val isEnabled: Boolean? = null,
    @SerialName("BlockReason") val blockReason: String? = null,
    @SerialName("SecondsRemaining") val secondsRemaining: Double? = null,
    @SerialName("LimitingPeriod") val limitingPeriod: String? = null,
    @SerialName("LimitingAllowedSeconds") val limitingAllowedSeconds: Double? = null,
    @SerialName("NowPlaying") val nowPlaying: NowPlaying? = null,
    @SerialName("ApiLevel") val apiLevel: Int? = null,
) {
    /** True unless the plugin says this user's limit is switched off. */
    val enabled: Boolean get() = isEnabled != false

    /** 2 or higher: the plugin itself says what is playing, so a missing [nowPlaying] really means "not playing". */
    val reportsNowPlaying: Boolean get() = (apiLevel ?: 0) >= 2

    /** The end of a running time-out, or null when there is none, it has ended, or the text is unusable. */
    fun activeTimeOutEnd(now: Instant): Instant? {
        val text = timeOutUntil?.trim().orEmpty()
        if (text.isEmpty() || text.startsWith("0001-01-01")) return null
        return try {
            Instant.parse(text).takeIf { it.isAfter(now) }
        } catch (e: Exception) {
            null
        }
    }

    /** Why playback is blocked right now ("Allowed" when it is not). An older plugin does not say, so a time-out is detected from its end. */
    fun block(now: Instant): String = blockReason?.takeIf { it.isNotEmpty() } ?: if (activeTimeOutEnd(now) != null) "TimedOut" else "Allowed"

    /** Seconds left before the closest limit, or -1 when the user has no limit. Falls back to the daily limit for an older plugin. */
    val remainingSeconds: Double
        get() {
            secondsRemaining?.let { return if (it < 0) -1.0 else it }
            val allowed = dailyAllowedSeconds
            return if (allowed <= 0) -1.0 else maxOf(0.0, allowed - secondsWatchedDaily)
        }

    private val dailyAllowedSeconds: Long
        get() {
            val minutes = effectiveDailyLimitMinutes ?: dailyLimitMinutes
            return if (minutes <= 0) 0L else minutes * 60L + creditSecondsDaily.toLong()
        }

    /** Total seconds allowed in the closest limit's period (0 when there is no limit). */
    val allowedSeconds: Double get() = limitingAllowedSeconds ?: dailyAllowedSeconds.toDouble()

    /** "daily", "weekly", ... */
    val periodLabel: String get() = limitingPeriod?.takeIf { it.isNotEmpty() }?.lowercase() ?: "daily"
}

/** A [UserStatus] with the moment it was received, so the countdown and the playback position can move between updates. */
data class UserCard(
    val status: UserStatus,
    val receivedAtMs: Long,
) {
    val playing: Boolean get() = status.nowPlaying != null
    val paused: Boolean get() = status.nowPlaying?.isPaused == true

    /** True while the plugin is counting this user's time: limited, not blocked, and actually playing. */
    fun counting(now: Instant): Boolean = playing && !paused && status.enabled && status.block(now) == "Allowed"

    /** Seconds left right now (the last report minus the time since, while counting); -1 when unlimited. */
    fun remainingNow(nowMs: Long): Double {
        val base = status.remainingSeconds
        if (base < 0) return -1.0
        val elapsed = maxOf(0L, nowMs - receivedAtMs) / 1000.0
        return maxOf(0.0, if (counting(Instant.ofEpochMilli(nowMs))) base - elapsed else base)
    }

    /** Playback position in ticks right now. */
    fun positionTicksNow(nowMs: Long): Long {
        val np = status.nowPlaying ?: return 0L
        val base = np.positionTicks
        return if (np.isPaused) base else base + maxOf(0L, nowMs - receivedAtMs) * 10_000L
    }
}

/** Plain-language texts for the Hall Monitor cards. */
object WatchText {
    /** "1 h 05 min", "42 min", "9 min 30 s", "45 s". Negative counts as zero. */
    fun duration(seconds: Double): String {
        val total = maxOf(0.0, Math.floor(seconds)).toLong()
        val hours = total / 3600
        val minutes = (total % 3600) / 60
        val secs = total % 60
        return when {
            hours > 0 -> "%d h %02d min".format(java.util.Locale.ROOT, hours, minutes)
            minutes >= 10 -> "$minutes min"
            minutes >= 1 -> "%d min %02d s".format(java.util.Locale.ROOT, minutes, secs)
            else -> "$secs s"
        }
    }

    /**
     * Time LEFT. Below 10 minutes it counts down in seconds like [duration]; from 10 minutes up the minutes round UP, so a limit that
     * was just extended by 15 minutes reads "15 min" and not "14 min" (playback can run a moment past the limit, which leaves
     * 14 min 59 s). Time used and totals keep rounding down ([duration]), because that is the honest direction for them.
     */
    fun durationLeft(seconds: Double): String {
        val total = maxOf(0.0, seconds)
        if (total < 600.0) return duration(total)
        val minutes = Math.ceil(total / 60.0).toLong()
        val hours = minutes / 60
        val rest = minutes % 60
        return if (hours > 0) "%d h %02d min".format(java.util.Locale.ROOT, hours, rest) else "$rest min"
    }

    /** Playback position like Emby shows it: "7:05" or "1:02:03". */
    fun clock(ticks: Long): String {
        val total = maxOf(0L, ticks) / 10_000_000L
        val hours = total / 3600
        val minutes = (total % 3600) / 60
        val secs = total % 60
        return if (hours > 0) "%d:%02d:%02d".format(java.util.Locale.ROOT, hours, minutes, secs)
        else "%d:%02d".format(java.util.Locale.ROOT, minutes, secs)
    }

    /** "today", "this week", ... for the limiting period name. */
    fun periodPhrase(period: String?): String = when (period?.lowercase()) {
        "weekly" -> "this week"
        "monthly" -> "this month"
        "yearly" -> "this year"
        else -> "today"
    }

    /** Plain-language explanation for an HTTP error from the plugin (its error responses carry no text). */
    fun describeFailure(what: String, code: Int): String = when (code) {
        400 -> "$what: the plugin rejected the request. Minutes must be 1-43200, and the user must be an enabled limited user (time-outs also need the limiter turned on)."
        401 -> "$what: Emby did not accept your sign-in. Sign in again."
        403 -> "$what: refused. Check that \"Enable Public API\" is ticked on the plugin page and that the Hall Monitor key matches. Repeated wrong keys lock the plugin API for 5 minutes."
        404 -> "$what: not found. Is the Hall Monitor plugin installed (version 1.9 or newer for stop-stream and undo), and is the server address right?"
        else -> "$what failed: HTTP $code"
    }
}

/** The most minutes the plugin accepts in one request (30 days). */
const val MAX_MINUTES = 43200
