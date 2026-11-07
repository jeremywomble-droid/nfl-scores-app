package com.nflscores.app

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class Prediction(
    val week: Int,
    val away: String,
    val home: String,
    val predAway: Int,
    val predHome: Int,
    val pick: String,
    val confidence: Int
)

/**
 * Predictions are NOT computed in this app. They are the analyst model's
 * LOCKED picks from the nfl-scores web app (its game_picks store) — one
 * source of truth, two surfaces.
 *
 * Since v1.2.2 the picks arrive as a RUNTIME FEED, not a build-time sync:
 * data/predictions.json in this repo (regenerated weekly from the web app's
 * `getscores` action by scripts/sync_predictions.py) is fetched from GitHub
 * at runtime. [load] is network-first:
 *   1. fetch the feed (short timeout) and cache it to internal storage;
 *   2. on any fetch failure, use the last-good cached feed;
 *   3. on first run with no cache, fall back to the bundled
 *      assets/predictions.json snapshot (refreshed whenever a release is
 *      built anyway).
 * The week is read from whichever file supplied the picks, never hardcoded.
 * [load] does network I/O — call it from a background thread only (every
 * current call site does).
 */
object Predictions {

    private const val FEED_URL =
        "https://raw.githubusercontent.com/jeremywomble-droid/nfl-scores-app/main/data/predictions.json"
    private const val CACHE_FILE = "predictions-feed-cache.json"
    private const val FETCH_TIMEOUT_MS = 6000

    /** Week of the currently loaded predictions; set by [load]. 0 = not loaded. */
    @Volatile
    var week: Int = 0
        private set

    fun load(context: Context): List<Prediction> {
        // 1) Network-first: fetch the live feed and cache it as last-good.
        val fetched = try {
            fetchFeed()
        } catch (e: Exception) {
            null
        }
        if (fetched != null) {
            val parsed = parse(fetched)
            if (parsed != null) {
                try {
                    File(context.filesDir, CACHE_FILE).writeText(fetched)
                } catch (e: Exception) {
                    // Cache write is best-effort; the parsed picks still count.
                }
                return parsed
            }
        }
        // 2) Last-good cached feed from a previous successful fetch.
        try {
            val cached = File(context.filesDir, CACHE_FILE).readText()
            parse(cached)?.let { return it }
        } catch (e: Exception) {
            // No cache yet — fall through to the bundled snapshot.
        }
        // 3) Bundled snapshot (offline / first-run fallback).
        val text = context.assets.open("predictions.json").bufferedReader().use { it.readText() }
        return parse(text) ?: emptyList()
    }

    private fun fetchFeed(): String {
        val conn = URL(FEED_URL).openConnection() as HttpURLConnection
        conn.connectTimeout = FETCH_TIMEOUT_MS
        conn.readTimeout = FETCH_TIMEOUT_MS
        conn.useCaches = false
        conn.setRequestProperty("Cache-Control", "no-cache")
        conn.setRequestProperty("User-Agent", "NFLScores-Android")
        if (conn.responseCode != HttpURLConnection.HTTP_OK) {
            throw IllegalStateException("predictions feed HTTP ${conn.responseCode}")
        }
        return conn.inputStream.bufferedReader().use { it.readText() }
    }

    /** Parse one predictions file; sets [week]. Null if it carries no picks. */
    private fun parse(text: String): List<Prediction>? {
        val root = JSONObject(text)
        val arr = root.optJSONArray("predictions") ?: return null
        val out = mutableListOf<Prediction>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(
                Prediction(
                    week = o.optInt("week", 0),
                    away = o.optString("away"),
                    home = o.optString("home"),
                    predAway = o.optInt("predAway"),
                    predHome = o.optInt("predHome"),
                    pick = o.optString("pick"),
                    confidence = o.optInt("confidence")
                )
            )
        }
        if (out.isEmpty()) return null
        week = root.optInt("week", out.firstOrNull()?.week ?: 0)
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
            "Picks locked for Week $week · avg confidence $avgConf% · no finals yet"
        } else {
            "Winner picks: $winners/$graded correct · Exact scores: $exact · avg confidence $avgConf%"
        }
    }
}
