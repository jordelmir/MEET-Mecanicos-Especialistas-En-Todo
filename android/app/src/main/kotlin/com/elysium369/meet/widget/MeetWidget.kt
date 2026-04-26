package com.elysium369.meet.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.elysium369.meet.MainActivity
import com.elysium369.meet.R

class MeetWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }
}

internal fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
    // This requires layout XMLs which we will just define basic calls for
    val views = RemoteViews(context.packageName, android.R.layout.simple_list_item_2) // Fallback layout
    
    // Set text to placeholder
    views.setTextViewText(android.R.id.text1, "MEET OBD2")
    views.setTextViewText(android.R.id.text2, "Esperando conexión...")
    
    val intent = Intent(context, MainActivity::class.java)
    val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    views.setOnClickPendingIntent(android.R.id.text1, pendingIntent)

    appWidgetManager.updateAppWidget(appWidgetId, views)
}
