package com.feerlessleadr.embidextrous

import android.content.Context
import com.feerlessleadr.embidextrous.data.ApiException
import java.text.DateFormat
import java.util.Date

/** "Refresh all lists" without opening the app: used by the home-screen widget and the quick-settings tile. */
object QuickRefresh {
    /** Asks the server to refresh every list. Returns a short sentence for the widget or tile to show. */
    suspend fun run(context: Context): String {
        val container = (context.applicationContext as App).container
        val api = container.api() ?: return "Sign in to the app first"
        return try {
            api.refreshAll()
            "Started at " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())
        } catch (e: ApiException) {
            if (e.isUnauthorized) "Signed out: open the app" else e.message ?: "Failed"
        }
    }
}