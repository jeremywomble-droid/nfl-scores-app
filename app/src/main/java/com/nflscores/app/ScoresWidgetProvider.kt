package com.nflscores.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import kotlin.concurrent.thread

/**
 * Semi-transparent home-screen widget: Week 7 games with the actual score
 * on top and Muse's locked prediction underneath each one.
 * Background is 62% opaque black (see res/drawable/widget_bg.xml).
 */
class ScoresWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_layout)
            views.setTextViewText(R.id.widgetTitle, "🏈 NFL — WEEK 7")
            views.setTextViewText(R.id.widgetStats, "Loading scores…")
            attachClick(context, views)
            manager.updateAppWidget(id, views)
        }
        val pending = goAsync()
        thread {
            try {
                val games = EspnApi.fetchWeek(Predictions.WEEK)
                val preds = Predictions.load(context)
                for (id in ids) {
                    val views = RemoteViews(context.packageName, R.layout.widget_layout)
                    fill(context, views, games, preds)
                    attachClick(context, views)
                    manager.updateAppWidget(id, views)
                }
            } catch (e: Exception) {
                for (id in ids) {
                    val views = RemoteViews(context.packageName, R.layout.widget_layout)
                    views.setTextViewText(R.id.widgetStats, "Could not load — open the app to retry")
                    attachClick(context, views)
                    manager.updateAppWidget(id, views)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun attachClick(context: Context, views: RemoteViews) {
        val intent = Intent(context, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widgetRoot, pi)
    }

    private fun fill(context: Context, views: RemoteViews, games: List<Game>, preds: List<Prediction>) {
        views.setTextViewText(R.id.widgetTitle, "🏈 NFL — WEEK 7")
        views.setTextViewText(R.id.widgetStats, Predictions.statsLine(preds, games))

        val sorted = games.sortedWith(
            compareBy(
                { g: Game -> when (g.state) { "in" -> 0; "pre" -> 1; else -> 2 } },
                { g: Game -> g.dateMillis }
            )
        )
        val rowIds = listOf(
            R.id.widgetRow1, R.id.widgetRow2, R.id.widgetRow3, R.id.widgetRow4, R.id.widgetRow5
        )
        val shown = sorted.take(5)
        for (i in rowIds.indices) {
            if (i < shown.size) {
                val g = shown[i]
                val p = Predictions.find(preds, g)
                val main = if (g.state == "pre") {
                    "${g.awayAbbr} @ ${g.homeAbbr} · ${g.statusText}"
                } else {
                    "${g.awayAbbr} ${g.awayScore} @ ${g.homeAbbr} ${g.homeScore} · ${g.statusText}"
                }
                val predLine = if (p != null) {
                    "\nPred: ${p.away} ${p.predAway} — ${p.home} ${p.predHome} · ${p.pick} ${p.confidence}%"
                } else {
                    ""
                }
                views.setTextViewText(rowIds[i], main + predLine)
            } else {
                views.setTextViewText(rowIds[i], "")
            }
        }
        val remaining = games.size - shown.size
        views.setTextViewText(
            R.id.widgetMore,
            if (remaining > 0) "+$remaining more games — tap to open the app" else "Tap to open the app"
        )
    }
}
