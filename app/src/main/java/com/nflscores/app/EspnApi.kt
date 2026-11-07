package com.nflscores.app

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class Game(
    val awayAbbr: String,
    val homeAbbr: String,
    val awayScore: Int,
    val homeScore: Int,
    val awayRecord: String,
    val homeRecord: String,
    val state: String,        // "pre", "in", "post"
    val statusText: String,   // e.g. "Final", "3rd - 4:12", "Sun 1:00 PM"
    val dateMillis: Long
)

object EspnApi {
    // ESPN serves the same scoreboard from two hosts. If the phone's DNS
    // cannot resolve the primary host (seen on Jeremy's Pixel: "Unable to
    // resolve host site.api.espn.com"), fall back to the mirror host, and
    // retry each host once to ride out transient DNS failures.
    private val BASES = listOf(
        "https://site.api.espn.com/apis/site/v2/sports/football/nfl/scoreboard",
        "https://site.web.api.espn.com/apis/site/v2/sports/football/nfl/scoreboard"
    )

    fun fetchWeek(week: Int): List<Game> = parse(fetchJson { base -> "$base?week=$week&seasontype=2" })

    fun fetchCurrent(): List<Game> = parse(fetchJson { base -> base })

    fun currentWeekNumber(): Int {
        val text = fetchJson { base -> base }
        val root = JSONObject(text)
        return root.optJSONObject("week")?.optInt("number", 0) ?: 0
    }

    private fun fetchJson(mkUrl: (String) -> String): String {
        var lastError: Exception? = null
        for (base in BASES) {
            repeat(2) { attempt ->
                try {
                    return fetchOnce(mkUrl(base))
                } catch (e: Exception) {
                    lastError = e
                    if (attempt == 0) Thread.sleep(800)
                }
            }
        }
        throw lastError ?: IllegalStateException("fetch failed")
    }

    private fun fetchOnce(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        // ESPN's CDN rejects the default Java user agent; send a browser UA.
        conn.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 14; Pixel 10 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"
        )
        return conn.inputStream.bufferedReader().use { it.readText() }
    }

    private fun parse(text: String): List<Game> {
        val root = JSONObject(text)
        val events = root.optJSONArray("events") ?: return emptyList()
        val games = mutableListOf<Game>()
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'", Locale.US)
        iso.timeZone = TimeZone.getTimeZone("UTC")
        val kickoffFmt = SimpleDateFormat("EEE h:mm a", Locale.US)
        for (i in 0 until events.length()) {
            val ev = events.optJSONObject(i) ?: continue
            val comps = ev.optJSONArray("competitions") ?: continue
            if (comps.length() == 0) continue
            val comp = comps.optJSONObject(0) ?: continue
            val competitors = comp.optJSONArray("competitors") ?: continue
            var away: JSONObject? = null
            var home: JSONObject? = null
            for (j in 0 until competitors.length()) {
                val c = competitors.optJSONObject(j) ?: continue
                if (c.optString("homeAway") == "away") away = c else home = c
            }
            val a = away ?: continue
            val h = home ?: continue
            val status = ev.optJSONObject("status")
            val type = status?.optJSONObject("type")
            val state = type?.optString("state", "pre") ?: "pre"
            val dateMillis = try {
                iso.parse(ev.optString("date"))?.time ?: 0L
            } catch (e: Exception) {
                0L
            }
            val statusText = when (state) {
                "post" -> type?.optString("shortDetail", "Final") ?: "Final"
                "in" -> {
                    val period = status?.optInt("period", 0) ?: 0
                    val clock = status?.optString("displayClock", "") ?: ""
                    val q = when (period) {
                        1 -> "1st"; 2 -> "2nd"; 3 -> "3rd"; 4 -> "4th"; else -> "OT"
                    }
                    "LIVE $q $clock"
                }
                else -> kickoffFmt.format(Date(dateMillis))
            }
            fun rec(teamObj: JSONObject): String {
                val recs = teamObj.optJSONArray("records")
                return if (recs != null && recs.length() > 0)
                    recs.optJSONObject(0)?.optString("summary", "") ?: "" else ""
            }
            games.add(
                Game(
                    awayAbbr = a.optJSONObject("team")?.optString("abbreviation", "?") ?: "?",
                    homeAbbr = h.optJSONObject("team")?.optString("abbreviation", "?") ?: "?",
                    awayScore = a.optString("score", "0").toIntOrNull() ?: 0,
                    homeScore = h.optString("score", "0").toIntOrNull() ?: 0,
                    awayRecord = rec(a),
                    homeRecord = rec(h),
                    state = state,
                    statusText = statusText,
                    dateMillis = dateMillis
                )
            )
        }
        return games
    }
}
