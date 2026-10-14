package com.feerlessleadr.embidextrous.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.feerlessleadr.embidextrous.App
import com.feerlessleadr.embidextrous.MainActivity
import com.feerlessleadr.embidextrous.R
import com.feerlessleadr.embidextrous.watching.ActionReceiver
import java.time.LocalTime

/**
 * Shows and removes the notifications the engines produce: one Android channel per service (so sound and vibration are chosen in Android's own settings
 * for each), a generic version for the lock screen, quiet hours, and the add-time buttons of Hall Monitor's "used all" alert.
 */
object NotifPoster {
    const val CHANNEL_MONITOR = "monitor"
    const val EXTRA_OPEN = "open_tab"
    val QUICK_MINUTES = intArrayOf(15, 30, 60)

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** One alert per subject: the same id every time, so it can be replaced or cancelled. */
    fun notificationId(key: String): Int = key.hashCode() and 0x7fffffff

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_MONITOR, "Background connection", NotificationManager.IMPORTANCE_MIN)
                .apply { description = "Shown while the app listens for alerts in the background." },
        )
        for (service in NotifService.entries) {
            nm.createNotificationChannel(
                NotificationChannel(service.channelId, service.channelTitle, NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = "Alerts from ${service.title}. Choose the sound and vibration here." },
            )
        }
    }

    /** Opens Android's own screen for one service's channel (sound, vibration, importance). */
    fun channelSettings(context: Context, service: NotifService): Intent =
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, service.channelId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun openIntent(context: Context, opensTab: String?): PendingIntent {
        val open = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (opensTab != null) open.putExtra(EXTRA_OPEN, opensTab)
        // A request code per target, so two different targets never share one pending intent.
        return PendingIntent.getActivity(context, (opensTab ?: "").hashCode() and 0x7fffffff, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun clear(context: Context, key: String) {
        context.getSystemService(NotificationManager::class.java).cancel(notificationId(key))
    }

    /** Shows [d] unless notifications are paused or not allowed. During quiet hours it arrives without sound. */
    fun post(context: Context, d: NotifDelivery, force: Boolean = false) {
        if (!canNotify(context)) return
        val settings = (context.applicationContext as App).container.settings
        if (settings.notifPaused.value && !force) return
        val now = LocalTime.now()
        val quiet = settings.quietHours.value.isQuiet(now.hour * 60 + now.minute)
        val id = notificationId(d.key)
        val lockScreen: Notification = NotificationCompat.Builder(context, d.service.channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(d.service.title)
            .setContentText(d.lockScreenText)
            .build()
        val builder = NotificationCompat.Builder(context, d.service.channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(d.title)
            .setContentText(d.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(d.text))
            .setPriority(if (quiet) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH)
            .setSilent(quiet)
            .setAutoCancel(true)
            .setContentIntent(openIntent(context, d.opensTab))
            // On the lock screen only a generic notice is shown (no names, no buttons); the buttons also ask for the screen lock.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(lockScreen)
        d.addTimeFor?.let { (userId, name) ->
            for (minutes in QUICK_MINUTES) {
                builder.addAction(
                    NotificationCompat.Action.Builder(0, "+$minutes min", ActionReceiver.addTimeIntent(context, userId, name, minutes, id))
                        .setAuthenticationRequired(true)
                        .build(),
                )
            }
        }
        context.getSystemService(NotificationManager::class.java).notify(id, builder.build())
    }

    /** A notification to prove the setup works: the same channel, text and lock-screen version as a real alert of [service]. */
    fun test(context: Context, service: NotifService) {
        post(
            context,
            NotifDelivery(
                service, "test", "test|${service.name}", "Test from ${service.title}",
                "If you can read this, ${service.title} alerts reach this phone.", "${service.title} sent a test notification.",
            ),
            force = true,
        )
    }
}
