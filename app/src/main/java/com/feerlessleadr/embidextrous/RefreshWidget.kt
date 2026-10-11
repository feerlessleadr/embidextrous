package com.feerlessleadr.embidextrous

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

// A home-screen widget: one tap refreshes all smart lists, and it says how that went.
// This receiver is exported (the system delivers widget updates to it) so it does nothing but draw. The tap goes to
// RefreshReceiver, which is not exported: no other app can send it the refresh action.
class RefreshWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { manager.updateAppWidget(it, views(context, null)) }
    }

    companion object {
        // Draws the widget with an optional status line; its tap sends an explicit, immutable intent to RefreshReceiver.
        fun views(context: Context, status: String?): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_refresh)
            if (status != null) views.setTextViewText(R.id.widget_status, status)
            val click = Intent(context, RefreshReceiver::class.java).setAction(RefreshReceiver.ACTION_REFRESH)
            views.setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getBroadcast(context, 0, click, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
            )
            return views
        }

        fun update(context: Context, status: String) {
            val manager = AppWidgetManager.getInstance(context)
            manager.getAppWidgetIds(ComponentName(context, RefreshWidget::class.java)).forEach { manager.updateAppWidget(it, views(context, status)) }
        }
    }
}