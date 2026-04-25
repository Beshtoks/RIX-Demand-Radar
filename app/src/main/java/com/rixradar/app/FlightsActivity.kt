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
import java.time.LocalDate
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
            tvFlightsHint.text = "Нажми «Обновить», чтобы загрузить полное расписание"
            renderErrorRow("Сохранённого расписания нет")
            return
        }

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

                val accepted = responseContainsUsableFullList(response.rawBody)
                if (!accepted) {
                    if (!hasCache) {
                        tvFlightsSubtitle.text = "Backend вернул неполный список"
                        tvFlightsHint.text = "Ответ backend получен, но список рейсов пустой или повреждён"
                        renderErrorRow("Полное расписание не получено")
                    } else {
                        tvFlightsHint.text = appendUpdateStatus(
                            tvFlightsHint.text.toString(),
                            "Backend вернул неполный список, оставлены сохранённые данные"
                        )
                    }
                    return@runOnUiThread
                }

                cachePrefs.edit()
                    .putString(KEY_FLIGHTS_JSON, response.rawBody)
                    .putLong(KEY_FLIGHTS_FETCHED_AT, System.currentTimeMillis())
                    .apply()

                renderFlightsFromRawBody(response.rawBody, fromCache = false)
            }
        }.start()
    }

    private fun responseContainsUsableFullList(rawBody: String): Boolean {
        return try {
            val json = JSONObject(rawBody)
            val ok = json.optBoolean("ok", false)
            val flights = json.optJSONArray("flights") ?: JSONArray()
            ok && flights.length() > 0
        } catch (_: Exception) {
            false
        }
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
            tvFlightsHint.text = buildHintText(json, flights, fromCache)

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

        var previousDayKey = ""

        for (i in 0 until flights.length()) {
            val item = flights.optJSONObject(i) ?: continue
            val currentDayKey = dayKey(item)

            if (currentDayKey != previousDayKey) {
                layoutFlightsList.addView(createDayHeader(dayTitle(item)))
                previousDayKey = currentDayKey
            }

            layoutFlightsList.addView(createCompactFlightRow(item))
        }
    }

    private fun createDayHeader(title: String): TextView {
        return TextView(this).apply {
            text = title
            setTextColor(getColor(R.color.rr_text_primary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(12), dp(2), dp(4))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun dayKey(item: JSONObject): String {
        val label = item.optString("dayLabel", "").trim()
        if (label.isNotBlank()) return label.lowercase()

        val flightDate = item.optString("flightDate", "").trim()
        if (flightDate.isNotBlank()) return flightDate

        val baseDay = item.optString("_base_day", "").trim()
        if (baseDay.isNotBlank()) return baseDay

        return "unknown"
    }

    private fun dayTitle(item: JSONObject): String {
        val label = item.optString("dayLabel", "").trim()
        val flightDate = item.optString("flightDate", "").trim()
        val normalized = label.lowercase()

        return when {
            normalized == "šodien" || normalized == "sodien" -> {
                if (flightDate.isBlank()) "Šodien" else "Šodien • $flightDate"
            }
            normalized == "rīt" || normalized == "rit" -> {
                if (flightDate.isBlank()) "Rīt" else "Rīt • $flightDate"
            }
            flightDate.isNotBlank() -> flightDate
            label.isNotBlank() -> label
            else -> "Расписание"
        }
    }

    private fun scrollToWorkingStart(flights: JSONArray) {
        if (flights.length() == 0) return

        val targetFlightIndex = findWorkingStartIndex(flights)
        val targetViewIndex = targetFlightIndex + headerCountBeforeIndex(flights, targetFlightIndex)

        if (targetViewIndex <= 0) {
            scrollFlightsRoot.post {
                scrollFlightsRoot.scrollTo(0, 0)
            }
            return
        }

        scrollFlightsRoot.post {
            val targetView = layoutFlightsList.getChildAt(targetViewIndex)
            if (targetView != null) {
                val topOffset = targetView.top - dp(4)
                scrollFlightsRoot.scrollTo(0, topOffset.coerceAtLeast(0))
            }
        }
    }

    private fun headerCountBeforeIndex(flights: JSONArray, targetIndex: Int): Int {
        var headers = 0
        var previousDayKey = ""

        for (i in 0..targetIndex.coerceAtMost(flights.length() - 1)) {
            val item = flights.optJSONObject(i) ?: continue
            val currentDayKey = dayKey(item)
            if (currentDayKey != previousDayKey) {
                headers++
                previousDayKey = currentDayKey
            }
        }

        return headers
    }

    private fun findWorkingStartIndex(flights: JSONArray): Int {
        val nowMinusOneHour = LocalTime.now().minusHours(1)
        val today = todayIsoDate()
        var firstTomorrowIndex = -1

        for (i in 0 until flights.length()) {
            val item = flights.optJSONObject(i) ?: continue
            val dayLabel = item.optString("dayLabel", "").trim().lowercase()
            val flightDate = item.optString("flightDate", "").trim()
            val baseDay = item.optString("_base_day", "").trim()
            val dayIsTomorrow = dayLabel == "rīt" || dayLabel == "rit" ||
                (flightDate.isNotBlank() && flightDate != today) ||
                (baseDay.isNotBlank() && baseDay != today)

            if (dayIsTomorrow) {
                if (firstTomorrowIndex < 0) {
                    firstTomorrowIndex = i
                }
                continue
            }

            val scheduled = item.optString("scheduledTime", "")
            val actual = item.optString("actualTime", "")
            val referenceTimeText = scheduled.ifBlank { actual }

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
        return LocalDate.now().toString()
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

    private fun buildHintText(json: JSONObject, flights: JSONArray, fromCache: Boolean): String {
        val sourceUrl = json.optString("sourceUrl", "")
        val windowStart = json.optString("windowStart", "")
        val windowEnd = json.optString("windowEnd", "")
        val scheduleMode = json.optString("scheduleMode", "Sodien+Rit")
        val cacheStatus = if (fromCache) "сохранённые данные" else "обновлено с backend"
        val counts = countByDay(flights)

        return "Сегодня: ${counts.today} | Завтра: ${counts.tomorrow} | Всего: ${flights.length()}\n" +
            "Источник: $sourceUrl\n" +
            "Окно: $windowStart → $windowEnd\n" +
            "Расписание: $scheduleMode\n" +
            "Режим: $cacheStatus"
    }

    private fun countByDay(flights: JSONArray): DayCounts {
        var todayCount = 0
        var tomorrowCount = 0
        val today = todayIsoDate()

        for (i in 0 until flights.length()) {
            val item = flights.optJSONObject(i) ?: continue
            val label = item.optString("dayLabel", "").trim().lowercase()
            val flightDate = item.optString("flightDate", "").trim()
            val baseDay = item.optString("_base_day", "").trim()

            when {
                label == "rīt" || label == "rit" -> tomorrowCount++
                label == "šodien" || label == "sodien" -> todayCount++
                flightDate.isNotBlank() && flightDate != today -> tomorrowCount++
                baseDay.isNotBlank() && baseDay != today -> tomorrowCount++
                else -> todayCount++
            }
        }

        return DayCounts(today = todayCount, tomorrow = tomorrowCount)
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

    data class DayCounts(
        val today: Int,
        val tomorrow: Int
    )

    companion object {
        private const val FLIGHTS_CACHE_PREFS = "rix_flights_cache_full_v1"
        private const val KEY_FLIGHTS_JSON = "flights_json"
        private const val KEY_FLIGHTS_FETCHED_AT = "flights_fetched_at"
        private const val FLIGHTS_AUTO_REFRESH_MS = 60L * 60L * 1000L
        private const val FLIGHTS_PERIODIC_WORK_NAME = "rix_flights_hourly_refresh"
    }
}
