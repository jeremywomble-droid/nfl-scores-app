package com.nflscores.app

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService

/** Backs the widget's scrollable game list. */
class WidgetGamesService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = GamesFactory(applicationContext)
}

class GamesFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    private var games: List<Game> = emptyList()
    private var preds: List<Prediction> = emptyList()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        // Runs on a background thread — network fetch is allowed here.
        try {
            games = EspnApi.fetchWeek(Predictions.WEEK)
        } catch (e: Exception) {
            games = emptyList()
        }
        try {
            preds = Predictions.load(context)
        } catch (e: Exception) {
            preds = emptyList()
        }
        games = games.sortedWith(
            compareBy(
                { g: Game -> when (g.state) { "in" -> 0; "pre" -> 1; else -> 2 } },
                { g: Game -> g.dateMillis }
            )
        )
    }

    override fun onDestroy() {
        games = emptyList()
    }

    override fun getCount(): Int = games.size

    override fun getViewAt(position: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_game_row)
        if (position < 0 || position >= games.size) return views
        val g = games[position]
        val main = if (g.state == "pre") {
            "${g.awayAbbr} @ ${g.homeAbbr} · ${g.statusText}"
        } else {
            "${g.awayAbbr} ${g.awayScore} @ ${g.homeAbbr} ${g.homeScore} · ${g.statusText}"
        }
        views.setTextViewText(R.id.rowMain, main)
        val p = Predictions.find(preds, g)
        val predLine = if (p != null) {
            if (g.state == "post") {
                val winner = when {
                    g.awayScore > g.homeScore -> g.awayAbbr
                    g.homeScore > g.awayScore -> g.homeAbbr
                    else -> ""
                }
                val exact = g.awayScore == p.predAway && g.homeScore == p.predHome
                val mark = when {
                    exact -> "🎯 EXACT"
                    winner == p.pick -> "✓ Winner"
                    else -> "✗ Winner"
                }
                "Pred: ${p.away} ${p.predAway} — ${p.home} ${p.predHome} · ${p.pick} ${p.confidence}% · $mark"
            } else {
                "Pred: ${p.away} ${p.predAway} — ${p.home} ${p.predHome} · ${p.pick} ${p.confidence}%"
            }
        } else {
            ""
        }
        views.setTextViewText(R.id.rowPred, predLine)
        // Row tap opens the app (fill-in for the template set on the list).
        views.setOnClickFillInIntent(R.id.rowRoot, Intent())
        return views
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = true
}
