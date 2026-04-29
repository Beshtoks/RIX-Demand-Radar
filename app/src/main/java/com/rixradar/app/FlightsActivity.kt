package com.rixradar.app

import android.graphics.Color
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
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
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
    private val rigaZone: ZoneId = ZoneId.of("Europe/Riga")

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
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequest.Builder(
            FlightsRefreshWorker::class.java,
            FLIGHTS_BACKGROUND_REFRESH_MINUTES,
            TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

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
            val endpoint = if (force) "/api/refresh-flights" else "/api/real-flights"
            val response = serverClient.fetch(endpoint)

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

                val freshnessError = validateFreshFlightsResponse(response.rawBody)
                if (freshnessError != null) {
                    if (!hasCache) {
                        tvFlightsSubtitle.text = "Нет свежего расписания"
                        tvFlightsHint.text = freshnessError
                        renderErrorRow("Не удалось загрузить свежие рейсы")
                    } else {
                        tvFlightsHint.text = appendUpdateStatus(
                            tvFlightsHint.text.toString(),
                            freshnessError
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

    private fun validateFreshFlightsResponse(rawBody: String): String? {
        return try {
            val json = JSONObject(rawBody)
            val ok = json.optBoolean("ok", false)
            val flights = json.optJSONArray("flights")
            val cacheAgeSeconds = json.optLong("cacheAgeSeconds", 0L)
            val cacheMode = json.optString("cacheMode", "")

            when {
                !ok -> json.optString("error", "Backend вернул ошибку")
                flights == null || flights.length() == 0 -> "Backend вернул пустой список рейсов"
                cacheMode.startsWith("stale_after") -> "Backend не смог обновить RIX и вернул старый кэш"
                cacheAgeSeconds >= FLIGHTS_AUTO_REFRESH_MS / 1000L -> "Серверный список старше 60 минут"
                else -> null
            }
        } catch (e: Exception) {
            e.message ?: "Ошибка проверки свежести ответа"
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
        return LocalDate.now(rigaZone).toString()
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
            setTextColor(routeTextColor(item))
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

    private fun routeTextColor(item: JSONObject): Int {
        return when (flightImportanceLevel(item)) {
            5 -> Color.rgb(255, 239, 218)
            4 -> Color.rgb(255, 248, 218)
            3 -> Color.rgb(238, 255, 238)
            2 -> Color.rgb(238, 247, 255)
            1 -> Color.rgb(238, 238, 238)
            else -> getColor(R.color.rr_text_primary)
        }
    }

    private fun flightImportanceLevel(item: JSONObject): Int {
        val route = normalizeImportanceText(item.optString("destination", ""))
        val flightNumber = normalizeImportanceText(item.optString("flightNumber", ""))
        val status = normalizeImportanceText(item.optString("status", ""))

        var score = 0

        if (containsAny(route, PREMIUM_LONG_DISTANCE_ROUTES)) score += 5
        if (containsAny(route, STRONG_BUSINESS_ROUTES)) score += 4
        if (containsAny(route, STRONG_NORDIC_ROUTES)) score += 3
        if (containsAny(route, STRONG_LEISURE_ROUTES)) score += 3
        if (containsAny(route, REGIONAL_ROUTES)) score += 2
        if (containsAny(route, LOW_PRIORITY_ROUTES)) score -= 1

        if (flightNumber.startsWith("BT")) score += 1
        if (flightNumber.startsWith("LH") || flightNumber.startsWith("KL") ||
            flightNumber.startsWith("AF") || flightNumber.startsWith("TK") ||
            flightNumber.startsWith("EK") || flightNumber.startsWith("AY") ||
            flightNumber.startsWith("LO") || flightNumber.startsWith("SK")
        ) {
            score += 1
        }

        if (status.contains("LANDED") || status.contains("ARRIVED")) score += 1
        if (status.contains("DELAYED")) score += 1
        if (status.contains("CANCEL")) score -= 5

        return when {
            score >= 6 -> 5
            score == 5 -> 4
            score in 3..4 -> 3
            score == 2 -> 2
            score <= 0 -> 0
            else -> 1
        }
    }

    private fun normalizeImportanceText(value: String): String {
        return value
            .uppercase()
            .replace('Ā', 'A')
            .replace('Č', 'C')
            .replace('Ē', 'E')
            .replace('Ģ', 'G')
            .replace('Ī', 'I')
            .replace('Ķ', 'K')
            .replace('Ļ', 'L')
            .replace('Ņ', 'N')
            .replace('Š', 'S')
            .replace('Ū', 'U')
            .replace('Ž', 'Z')
    }

    private fun containsAny(text: String, keywords: Set<String>): Boolean {
        return keywords.any { keyword -> text.contains(keyword) }
    }

    private fun statusSquareColor(item: JSONObject): Int {
        if (isActualArrivalOlderThanOneHour(item)) {
            return R.color.rr_text_primary
        }

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

    private fun isActualArrivalOlderThanOneHour(item: JSONObject): Boolean {
        val baseDayText = item.optString("_base_day", "").ifBlank {
            item.optString("flightDate", "")
        }
        val actualText = item.optString("actualTime", "")

        if (baseDayText.length < 10 || actualText.isBlank()) {
            return false
        }

        return try {
            val flightDate = LocalDate.parse(baseDayText.take(10))
            val actualTime = LocalTime.parse(actualText)
            val arrivalDateTime = LocalDateTime.of(flightDate, actualTime)
            val now = LocalDateTime.now(rigaZone)

            if (!arrivalDateTime.isBefore(now)) {
                return false
            }

            Duration.between(arrivalDateTime, now).toMinutes() > 60
        } catch (_: Exception) {
            false
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
        private const val FLIGHTS_BACKGROUND_REFRESH_MINUTES = 45L

        private val PREMIUM_LONG_DISTANCE_ROUTES = setOf(
            "DUBAI", "ABU DHABI", "DOHA", "ISTANBUL", "TEL AVIV", "TASHKENT", "YEREVAN", "BAKU"
        )
        private val STRONG_BUSINESS_ROUTES = setOf(
            "LONDON", "FRANKFURT", "MUNICH", "PARIS", "AMSTERDAM", "ZURICH", "VIENNA", "BRUSSELS",
            "BERLIN", "HAMBURG", "DUSSELDORF", "WARSAW", "PRAGUE"
        )
        private val STRONG_NORDIC_ROUTES = setOf(
            "STOCKHOLM", "OSLO", "COPENHAGEN", "HELSINKI", "GOTHENBURG", "GOTEBORG", "TALLINN", "VILNIUS"
        )
        private val STRONG_LEISURE_ROUTES = setOf(
            "BARCELONA", "MADRID", "MALAGA", "ALICANTE", "PALMA", "TENERIFE", "LISBON", "ROME", "MILAN",
            "ATHENS", "LARNACA", "HERAKLION", "NICE"
        )
        private val REGIONAL_ROUTES = setOf(
            "KAUNAS", "PALANGA", "TARTU", "TAMPERE", "GDANSK", "KRAKOW", "BUDAPEST"
        )
        private val LOW_PRIORITY_ROUTES = setOf(
            "CHARTER", "CARGO", "TRAINING", "POSITIONING"
        )
    }
}
