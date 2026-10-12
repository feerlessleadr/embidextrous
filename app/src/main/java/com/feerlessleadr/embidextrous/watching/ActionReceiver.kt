package com.feerlessleadr.embidextrous.watching

import android.app.KeyguardManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.feerlessleadr.embidextrous.App
import com.feerlessleadr.embidextrous.R
import com.feerlessleadr.embidextrous.data.ApiException
import com.feerlessleadr.embidextrous.data.MAX_MINUTES
import kotlinx.coroutines.runBlocking

/** Handles the "+15 min" style buttons on a limit-reached notification: adds the time without opening the app. */
class ActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_ADD) return
        val userId = intent.getStringExtra(EXTRA_USER_ID) ?: return
        val name = intent.getStringExtra(EXTRA_NAME) ?: "User"
        val minutes = intent.getIntExtra(EXTRA_MINUTES, 0)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        if (minutes !in 1..MAX_MINUTES) return
        // Defence in depth: never add time while the phone is locked, whatever the notification showed.
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (keyguard?.isDeviceLocked == true) return

        val app = context.applicationContext
        val pending = goAsync()
        Thread {
            try {
                val container = (app as App).container
                var ok = false
                var alreadyHandled = false
                val result = try {
                    val client = container.watchingEyeClient() ?: throw ApiException(0, "Sign in and enter the Hall Monitor key in the app first.")
                    // Time may already have been added from another phone: look first, so a stale button can never add it twice.
                    val current = runBlocking { client.status() }.firstOrNull { it.userId == userId }
                    if (current != null && current.enabled && current.block(java.time.Instant.now()) != "TimeLimitExceeded") {
                        alreadyHandled = true
                        "$name already has time, so nothing was added."
                    } else {
                        runBlocking { client.extend(userId, minutes) }
                        ok = true
                        "Added $minutes min for $name"
                    }
                } catch (e: ApiException) {
                    e.message.orEmpty()
                } catch (e: Exception) {
                    "Add time failed. ${e.message.orEmpty()}".trim()
                }
                val nm = app.getSystemService(NotificationManager::class.java)
                if (ok) {
                    nm.cancel(notificationId)
                } else if (alreadyHandled) {
                    // Someone else already gave time: the old alert goes, and a short note says why nothing was added.
                    nm.cancel(notificationId)
                    if (AlertService.canNotify(app)) {
                        nm.notify(
                            notificationId,
                            NotificationCompat.Builder(app, AlertService.CHANNEL_ALERTS)
                                .setSmallIcon(R.drawable.ic_notification)
                                .setContentTitle(result)
                                .setTimeoutAfter(10_000)
                                .setAutoCancel(true)
                                .build(),
                        )
                    }
                } else if (AlertService.canNotify(app)) {
                    nm.notify(
                        notificationId,
                        NotificationCompat.Builder(app, AlertService.CHANNEL_ALERTS)
                            .setSmallIcon(R.drawable.ic_notification)
                            .setContentTitle("$name: could not add time")
                            .setContentText(result)
                            .setStyle(NotificationCompat.BigTextStyle().bigText(result))
                            .setAutoCancel(true)
                            .build(),
                    )
                }
            } finally {
                pending.finish()
            }
        }.start()
    }

    companion object {
        private const val ACTION_ADD = "com.feerlessleadr.embidextrous.ADD_TIME"
        private const val EXTRA_USER_ID = "user_id"
        private const val EXTRA_NAME = "name"
        private const val EXTRA_MINUTES = "minutes"
        private const val EXTRA_NOTIFICATION_ID = "notification_id"

        fun addTimeIntent(context: Context, userId: String, name: String, minutes: Int, notificationId: Int): PendingIntent {
            val intent = Intent(context, ActionReceiver::class.java)
                .setAction(ACTION_ADD)
                .putExtra(EXTRA_USER_ID, userId)
                .putExtra(EXTRA_NAME, name)
                .putExtra(EXTRA_MINUTES, minutes)
                .putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            // One request code per user and amount, so the three buttons never replace each other.
            val requestCode = (notificationId * 31 + minutes) and 0x7fffffff
            return PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }
}
