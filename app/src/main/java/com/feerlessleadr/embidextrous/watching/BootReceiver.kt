package com.feerlessleadr.embidextrous.watching

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts the optional background alerts after the phone has been restarted. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) AlertService.sync(context)
    }
}
