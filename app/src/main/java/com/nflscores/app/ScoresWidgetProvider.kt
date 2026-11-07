package com.nflscores.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import kotlin.concurrent.thread

/**
 * Semi-transparent, scrollable home-screen widget: every game of the current
 * predictions week with the actual score on top and Muse's locked prediction
 * underneath. The picks (and their week) come from the nfl-scores web app,
 * fetched at runtime by [Predictions] from the repo's data/predictions.json
 * feed (assets/predictions.json is only the offline fallback snapshot).
 *
 * Refresh is game-aware (the system widget timer is OFF, updatePeriodMillis=0):
 *  - any game live        -> wake again in ~5 minutes
 *  - a kickoff is ahead   -> wake at that kickoff
 *  - everything final     -> check back in ~12 hours
 * Alarms use setAndAllowWhileIdle (no exact-alarm permission needed). Android
 * may stretch background wake-ups while the phone is idle/dozing; while the
 * screen is in use the refreshes land on schedule.
 */
class ScoresWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH || intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ScoresWidgetProvider::class.java))
            if (ids.isNotEmpty()) onUpdate(context, manager, ids)
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            manager.updateAppWidget(id, buildViews(context, "Loading scores…"))
        }
        val pending = goAsync()
        thread {
            var games: List<Game> = emptyList()
            var stats = "Could not load — tap to open the app"
            try {
                val preds = Predictions.load(context)
                if (Predictions.week > 0) games = EspnApi.fetchWeek(Predictions.week)
                stats = Predictions.statsLine(preds, games)
            } catch (e: Exception) {
                // keep the fallback stats text
            }
            try {
                for (id in ids) {
                    manager.updateAppWidget(id, buildViews(context, stats))
                    manager.notifyAppWidgetViewDataChanged(id, R.id.widgetList)
                }
                scheduleNext(context, games)
            } finally {
                pending.finish()
            }
        }
    }

    private fun buildViews(context: Context, stats: String): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_layout)
        val title = if (Predictions.week > 0) "🏈 NFL — WEEK ${Predictions.week}" else "🏈 NFL SCORES"
        views.setTextViewText(R.id.widgetTitle, title)
        views.setTextViewText(R.id.widgetStats, stats)
        val adapterIntent = Intent(context, WidgetGamesService::class.java)
        views.setRemoteAdapter(R.id.widgetList, adapterIntent)
        val openApp = Intent(context, MainActivity::class.java)
        val template = PendingIntent.getActivity(
            context, 0, openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setPendingIntentTemplate(R.id.widgetList, template)
        views.setOnClickPendingIntent(R.id.widgetTitle, template)
        return views
    }

    private fun scheduleNext(context: Context, games: List<Game>) {
        val now = System.currentTimeMillis()
        val next: Long = when {
            games.any { it.state == "in" } -> now + 5 * 60 * 1000L
            games.any { it.state == "pre" } -> {
                val kickoff = games.filter { it.state == "pre" }.minOf { it.dateMillis }
                if (kickoff > now) kickoff + 60 * 1000L else now + 5 * 60 * 1000L
            }
            games.isNotEmpty() -> now + 12 * 60 * 60 * 1000L
            else -> now + 60 * 60 * 1000L // fetch failed: retry in an hour
        }
        val intent = Intent(context, ScoresWidgetProvider::class.java).apply {
            action = ACTION_REFRESH
        }
        val pi = PendingIntent.getBroadcast(
            context, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
    }

    companion object {
        const val ACTION_REFRESH = "com.nflscores.app.ACTION_WIDGET_REFRESH"

        /** Ask any placed widgets to refresh now (used when the app is opened). */
        fun requestUpdate(context: Context) {
            val intent = Intent(context, ScoresWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
            }
            context.sendBroadcast(intent)
        }
    }
}
