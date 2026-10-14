package com.feerlessleadr.embidextrous.watching

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import com.feerlessleadr.embidextrous.App
import com.feerlessleadr.embidextrous.notifications.NotifService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The failsafe. Every 15 minutes (Android's shortest period) while the background listener is NOT running, this asks each plugin that keeps an event
 * log what it sent while this phone was not listening, and shows it. When the listener runs, pushes already deliver and this does nothing, so polling
 * is only ever a backup. It is scheduled only while at least one rule of such a plugin is on.
 */
class BackupPollJob : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var work: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        if (AlertService.running) return false
        val container = (application as App).container
        work = scope.launch {
            try {
                val hub = container.notifHub
                for (service in hub.connectionsNeeded()) {
                    if (service.apiBase != null) hub.catchUp(service)
                }
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        work?.cancel()
        return true
    }

    companion object {
        private const val JOB_ID = 4711

        /** Schedules the backup poll when a plugin with an event log has a rule on, and cancels it otherwise. */
        fun sync(context: Context) {
            val app = context.applicationContext
            val scheduler = app.getSystemService(JobScheduler::class.java) ?: return
            val container = (app as App).container
            val wanted = container.session.value != null &&
                container.notifHub.connectionsNeeded().any { it.apiBase != null && it != NotifService.HallMonitor }
            if (!wanted) {
                scheduler.cancel(JOB_ID)
                return
            }
            if (scheduler.getPendingJob(JOB_ID) != null) return
            scheduler.schedule(
                JobInfo.Builder(JOB_ID, ComponentName(app, BackupPollJob::class.java))
                    .setPeriodic(15 * 60 * 1000L)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPersisted(true)
                    .build(),
            )
        }
    }
}
