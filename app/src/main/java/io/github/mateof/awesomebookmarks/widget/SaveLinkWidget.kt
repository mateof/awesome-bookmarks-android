// Copyright (C) 2026 mateof
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mateof.awesomebookmarks.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import io.github.mateof.awesomebookmarks.MainActivity
import io.github.mateof.awesomebookmarks.R
import io.github.mateof.awesomebookmarks.save.SaveBookmarkActivity

/**
 * Home screen shortcut: one tap into the save sheet, one into the library.
 * A browser bookmark to the web UI can offer neither.
 */
class SaveLinkWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { widgetId ->
            val views = RemoteViews(context.packageName, R.layout.widget_save_link).apply {
                setOnClickPendingIntent(R.id.widget_save, activityIntent(context, SaveBookmarkActivity::class.java, 0))
                setOnClickPendingIntent(R.id.widget_open, activityIntent(context, MainActivity::class.java, 1))
            }
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }

    private fun activityIntent(context: Context, target: Class<*>, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
