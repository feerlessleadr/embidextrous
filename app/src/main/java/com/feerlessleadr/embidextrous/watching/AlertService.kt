package com.feerlessleadr.embidextrous.watching

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.feerlessleadr.embidextrous.App
import com.feerlessleadr.embidextrous.MainActivity
import com.feerlessleadr.embidextrous.R
import com.feerlessleadr.embidextrous.data.UserStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Optional background alerts. While the setting is on (and someone is signed in), this foreground service keeps the live
 * connection open so a notification can appear the moment someone reaches their limit, with buttons to add time without
 * opening the app.
 */
class AlertService : Service() {
    private val alerts = LimitAlerts()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var holding = false
    private var collector: Job? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_MONITOR, "Background connection", NotificationManager.IMPORTANCE_MIN)
                .apply { description = "Shown while the app watches for limits in the background." },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, "Limit reached", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "When someone reaches their watch-time limit." },
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val ongoing = NotificationCompat.Builder(this, CHANNEL_MONITOR)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Hall Monitor is watching for limits")
            .setContentText("You will be alerted when someone reaches their limit.")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(openAppIntent())
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                ServiceCompat.startForeground(this, MONITOR_ID, ongoing, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(MONITOR_ID, ongoing)
            }
        } catch (e: RuntimeException) {
            stopSelf()
            return START_NOT_STICKY
        }

        val container = (application as App).container
        if (!holding) {
            holding = true
            container.hallMonitor.acquire()
            collector = scope.launch {
                container.hallMonitor.state.collect { state ->
                    // Nothing to watch once signed out, or without a key: stop instead of holding a notification for nothing.
                    if (container.session.value == null || container.pluginKey.value == null) {
                        stopSelf()
                    } else if (state.loaded) {
                        val changes = alerts.update(state.cards)
                        // Someone got time (from any phone or the dashboard): their alert is out of date, so it goes on this phone too.
                        val nm = getSystemService(NotificationManager::class.java)
                        for (user in changes.cleared) nm.cancel(notificationId(user.userId))
                        for (user in changes.blocked) notifyLimitReached(user)
                    }
                }
            }
        }
        return START_STICKY
    }

    private fun notifyLimitReached(user: UserStatus) {
        if (!canNotify(this)) return
        val name = user.username.ifEmpty { "A user" }
        val id = notificationId(user.userId)
        val builder = NotificationCompat.Builder(this, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$name reached their ${user.periodLabel} limit")
            .setContentText("Playback was stopped. Add time to let them continue.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
        // On the lock screen only a generic notice is shown (no names, no buttons); the buttons also ask for the screen lock.
        val lockScreenVersion: Notification = NotificationCompat.Builder(this, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Hall Monitor")
            .setContentText("A viewing limit was reached. Unlock to see who.")
            .build()
        builder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(lockScreenVersion)
        for (minutes in QUICK_MINUTES) {
            builder.addAction(
                NotificationCompat.Action.Builder(0, "+$minutes min", ActionReceiver.addTimeIntent(this, user.userId, name, minutes, id))
                    .setAuthenticationRequired(true)
                    .build(),
            )
        }
        getSystemService(NotificationManager::class.java).notify(id, builder.build())
    }

    private fun openAppIntent(): PendingIntent {
        val open = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    override fun onDestroy() {
        collector?.cancel()
        if (holding) {
            (application as App).container.hallMonitor.release()
            holding = false
        }
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_MONITOR = "monitor"
        const val CHANNEL_ALERTS = "alerts"
        private const val MONITOR_ID = 1
        val QUICK_MINUTES = intArrayOf(15, 30, 60)

        /** One alert per person: the same id every time, so it can be replaced or cancelled. */
        fun notificationId(userId: String): Int = userId.hashCode() and 0x7fffffff

        fun canNotify(context: Context): Boolean =
            Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        /** Starts or stops the service to match the setting, the notification permission and the sign-in. Call from the app and at boot. */
        fun sync(context: Context) {
            val app = context.applicationContext
            val container = (app as App).container
            val wanted = container.settings.alertsEnabled.value && canNotify(app) &&
                container.session.value != null && container.pluginKey.value != null
            val intent = Intent(app, AlertService::class.java)
            if (wanted) {
                try {
                    ContextCompat.startForegroundService(app, intent)
                } catch (e: RuntimeException) {
                    // Android refused to start it from the background; it starts the next time the app opens.
                }
            } else {
                app.stopService(intent)
            }
        }
    }
}
