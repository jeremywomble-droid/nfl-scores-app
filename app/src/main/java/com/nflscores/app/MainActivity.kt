package com.nflscores.app

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private lateinit var container: LinearLayout
    private lateinit var updatedText: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            loadData()
            handler.postDelayed(this, 60000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        container = findViewById(R.id.contentContainer)
        updatedText = findViewById(R.id.updatedText)
        findViewById<Button>(R.id.refreshButton).setOnClickListener { loadData() }
    }

    override fun onResume() {
        super.onResume()
        ScoresWidgetProvider.requestUpdate(this)
        handler.removeCallbacks(refreshRunnable)
        handler.post(refreshRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refreshRunnable)
    }

    private fun loadData() {
        updatedText.text = "Updating…"
        thread {
            val preds = try {
                Predictions.load(this)
            } catch (e: Exception) {
                emptyList()
            }
            val predWeek = Predictions.week
            var predGames: List<Game> = emptyList()
            var current: List<Game> = emptyList()
            var currentWeek = 0
            var error: String? = null
            try {
                if (predWeek > 0) predGames = EspnApi.fetchWeek(predWeek)
                current = EspnApi.fetchCurrent()
                currentWeek = EspnApi.currentWeekNumber()
            } catch (e: Exception) {
                error = e.message ?: "network error"
            }
            runOnUiThread { render(predGames, current, currentWeek, preds, error) }
        }
    }

    private fun render(
        predGames: List<Game>,
        current: List<Game>,
        currentWeek: Int,
        preds: List<Prediction>,
        error: String?
    ) {
        container.removeAllViews()
        val timeFmt = SimpleDateFormat("h:mm:ss a", Locale.US)
        updatedText.text = if (error != null) {
            "Could not load scores ($error) — check connection and tap Refresh"
        } else {
            "Updated ${timeFmt.format(Date())} · auto-refreshes every minute"
        }

        // ---- Predictions section (week comes from the web-app picks feed) ----
        val predWeek = Predictions.week
        container.addView(sectionTitle("🔮 CLAUDE'S WEEK $predWeek PREDICTIONS — LOCKED"))
        if (predGames.isEmpty()) {
            container.addView(bodyText("Week $predWeek games not available yet."))
        } else {
            container.addView(statsText(Predictions.statsLine(preds, predGames)))
            val sorted = predGames.sortedWith(compareBy({ stateOrder(it.state) }, { it.dateMillis }))
            for (g in sorted) {
                container.addView(gameCard(g, Predictions.find(preds, g), showPrediction = true))
            }
        }

        // ---- Current week section ----
        if (currentWeek != predWeek && currentWeek > 0 && current.isNotEmpty()) {
            container.addView(sectionTitle("📅 THIS WEEK (WEEK $currentWeek)"))
            val sorted = current.sortedWith(compareBy({ stateOrder(it.state) }, { it.dateMillis }))
            for (g in sorted) {
                container.addView(gameCard(g, null, showPrediction = false))
            }
        }
    }

    private fun stateOrder(state: String): Int = when (state) {
        "in" -> 0
        "pre" -> 1
        else -> 2
    }

    private fun sectionTitle(text: String): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.setTextColor(Color.parseColor("#FFC857"))
        tv.textSize = 15f
        tv.setTypeface(null, Typeface.BOLD)
        tv.setPadding(6, 22, 6, 8)
        return tv
    }

    private fun statsText(text: String): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.setTextColor(Color.parseColor("#9FE8B1"))
        tv.textSize = 13f
        tv.setPadding(8, 2, 8, 10)
        return tv
    }

    private fun bodyText(text: String): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.setTextColor(Color.parseColor("#D6DEE8"))
        tv.textSize = 14f
        tv.setPadding(8, 6, 8, 6)
        return tv
    }

    private fun gameCard(g: Game, p: Prediction?, showPrediction: Boolean): LinearLayout {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.setBackgroundColor(Color.parseColor("#161B22"))
        card.setPadding(14, 12, 14, 12)
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.setMargins(4, 5, 4, 5)
        card.layoutParams = lp

        val scoreLine = TextView(this)
        val scoreText = if (g.state == "pre") {
            "${g.awayAbbr} (${g.awayRecord})  @  ${g.homeAbbr} (${g.homeRecord})"
        } else {
            "${g.awayAbbr}  ${g.awayScore}   @   ${g.homeAbbr}  ${g.homeScore}"
        }
        scoreLine.text = scoreText
        scoreLine.setTextColor(Color.WHITE)
        scoreLine.textSize = 17f
        scoreLine.setTypeface(null, Typeface.BOLD)
        card.addView(scoreLine)

        val status = TextView(this)
        status.text = g.statusText
        status.setTextColor(
            if (g.state == "in") Color.parseColor("#FF6B5E") else Color.parseColor("#9AA4B2")
        )
        status.textSize = 12f
        card.addView(status)

        if (showPrediction && p != null) {
            val pred = TextView(this)
            val base = "Prediction: ${p.away} ${p.predAway} — ${p.home} ${p.predHome}" +
                "  ·  Pick: ${p.pick}  ·  Confidence ${p.confidence}%"
            pred.text = if (g.state == "post") {
                val actualWinner = when {
                    g.awayScore > g.homeScore -> g.awayAbbr
                    g.homeScore > g.awayScore -> g.homeAbbr
                    else -> ""
                }
                val exact = g.awayScore == p.predAway && g.homeScore == p.predHome
                base + when {
                    exact -> "  ·  🎯 EXACT SCORE"
                    actualWinner == p.pick -> "  ·  ✓ Winner"
                    else -> "  ·  ✗ Winner"
                }
            } else {
                base
            }
            pred.setTextColor(Color.parseColor("#FFC857"))
            pred.textSize = 13f
            pred.setPadding(0, 6, 0, 0)
            card.addView(pred)
        }
        return card
    }
}
