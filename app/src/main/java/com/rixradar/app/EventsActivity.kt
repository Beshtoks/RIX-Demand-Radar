package com.rixradar.app

import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
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
    private lateinit var rootScrollView: ScrollView

    private val serverClient = ServerClient()

    private var touchStartY = 0f
    private var refreshInProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_events)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "События"

        bindViews()
        bindPullToRefresh()
        renderCachedEvents()
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

        rootScrollView = findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ScrollView
    }

    private fun bindPullToRefresh() {
        rootScrollView.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchStartY = event.rawY
                }

                MotionEvent.ACTION_UP -> {
                    val dy = event.rawY - touchStartY
                    val isAtTop = rootScrollView.scrollY == 0
                    val isPullDown = dy > dp(70)

                    if (isAtTop && isPullDown && !refreshInProgress) {
                        refreshEventsFromServer()
                    }
                }
            }

            view.performClick()
            false
        }
    }

    private fun renderCachedEvents() {
        val cachedJson = getCachedEventsJson()

        if (!cachedJson.isNullOrBlank() && isOutdatedEventsWindowCache(cachedJson)) {
            tvEventsTitle.text = "Events"
            tvEventsSubtitle.text = "Updating events..."
            tvEventsBlockTitle.text = "Events in next 30 days"
            tvEventsHint.text = "Старый кэш мероприятий был рассчитан только на 24 часа. Загружаю диапазон на 30 дней."
            renderErrorRow("Обновляю сохранённое расписание")
            refreshEventsFromServer()
            return
        }

        if (cachedJson.isNullOrBlank()) {
            tvEventsTitle.text = "Events"
            tvEventsSubtitle.text = "Saved events not loaded yet"
            tvEventsBlockTitle.text = "Events in next 30 days"
            tvEventsHint.text = "Потяни список вниз, чтобы загрузить мероприятия с сервера. После успешной загрузки они будут сохранены."
            renderErrorRow("Нет сохранённого расписания")
            return
        }

        renderRawJson(cachedJson, fromCache = true)
    }

    private fun refreshEventsFromServer() {
        refreshInProgress = true
        tvEventsSubtitle.text = "Updating events..."
        tvEventsHint.text = "Загружаю свежие мероприятия с сервера..."

        Thread {
            val response = serverClient.fetch(ServerConfig.EVENTS_PATH)

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread

                refreshInProgress = false

                if (!response.success || response.rawBody.isNullOrBlank()) {
                    tvEventsSubtitle.text = "Events server temporarily unavailable"
                    tvEventsHint.text = response.errorMessage ?: "Could not load events"
                    return@runOnUiThread
                }

                try {
                    JSONObject(response.rawBody)
                    saveCachedEventsJson(response.rawBody)
                    renderRawJson(response.rawBody, fromCache = false)
                } catch (e: Exception) {
                    tvEventsSubtitle.text = "Events JSON error"
                    tvEventsHint.text = e.message ?: "Could not parse events"
                }
            }
        }.start()
    }

    private fun isOutdatedEventsWindowCache(rawJson: String): Boolean {
        return try {
            val json = JSONObject(rawJson)
            val subtitle = json.optString("subtitle", "")
            val sourceMode = json.optString("sourceMode", "")
            val windowStart = json.optString("windowStart", "")
            val windowEnd = json.optString("windowEnd", "")

            subtitle.contains("+24h", ignoreCase = true) ||
                sourceMode != "live_events_30d" ||
                (windowStart.isNotBlank() && windowEnd.isNotBlank() && subtitle.contains("+24", ignoreCase = true))
        } catch (e: Exception) {
            false
        }
    }

    private fun renderRawJson(rawJson: String, fromCache: Boolean) {
        try {
            val json = JSONObject(rawJson)
            val events = json.optJSONArray("events") ?: JSONArray()
            val visibleEvents = filterVisibleEvents(events)

            tvEventsTitle.text = json.optStringOrDefault("title", "Events")
            tvEventsSubtitle.text = json.optStringOrDefault("subtitle", "Window: -1h to +30d")
            tvEventsBlockTitle.text = json.optStringOrDefault("blockTitle", "Events in next 30 days")
            tvEventsHint.text = buildHintText(json, visibleEvents.length(), fromCache)

            renderEvents(visibleEvents)
        } catch (e: Exception) {
            tvEventsTitle.text = "Events"
            tvEventsSubtitle.text = "Saved events JSON error"
            tvEventsBlockTitle.text = "Events in next 30 days"
            tvEventsHint.text = e.message ?: "Could not parse saved events"
            renderErrorRow("Ошибка сохранённого расписания")
        }
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

    private fun filterVisibleEvents(events: JSONArray): JSONArray {
        val result = JSONArray()

        for (i in 0 until events.length()) {
            val item = events.optJSONObject(i) ?: continue
            if (shouldShowEvent(item)) {
                result.put(item)
            }
        }

        return result
    }

    private fun shouldShowEvent(item: JSONObject): Boolean {
        val expectedVisitors = item.optInt("expectedVisitors", 0)
        if (expectedVisitors < MIN_EVENT_VISITORS) {
            return false
        }

        val venue = item.optString("venue", "").lowercase()
        val headline = item.optString("headline", "").lowercase()
        val eventType = item.optString("eventType", "").lowercase()
        val source = item.optString("source", "").lowercase()
        val combined = "$venue $headline $eventType $source"

        val blockedTerms = listOf(
            "mihaila čehova",
            "mihaila cehova",
            "čehova rīgas krievu teātris",
            "cehova rigas krievu teatris",
            "rīgas krievu teātris",
            "rigas krievu teatris",
            "restorāns",
            "restorans",
            "restaurant"
        )

        return blockedTerms.none { blocked -> combined.contains(blocked) }
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
        val dateShort = item.optString("dateShort", item.optString("eventDate", ""))
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

        val tvDateStart = TextView(this).apply {
            text = if (dateShort.isNotBlank()) "$dateShort  $startTime" else startTime
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

        middleLine.addView(tvDateStart)
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

    private fun buildHintText(json: JSONObject, visibleCount: Int, fromCache: Boolean): String {
        val windowStart = json.optString("windowStart", "")
        val windowEnd = json.optString("windowEnd", "")
        val sourceMode = json.optString("sourceMode", "")
        val totalCount = json.optInt("count", visibleCount)
        val cacheText = if (fromCache) "Saved" else "Fresh"

        return "$cacheText | Window: $windowStart -> $windowEnd\nVisible: $visibleCount | Count: $totalCount | Mode: $sourceMode"
    }

    private fun getCachedEventsJson(): String? {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_EVENTS_JSON, null)
    }

    private fun saveCachedEventsJson(rawJson: String) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(KEY_EVENTS_JSON, rawJson)
            .apply()
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    companion object {
        private const val PREFS_NAME = "rix_events_cache"
        private const val KEY_EVENTS_JSON = "events_json"
        private const val MIN_EVENT_VISITORS = 5000
    }
}
