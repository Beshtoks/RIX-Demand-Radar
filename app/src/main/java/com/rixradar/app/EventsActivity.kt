package com.rixradar.app

import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject

class EventsActivity : AppCompatActivity() {

    private lateinit var tvEventsTitle: TextView
    private lateinit var tvEventsSubtitle: TextView
    private lateinit var tvEventsBlockTitle: TextView
    private lateinit var layoutEventsList: LinearLayout
    private lateinit var tvEventsHint: TextView

    private val serverClient = ServerClient()

    private val handler = Handler(Looper.getMainLooper())
    private val refreshIntervalMs = 60_000L

    private val refreshRunnable = object : Runnable {
        override fun run() {
            loadEvents()
            handler.postDelayed(this, refreshIntervalMs)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_events)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "События"

        bindViews()
        loadEvents()
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(refreshRunnable)
        handler.postDelayed(refreshRunnable, refreshIntervalMs)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refreshRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(refreshRunnable)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun bindViews() {
        tvEventsTitle = findViewById(R.id.tvEventsTitle)
        tvEventsSubtitle = findViewById(R.id.tvEventsSubtitle)
        tvEventsBlockTitle = findViewById(R.id.tvEventsBlockTitle)
        layoutEventsList = findViewById(R.id.layoutEventsList)
        tvEventsHint = findViewById(R.id.tvEventsHint)
    }

    private fun loadEvents() {
        tvEventsTitle.text = "Events"
        tvEventsSubtitle.text = "Loading..."
        tvEventsBlockTitle.text = "Events in next 30 days"
        tvEventsHint.text = "Connecting to backend..."

        Thread {
            val response = serverClient.fetch(ServerConfig.EVENTS_PATH)

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread

                if (!response.success || response.rawBody.isNullOrBlank()) {
                    tvEventsSubtitle.text = "Events server temporarily unavailable"
                    tvEventsHint.text = response.errorMessage ?: "Could not load events"
                    renderErrorRow("No events data")
                    return@runOnUiThread
                }

                try {
                    val json = JSONObject(response.rawBody)
                    val events = json.optJSONArray("events") ?: JSONArray()

                    tvEventsTitle.text = json.optStringOrDefault("title", "Events")
                    tvEventsSubtitle.text = json.optStringOrDefault("subtitle", "Window: -1h to +30d")
                    tvEventsBlockTitle.text = json.optStringOrDefault("blockTitle", "Events in next 30 days")
                    tvEventsHint.text = buildHintText(json, events.length())

                    renderEvents(events)
                } catch (e: Exception) {
                    tvEventsSubtitle.text = "Events JSON error"
                    tvEventsHint.text = e.message ?: "Could not parse events"
                    renderErrorRow("Could not parse events list")
                }
            }
        }.start()
    }

    private fun renderEvents(events: JSONArray) {
        layoutEventsList.removeAllViews()

        if (events.length() == 0) {
            renderErrorRow("No events in selected window")
            return
        }

        for (i in 0 until events.length()) {
            val item = events.optJSONObject(i) ?: continue
            layoutEventsList.addView(createEventRow(item))
        }
    }

    private fun renderErrorRow(message: String) {
        layoutEventsList.removeAllViews()

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = getDrawable(R.color.rr_surface_2)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val tv = TextView(this).apply {
            text = message
            setTextColor(getColor(R.color.rr_text_primary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        }

        row.addView(tv)
        layoutEventsList.addView(row)
    }

    private fun createEventRow(item: JSONObject): LinearLayout {
        val venue = item.optString("venue", "—")
        val eventType = item.optString("eventType", "Event")
        val headline = item.optString("headline", "—")
        val countryInfo = item.optString("countryInfo", "")
        val startTime = item.optString("startTime", "").ifBlank { "—" }
        val endTime = item.optString("endTime", "").ifBlank { "—" }
        val expectedVisitors = item.optInt("expectedVisitors", 0)
        val impactScore = item.optInt("impactScore", 0)
        val zone = item.optString("zone", "").ifBlank { "—" }
        val source = item.optString("source", "")

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = getDrawable(R.color.rr_surface_2)

            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(6)
            }
        }

        val topLine = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val square = View(this).apply {
            setBackgroundColor(getColor(eventSquareColor(impactScore)))
            layoutParams = LinearLayout.LayoutParams(dp(10), dp(10)).apply {
                rightMargin = dp(8)
            }
        }

        val tvVenue = TextView(this).apply {
            text = venue
            setTextColor(getColor(R.color.rr_text_primary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        val tvType = TextView(this).apply {
            text = eventType
            setTextColor(getColor(R.color.rr_text_secondary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        }

        topLine.addView(square)
        topLine.addView(tvVenue)
        topLine.addView(tvType)

        val headlineLine = TextView(this).apply {
            text = buildHeadlineLine(headline, countryInfo)
            setTextColor(getColor(R.color.rr_text_primary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(3)
            }
        }

        val middleLine = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(3)
            }
        }

        val tvStart = TextView(this).apply {
            text = startTime
            setTextColor(getColor(R.color.rr_text_primary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.START
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        val tvEnd = TextView(this).apply {
            text = endTime
            setTextColor(getColor(R.color.rr_text_primary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        val tvZoneImpact = TextView(this).apply {
            text = "$zone  $impactScore"
            setTextColor(getColor(R.color.rr_text_secondary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        middleLine.addView(tvStart)
        middleLine.addView(tvEnd)
        middleLine.addView(tvZoneImpact)

        val bottomLine = TextView(this).apply {
            text = buildBottomLine(expectedVisitors, source)
            setTextColor(getColor(R.color.rr_text_secondary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(3)
            }
        }

        row.addView(topLine)
        row.addView(headlineLine)
        row.addView(middleLine)
        row.addView(bottomLine)

        return row
    }

    private fun buildHeadlineLine(headline: String, countryInfo: String): String {
        return when {
            headline.isNotBlank() && countryInfo.isNotBlank() -> "$headline   |   $countryInfo"
            headline.isNotBlank() -> headline
            countryInfo.isNotBlank() -> countryInfo
            else -> "-"
        }
    }

    private fun buildBottomLine(expectedVisitors: Int, source: String): String {
        val visitorsText = if (expectedVisitors > 0) {
            "Visitors: $expectedVisitors"
        } else {
            "Visitors: unknown"
        }

        return if (source.isNotBlank()) {
            "$visitorsText | Source: $source"
        } else {
            visitorsText
        }
    }

    private fun eventSquareColor(impactScore: Int): Int {
        return when {
            impactScore >= 80 -> R.color.rr_red
            impactScore >= 60 -> R.color.rr_yellow
            else -> R.color.rr_green
        }
    }

    private fun JSONObject.optStringOrDefault(
        key: String,
        defaultValue: String
    ): String {
        val value = optString(key, "")
        return if (value.isBlank()) defaultValue else value
    }

    private fun buildHintText(json: JSONObject, visibleCount: Int): String {
        val windowStart = json.optString("windowStart", "")
        val windowEnd = json.optString("windowEnd", "")
        val sourceMode = json.optString("sourceMode", "")
        val liveFetched = json.optInt("liveFetchedCount", 0)
        val totalCount = json.optInt("count", visibleCount)

        return "Window: $windowStart -> $windowEnd\nVisible: $visibleCount | Count: $totalCount | Live fetched: $liveFetched | Mode: $sourceMode"
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
}
