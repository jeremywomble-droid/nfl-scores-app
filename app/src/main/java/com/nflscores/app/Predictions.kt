package com.nflscores.app

import android.content.Context
import org.json.JSONObject

data class Prediction(
    val week: Int,
    val away: String,
    val home: String,
    val predAway: Int,
    val predHome: Int,
    val pick: String,
    val confidence: Int
)

object Predictions {
    const val WEEK = 7

    fun load(context: Context): List<Prediction> {
        val text = context.assets.open("predictions.json").bufferedReader().use { it.readText() }
        val root = JSONObject(text)
        val arr = root.optJSONArray("predictions") ?: return emptyList()
        val out = mutableListOf<Prediction>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(
                Prediction(
                    week = o.optInt("week", WEEK),
                    away = o.optString("away"),
                    home = o.optString("home"),
                    predAway = o.optInt("predAway"),
                    predHome = o.optInt("predHome"),
                    pick = o.optString("pick"),
                    confidence = o.optInt("confidence")
                )
            )
        }
        return out
    }

    fun find(preds: List<Prediction>, game: Game): Prediction? =
        preds.firstOrNull { it.away == game.awayAbbr && it.home == game.homeAbbr }

    /** Grade finals: returns (graded, winnerCorrect, exactScores). */
    fun grade(preds: List<Prediction>, games: List<Game>): Triple<Int, Int, Int> {
        var graded = 0
        var winners = 0
        var exact = 0
        for (g in games) {
            if (g.state != "post") continue
            val p = find(preds, g) ?: continue
            graded++
            val actualWinner = when {
                g.awayScore > g.homeScore -> g.awayAbbr
                g.homeScore > g.awayScore -> g.homeAbbr
                else -> ""
            }
            if (actualWinner == p.pick) winners++
            if (g.awayScore == p.predAway && g.homeScore == p.predHome) exact++
        }
        return Triple(graded, winners, exact)
    }

    fun statsLine(preds: List<Prediction>, games: List<Game>): String {
        val (graded, winners, exact) = grade(preds, games)
        val avgConf = if (preds.isEmpty()) 0 else preds.sumOf { it.confidence } / preds.size
        return if (graded == 0) {
            "Picks locked for Week $WEEK · avg confidence $avgConf% · no finals yet"
        } else {
            "Winner picks: $winners/$graded correct · Exact scores: $exact · avg confidence $avgConf%"
        }
    }
}
