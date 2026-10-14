package com.feerlessleadr.embidextrous.watching

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.feerlessleadr.embidextrous.App
import com.feerlessleadr.embidextrous.R
import com.feerlessleadr.embidextrous.notifications.NotifPoster
import com.feerlessleadr.embidextrous.notifications.NotifService
import com.feerlessleadr.embidextrous.push.EmbyTopic
import com.feerlessleadr.embidextrous.push.TracearrLive
import com.feerlessleadr.embidextrous.push.TracearrLiveListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * The background listener. While at least one notification rule is on (and this phone can reach what it watches), this foreground service keeps the
 * live connections open: Hall Monitor's status, Emby's websocket for what plugins and the server push, and Tracearr's event stream. Everything that
 * arrives goes to [com.feerlessleadr.embidextrous.notifications.NotifHub], which decides what to show. Push is the main path; this service never polls.
 */
class AlertService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var supervisor: Job? = null

    override fun onCreate() {
        super.onCreate()
        NotifPoster.createChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val ongoing = NotificationCompat.Builder(this, NotifPoster.CHANNEL_MONITOR)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Embidextrous is listening for alerts")
            .setContentText("You will be told about the things you chose in Settings, Notifications.")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(NotifPoster.openIntent(this, null))
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
        running = true
        if (supervisor == null) supervisor = scope.launch { supervise() }
        return START_STICKY
    }

    /** Keeps the right connections open for what is switched on, and changes them when that changes. */
    private suspend fun supervise() {
        val container = (application as App).container
        val hub = container.notifHub
        val settings = container.settings
        data class Config(val needed: Set<NotifService>, val token: String?, val tracearr: String?)
        combine(
            listOf<kotlinx.coroutines.flow.Flow<Any?>>(
                settings.notifRules, settings.serviceSwitches, container.session, container.pluginKey, container.tracearr, settings.profile, settings.customSmartLists,
            ),
        ) { Config(hub.connectionsNeeded(), container.session.value?.token, container.tracearr.value?.url) }
            .distinctUntilChanged()
            .collectLatest { config ->
                // Nothing to watch (signed out, no key, every rule off): stop instead of holding a notification for nothing.
                if (config.needed.isEmpty() || config.token == null) {
                    stopSelf()
                    return@collectLatest
                }
                hub.resetLooks()
                coroutineScope {
                    if (NotifService.HallMonitor in config.needed) launch { watchHallMonitor() }
                    val pushed = config.needed - NotifService.HallMonitor - NotifService.Tracearr
                    if (pushed.isNotEmpty()) launch { watchPushes(pushed) }
                    if (NotifService.Tracearr in config.needed) launch { watchTracearr() }
                }
            }
    }

    private suspend fun watchHallMonitor() {
        val container = (application as App).container
        container.hallMonitor.acquire()
        try {
            container.hallMonitor.state.collect { container.notifHub.onHallMonitor(it) }
        } finally {
            container.hallMonitor.release()
        }
    }

    /** Emby's websocket: what plugins and the server push on their own, plus the task list when a task alert is wanted. */
    private suspend fun watchPushes(services: Set<NotifService>): Unit = coroutineScope {
        val container = (application as App).container
        val hub = container.notifHub
        val topics = buildList {
            add(EmbyTopic.Events)
            if (NotifService.Server in services) add(EmbyTopic.ScheduledTasks)
        }
        val lease = container.push.acquire(*topics.toTypedArray())
        try {
            launch { container.push.events.collect { hub.onPush(it.type, it.data) } }
            // Each time the connection comes up, ask the plugins what they sent while it was down (and refresh their alert lists).
            launch {
                var wasUp = false
                container.push.connected.collect { up ->
                    if (up && !wasUp) {
                        for (s in services) {
                            if (s.apiBase == null) continue
                            launch {
                                hub.catchUp(s)
                                hub.refreshCatalog(s)
                            }
                        }
                    }
                    wasUp = up
                }
            }
            awaitCancellation()
        } finally {
            lease.release()
        }
    }

    private suspend fun watchTracearr(): Unit = coroutineScope {
        val container = (application as App).container
        val hub = container.notifHub
        val connection = container.tracearr.value ?: return@coroutineScope
        val client = container.tracearrClient() ?: return@coroutineScope
        var names = emptyMap<String, String>()
        TracearrLive(connection, container.httpClient).run(
            object : TracearrLiveListener {
                override fun onConnected(connected: Boolean) = Unit

                override fun onReady() {
                    launch {
                        names = try { client.servers().servers.associate { it.id to it.name } } catch (e: Exception) { names }
                        hub.checkViolations(client)
                    }
                }

                override fun onEvent(type: String, data: String) {
                    when (type) {
                        "violation.created" -> launch { hub.checkViolations(client) }
                        "server.health" -> hub.onTracearrHealth(data, names)
                    }
                }

                override fun onKeyRejected() = Unit
            },
        )
    }

    override fun onDestroy() {
        running = false
        supervisor?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        /** Kept for the notification buttons, which were written against this class. */
        val CHANNEL_ALERTS: String = NotifService.HallMonitor.channelId
        private const val MONITOR_ID = 1
        val QUICK_MINUTES = NotifPoster.QUICK_MINUTES

        /** True while the service runs; the backup job does nothing then, because the live connections already deliver. */
        @Volatile var running = false

        fun notificationId(key: String): Int = NotifPoster.notificationId(key)

        fun canNotify(context: Context): Boolean = NotifPoster.canNotify(context)

        /** Starts or stops the service to match the rules, the notification permission and the sign-in. Call from the app and at boot. */
        fun sync(context: Context) {
            val app = context.applicationContext
            val container = (app as App).container
            val wanted = container.notifHub.connectionsNeeded().isNotEmpty() && canNotify(app) && container.session.value != null
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
            BackupPollJob.sync(app)
        }
    }
}
