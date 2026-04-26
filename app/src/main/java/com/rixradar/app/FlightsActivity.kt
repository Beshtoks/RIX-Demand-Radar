package com.rixradar.app

import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.LocalTime
import java.util.concurrent.TimeUnit

class FlightsActivity : AppCompatActivity() {

    private lateinit var scrollFlightsRoot: ScrollView
    private lateinit var tvFlightsTitle: TextView
    private lateinit var tvFlightsSubtitle: TextView
    private lateinit var tvFlightsBlockTitle: TextView
    private lateinit var btnFlightsRefresh: Button
    private lateinit var layoutFlightsList: LinearLayout
    private lateinit var tvFlightsHint: TextView

    private val serverClient = ServerClient()

    private var isLoadingFlights = false

    private val cachePrefs by lazy {
        getSharedPreferences(FLIGHTS_CACHE_PREFS, MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_flights)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Рейсы"

        bindViews()
        bindRefreshButton()
        scheduleHourlyFlightsRefresh()
        renderCachedFlightsOrEmpty()
        refreshIfCacheExpired()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun bindViews() {
        scrollFlightsRoot = findViewById(R.id.scrollFlightsRoot)
        tvFlightsTitle = findViewById(R.id.tvFlightsTitle)
        tvFlightsSubtitle = findViewById(R.id.tvFlightsSubtitle)
        tvFlightsBlockTitle = findViewById(R.id.tvFlightsBlockTitle)
        btnFlightsRefresh = findViewById(R.id.btnFlightsRefresh)
        layoutFlightsList = findViewById(R.id.layoutFlightsList)
        tvFlightsHint = findViewById(R.id.tvFlightsHint)
    }

    private fun bindRefreshButton() {
        btnFlightsRefresh.setOnClickListener {
            loadFlights(force = true, showLoadingText = true)
        }
    }

    private fun scheduleHourlyFlightsRefresh() {
        val request = PeriodicWorkRequest.Builder(
            FlightsRefreshWorker::class.java,
            1,
            TimeUnit.HOURS
        ).build()

        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            FLIGHTS_PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    private fun renderCachedFlightsOrEmpty() {
        val cachedBody = cachePrefs.getString(KEY_FLIGHTS_JSON, null)

        if (cachedBody.isNullOrBlank()) {
            tvFlightsTitle.text = "Прилёты RIX"
            tvFlightsSubtitle.text = "Сохранённого расписания пока нет"
            tvFlightsBlockTitle.text = "Окно: Šodien + Rīt"
            tvFlightsHint.text = "Нажми «Обновить», чтобы загрузить расписание"
            renderErrorRow("Сохранённого расписания нет")
            return
        }

        ServerRadarRepository.cacheFlightsRawBody(cachedBody)
        renderFlightsFromRawBody(cachedBody, fromCache = true)
    }

    private fun refreshIfCacheExpired() {
        val lastFetchAt = cachePrefs.getLong(KEY_FLIGHTS_FETCHED_AT, 0L)
        val hasCache = !cachePrefs.getString(KEY_FLIGHTS_JSON, null).isNullOrBlank()
        val expired = System.currentTimeMillis() - lastFetchAt >= FLIGHTS_AUTO_REFRESH_MS

        if (!hasCache || expired) {
            loadFlights(force = false, showLoadingText = !hasCache)
        }
    }

    private fun loadFlights(force: Boolean, showLoadingText: Boolean) {
        if (isLoadingFlights) return

        val lastFetchAt = cachePrefs.getLong(KEY_FLIGHTS_FETCHED_AT, 0L)
        val hasCache = !cachePrefs.getString(KEY_FLIGHTS_JSON, null).isNullOrBlank()
        val freshEnough = System.currentTimeMillis() - lastFetchAt < FLIGHTS_AUTO_REFRESH_MS

        if (!force && hasCache && freshEnough) {
            return
        }

        isLoadingFlights = true
        btnFlightsRefresh.isEnabled = false

        if (showLoadingText) {
            tvFlightsTitle.text = "Прилёты RIX"
            tvFlightsSubtitle.text = "Обновление..."
            tvFlightsBlockTitle.text = "Окно: Šodien + Rīt"
            tvFlightsHint.text = "Подключение к backend..."
        } else {
            tvFlightsHint.text = appendUpdateStatus(tvFlightsHint.text.toString(), "Фоновое обновление расписания...")
        }

        Thread {
            val response = serverClient.fetch("/api/real-flights")

            runOnUiThread {
                isLoadingFlights = false
                btnFlightsRefresh.isEnabled = true

                if (!response.success || response.rawBody.isNullOrBlank()) {
                    if (!hasCache) {
                        tvFlightsSubtitle.text = "Сервер временно недоступен"
                        tvFlightsHint.text = response.errorMessage ?: "Не удалось получить данные"
                        renderErrorRow("Не удалось загрузить рейсы")
                    } else {
                        tvFlightsHint.text = appendUpdateStatus(
                            tvFlightsHint.text.toString(),
                            "Обновление не удалось: ${response.errorMessage ?: "ошибка сети"}"
                        )
                    }
                    return@runOnUiThread
                }

                cachePrefs.edit()
                    .putString(KEY_FLIGHTS_JSON, response.rawBody)
                    .putLong(KEY_FLIGHTS_FETCHED_AT, System.currentTimeMillis())
                    .apply()

                ServerRadarRepository.cacheFlightsRawBody(response.rawBody)
                renderFlightsFromRawBody(response.rawBody, fromCache = false)
            }
        }.start()
    }

    private fun renderFlightsFromRawBody(rawBody: String, fromCache: Boolean) {
        try {
            val json = JSONObject(rawBody)
            val flights = json.optJSONArray("flights") ?: JSONArray()

            tvFlightsTitle.text = "Прилёты RIX"
            tvFlightsSubtitle.text = if (fromCache) {
                "Сохранённое расписание"
            } else {
                "Расписание обновлено"
            }
            tvFlightsBlockTitle.text = "Окно: Šodien + Rīt"
            tvFlightsHint.text = buildHintText(json, flights.length(), fromCache)

            renderFlights(flights)
            scrollToWorkingStart(flights)
        } catch (e: Exception) {
            tvFlightsSubtitle.text = "Ошибка чтения JSON"
            tvFlightsHint.text = e.message ?: "Не удалось разобрать ответ"
            renderErrorRow("Ошибка разбора списка рейсов")
        }
    }

    private fun renderFlights(flights: JSONArray) {
        layoutFlightsList.removeAllViews()

        if (flights.length() == 0) {
            renderErrorRow("Список рейсов пуст")
            return
        }

        for (i in 0 until flights.length()) {
            val item = flights.optJSONObject(i) ?: continue
            layoutFlightsList.addView(createCompactFlightRow(item))
        }
    }

    private fun scrollToWorkingStart(flights: JSONArray) {
        if (flights.length() == 0) return

        val targetIndex = findWorkingStartIndex(flights)
        if (targetIndex <= 0) {
            scrollFlightsRoot.post {
                scrollFlightsRoot.scrollTo(0, 0)
            }
            return
        }

        scrollFlightsRoot.post {
            val targetView = layoutFlightsList.getChildAt(targetIndex)
            if (targetView != null) {
                val topOffset = targetView.top - dp(4)
                scrollFlightsRoot.scrollTo(0, topOffset.coerceAtLeast(0))
            }
        }
    }

    private fun findWorkingStartIndex(flights: JSONArray): Int {
        val nowMinusOneHour = LocalTime.now().minusHours(1)
        var firstTomorrowIndex = -1

        for (i in 0 until flights.length()) {
            val item = flights.optJSONObject(i) ?: continue
            val baseDay = item.optString("_base_day", "")
            val scheduled = item.optString("scheduledTime", "")
            val actual = item.optString("actualTime", "")
            val referenceTimeText = scheduled.ifBlank { actual }

            if (baseDay.isNotBlank() && baseDay != todayIsoDate()) {
                if (firstTomorrowIndex < 0) {
                    firstTomorrowIndex = i
                }
                continue
            }

            if (referenceTimeText.isBlank()) {
                continue
            }

            try {
                val referenceTime = LocalTime.parse(referenceTimeText)
                if (!referenceTime.isBefore(nowMinusOneHour)) {
                    return i
                }
            } catch (_: Exception) {
                continue
            }
        }

        return if (firstTomorrowIndex >= 0) firstTomorrowIndex else 0
    }

    private fun todayIsoDate(): String {
        val calendar = java.util.Calendar.getInstance()
        val year = calendar.get(java.util.Calendar.YEAR)
        val month = calendar.get(java.util.Calendar.MONTH) + 1
        val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)
        return "%04d-%02d-%02d".format(year, month, day)
    }

    private fun renderErrorRow(message: String) {
        layoutFlightsList.removeAllViews()

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
        layoutFlightsList.addView(row)
    }

    private fun createCompactFlightRow(item: JSONObject): LinearLayout {
        val route = item.optString("destination", "—")
        val flightNumber = item.optString("flightNumber", "—")
        val scheduledTime = item.optString("scheduledTime", "").ifBlank { "—" }
        val actualTime = item.optString("actualTime", "").ifBlank { "—" }
        val terminal = item.optString("terminal", "").ifBlank { "—" }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = getDrawable(R.color.rr_surface_2)

            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = dp(6)
            layoutParams = params
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
            setBackgroundColor(getColor(statusSquareColor(item)))
            layoutParams = LinearLayout.LayoutParams(dp(10), dp(10)).apply {
                rightMargin = dp(8)
            }
        }

        val tvRoute = TextView(this).apply {
            text = route
            setTextColor(getColor(R.color.rr_text_primary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        val tvFlight = TextView(this).apply {
            text = flightNumber
            setTextColor(getColor(R.color.rr_text_secondary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        }

        topLine.addView(square)
        topLine.addView(tvRoute)
        topLine.addView(tvFlight)

        val bottomLine = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(3)
            }
        }

        val tvPlanned = TextView(this).apply {
            text = scheduledTime
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

        val tvActual = TextView(this).apply {
            text = actualTime
            setTextColor(getColor(R.color.rr_text_primary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        val tvTerminal = TextView(this).apply {
            text = terminal
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

        bottomLine.addView(tvPlanned)
        bottomLine.addView(tvActual)
        bottomLine.addView(tvTerminal)

        row.addView(topLine)
        row.addView(bottomLine)

        return row
    }

    private fun statusSquareColor(item: JSONObject): Int {
        val scheduled = item.optString("scheduledTime", "")
        val actual = item.optString("actualTime", "")
        val status = item.optString("status", "").uppercase()

        if (scheduled.isBlank() || actual.isBlank()) {
            return when (status) {
                "DELAYED" -> R.color.rr_yellow
                "LANDED", "ARRIVED", "SCHEDULED", "ON TIME" -> R.color.rr_green
                else -> R.color.rr_text_muted
            }
        }

        return try {
            val scheduledTime = LocalTime.parse(scheduled)
            val actualTime = LocalTime.parse(actual)
            val diffMinutes = Duration.between(scheduledTime, actualTime).toMinutes()
            val absMinutes = kotlin.math.abs(diffMinutes)

            when {
                absMinutes <= 15 -> R.color.rr_green
                absMinutes in 16..59 -> R.color.rr_yellow
                absMinutes >= 60 -> R.color.rr_red
                else -> R.color.rr_green
            }
        } catch (_: Exception) {
            R.color.rr_text_muted
        }
    }

    private fun buildHintText(json: JSONObject, count: Int, fromCache: Boolean): String {
        val sourceUrl = json.optString("sourceUrl", "")
        val windowStart = json.optString("windowStart", "")
        val windowEnd = json.optString("windowEnd", "")
        val scheduleMode = json.optString("scheduleMode", "Sodien+Rit")
        val cacheStatus = if (fromCache) "сохранённые данные" else "обновлено с backend"
        return "Источник: $sourceUrl\nОкно: $windowStart → $windowEnd\nРейсов: $count\nРасписание: $scheduleMode\nРежим: $cacheStatus"
    }

    private fun appendUpdateStatus(oldText: String, status: String): String {
        val base = oldText.substringBefore("\nСтатус:")
        return "$base\nСтатус: $status"
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    companion object {
        private const val FLIGHTS_CACHE_PREFS = "rix_flights_cache"
        private const val KEY_FLIGHTS_JSON = "flights_json"
        private const val KEY_FLIGHTS_FETCHED_AT = "flights_fetched_at"
        private const val FLIGHTS_AUTO_REFRESH_MS = 60L * 60L * 1000L
        private const val FLIGHTS_PERIODIC_WORK_NAME = "rix_flights_hourly_refresh"
    }
}
