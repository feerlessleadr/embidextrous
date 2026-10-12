package com.feerlessleadr.embidextrous.watching

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.feerlessleadr.embidextrous.App
import com.feerlessleadr.embidextrous.MainActivity
import com.feerlessleadr.embidextrous.R
import com.feerlessleadr.embidextrous.data.ApiException
import com.feerlessleadr.embidextrous.data.UserStatus
import kotlinx.coroutines.runBlocking
import java.text.DateFormat
import java.time.Instant
import java.time.ZoneId
import java.util.Date
import java.util.Locale

/**
 * The home-screen widget: each limited user and how much time they have left. It follows live data while the app or the
 * alert service is running, and otherwise refreshes itself every 30 minutes (Android's minimum for widgets).
 */
class WatchingWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val app = context.applicationContext
        val container = (app as App).container
        val state = container.watchingEye.state.value
        if (state.loaded) {
            WidgetUpdater.render(app, state.cards.map { it.status }, live = state.link == Link.LIVE)
            return
        }
        val pending = goAsync()
        Thread {
            try {
                WidgetUpdater.refreshOnce(app)
            } finally {
                pending.finish()
            }
        }.start()
    }
}

/** Draws the widget from the latest data. Does nothing when no widget has been placed. */
object WidgetUpdater {
    private val ROWS = intArrayOf(R.id.row1, R.id.row2, R.id.row3, R.id.row4)
    private var lastSignature = ""

    private fun widgetIds(context: Context, manager: AppWidgetManager) =
        manager.getAppWidgetIds(ComponentName(context, WatchingWidget::class.java))

    /** One quick request to the server, for when nothing else keeps the data fresh (the widget's own 30-minute refresh). */
    fun refreshOnce(context: Context) {
        val container = (context.applicationContext as App).container
        val client = container.watchingEyeClient()
        if (client == null) {
            renderMessage(context, "Open the app, sign in and enter the Hall Monitor key.")
            return
        }
        try {
            render(context, runBlocking { client.status() }, live = false)
        } catch (e: ApiException) {
            renderMessage(context, e.message.orEmpty())
        } catch (e: Exception) {
            renderMessage(context, "Could not reach the server.")
        }
    }

    fun render(context: Context, users: List<UserStatus>, live: Boolean) {
        val container = (context.applicationContext as App).container
        val hideNames = container.settings.widgetHideNames.value
        val now = Instant.now()
        val lines = ArrayList<String>()
        users.forEachIndexed { index, user ->
            lines.add(WidgetText.row(user, now, ZoneId.systemDefault(), Locale.getDefault(), if (hideNames) "User ${index + 1}" else null))
        }
        if (lines.isEmpty()) lines.add("No limited users yet.")
        val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())
        draw(context, lines, if (live) "Live · $time" else "Updated $time", WidgetText.summary(users, now, hideNames))
    }

    /** Replaces whatever the widget shows (names, times) once the person has signed out. */
    fun showSignedOut(context: Context) {
        lastSignature = ""
        renderMessage(context, "Signed out. Open the app to sign in.")
    }

    private fun renderMessage(context: Context, message: String) = draw(context, listOf(message), "Tap to open the app", null)

    @Synchronized
    private fun draw(context: Context, lines: List<String>, footer: String, summary: WidgetText.Summary?) {
        val accent = WidgetColors.accent(context)
        val manager = AppWidgetManager.getInstance(context)
        val ids = widgetIds(context, manager)
        if (ids.isEmpty()) return
        // The footer carries a clock; only redraw when the lines changed, so a stream of identical pushes costs nothing.
        val signature = WidgetColors.accent(context).toString() + "|" + summary?.text.orEmpty() + "|" + lines.joinToString("|")
        if (signature == lastSignature && footer.startsWith("Live")) return
        lastSignature = signature

        val views = RemoteViews(context.packageName, R.layout.widget_status)
        views.setTextColor(R.id.widgetTitle, accent)
        for (i in ROWS.indices) {
            if (i < lines.size) {
                views.setViewVisibility(ROWS[i], View.VISIBLE)
                views.setTextViewText(ROWS[i], lines[i])
            } else {
                views.setViewVisibility(ROWS[i], View.GONE)
            }
        }
        if (summary == null) {
            views.setViewVisibility(R.id.widgetSummary, View.GONE)
        } else {
            views.setViewVisibility(R.id.widgetSummary, View.VISIBLE)
            views.setTextViewText(R.id.widgetSummary, summary.text)
            views.setTextColor(R.id.widgetSummary, if (summary.needsAttention) WidgetColors.warning(context) else accent)
        }
        views.setTextViewText(R.id.widgetFooter, if (lines.size > ROWS.size) "+${lines.size - ROWS.size} more · $footer" else footer)
        val open = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        views.setOnClickPendingIntent(
            R.id.widgetRoot,
            PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
        )
        manager.updateAppWidget(ids, views)
    }
}
