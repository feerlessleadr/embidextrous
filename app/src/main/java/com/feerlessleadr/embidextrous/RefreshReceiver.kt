package com.feerlessleadr.embidextrous

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Handles the widget's tap. Not exported (see the manifest): only this app's own PendingIntent can reach it.
class RefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REFRESH) return
        RefreshWidget.update(context, "Refreshing…")
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                RefreshWidget.update(context, QuickRefresh.run(context))
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.feerlessleadr.embidextrous.action.REFRESH_ALL"
    }
}