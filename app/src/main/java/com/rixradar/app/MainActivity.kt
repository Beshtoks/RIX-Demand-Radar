package com.rixradar.app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class MainActivity : AppCompatActivity() {

    private lateinit var tvCity: TextView
    private lateinit var tvUpdated: TextView
    private lateinit var tvDataMode: TextView
    private lateinit var arrivalRadarView: ArrivalRadarView

    private lateinit var btnFlights: Button
    private lateinit var btnEvents: Button
    private lateinit var btnForecast: Button
    private lateinit var btnAi: Button

    private val radarDataSource: RadarDataSource = RadarRepositoryProvider.dataSource
    private val startupFallbackDataSource: RadarDataSource = FakeServerRadarRepository()
    private val serverClient = ServerClient()

    private val flightsCachePrefs by lazy {
        getSharedPreferences(FLIGHTS_CACHE_PREFS, MODE_PRIVATE)
    }

    private var firstResumeHandled = false
    private var dashboardLoading = false
    private var radarLoading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        bindButtons()

        render(startupFallbackDataSource.getDashboardState())
        renderDataMode("Режим данных: SERVER / загрузка...")
        renderRadarFromCache()
        loadDashboardFromServer()
        loadRadarFlightsIfNeeded()
    }

    override fun onResume() {
        super.onResume()

        if (!firstResumeHandled) {
            firstResumeHandled = true
            return
        }

        loadDashboardFromServer()
        loadRadarFlightsIfNeeded()
    }

    private fun bindViews() {
        tvCity = findViewById(R.id.tvCity)
        tvUpdated = findViewById(R.id.tvUpdated)
        tvDataMode = findViewById(R.id.tvDataMode)
        arrivalRadarView = findViewById(R.id.arrivalRadarView)

        btnFlights = findViewById(R.id.btnFlights)
        btnEvents = findViewById(R.id.btnEvents)
        btnForecast = findViewById(R.id.btnForecast)
        btnAi = findViewById(R.id.btnAi)
    }

    private fun bindButtons() {
        btnFlights.setOnClickListener { startActivity(Intent(this, FlightsActivity::class.java)) }
        btnEvents.setOnClickListener { startActivity(Intent(this, EventsActivity::class.java)) }
        btnForecast.setOnClickListener { startActivity(Intent(this, ForecastActivity::class.java)) }
        btnAi.setOnClickListener { startActivity(Intent(this, AiActivity::class.java)) }
    }

    private fun loadDashboardFromServer() {
        if (dashboardLoading) return
        dashboardLoading = true

        Thread {
            val state = try {
                radarDataSource.getDashboardState()
            } catch (_: Exception) {
                startupFallbackDataSource.getDashboardState().copy(
                    updatedText = "Обновлено: server dashboard error"
                )
            }

            runOnUiThread {
                dashboardLoading = false
                render(state)
                renderDataMode()
            }
        }.start()
    }

    private fun render(state: DashboardUiState) {
        tvCity.text = state.cityText
        tvUpdated.text = state.updatedText
    }

    private fun renderDataMode(customText: String? = null) {
        tvDataMode.text = customText ?: "Режим данных: ${RadarRepositoryProvider.currentMode.name}"
    }

    private fun renderRadarFromCache() {
        val cachedBody = flightsCachePrefs.getString(KEY_FLIGHTS_JSON, null)
        if (cachedBody.isNullOrBlank()) {
            arrivalRadarView.clearWithPlaceholder()
            return
        }

        val points = parseArrivalPoints(cachedBody)
        if (points.isEmpty()) {
            arrivalRadarView.clearWithPlaceholder()
            return
        }

        ServerRadarRepository.cacheFlightsRawBody(cachedBody)
        arrivalRadarView.setArrivals(points)
    }

    private fun loadRadarFlightsIfNeeded() {
        if (radarLoading) return

        val lastFetchAt = flightsCachePrefs.getLong(KEY_FLIGHTS_FETCHED_AT, 0L)
        val cachedBody = flightsCachePrefs.getString(KEY_FLIGHTS_JSON, null)
        val freshEnough = !cachedBody.isNullOrBlank() && System.currentTimeMillis() - lastFetchAt < FLIGHTS_AUTO_REFRESH_MS

        if (freshEnough) {
            renderRadarFromCache()
            return
        }

        radarLoading = true
        Thread {
            val response = serverClient.fetch("/api/real-flights")

            runOnUiThread {
                radarLoading = false

                if (!response.success || response.rawBody.isNullOrBlank()) {
                    renderRadarFromCache()
                    return@runOnUiThread
                }

                flightsCachePrefs.edit()
                    .putString(KEY_FLIGHTS_JSON, response.rawBody)
                    .putLong(KEY_FLIGHTS_FETCHED_AT, System.currentTimeMillis())
                    .apply()

                ServerRadarRepository.cacheFlightsRawBody(response.rawBody)
                val points = parseArrivalPoints(response.rawBody)
                if (points.isEmpty()) {
                    renderRadarFromCache()
                } else {
                    arrivalRadarView.setArrivals(points)
                }
            }
        }.start()
    }

    private fun parseArrivalPoints(rawBody: String): List<ArrivalRadarView.ArrivalPoint> {
        return try {
            val json = JSONObject(rawBody)
            val result = mutableListOf<ArrivalRadarView.ArrivalPoint>()

            val directArray = json.optJSONArray("flights")
            if (directArray != null && directArray.length() > 0) {
                result.addAll(parseFlightsArray(directArray, defaultDayOffset = 0))
                return normalizeArrivalOrder(result)
            }

            val todayArray = json.optJSONObject("today")?.optJSONArray("flights")
            if (todayArray != null && todayArray.length() > 0) {
                result.addAll(parseFlightsArray(todayArray, defaultDayOffset = 0))
            }

            val tomorrowArray = json.optJSONObject("tomorrow")?.optJSONArray("flights")
            if (tomorrowArray != null && tomorrowArray.length() > 0) {
                result.addAll(parseFlightsArray(tomorrowArray, defaultDayOffset = 1440))
            }

            normalizeArrivalOrder(result)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseFlightsArray(array: JSONArray, defaultDayOffset: Int): List<ArrivalRadarView.ArrivalPoint> {
        val result = mutableListOf<ArrivalRadarView.ArrivalPoint>()

        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val absoluteMinutes = correctedFlightAbsoluteMinutes(item, defaultDayOffset) ?: continue
            result.add(ArrivalRadarView.ArrivalPoint(absoluteMinutes))
        }

        return result
    }

    private fun correctedFlightAbsoluteMinutes(item: JSONObject, defaultDayOffset: Int): Int? {
        val referenceTimeText = item.optString("actualTime", "").ifBlank {
            item.optString("scheduledTime", "")
        }
        val referenceMinutes = parseTimeToMinutes(referenceTimeText) ?: return null
        val baseDay = item.optString("_base_day", "").ifBlank {
            item.optString("flightDate", "")
        }

        if (baseDay.isBlank()) {
            return referenceMinutes + defaultDayOffset
        }

        return try {
            var referenceDate = LocalDate.parse(baseDay.take(10))
            val scheduled = item.optString("scheduledTime", "")
            val actual = item.optString("actualTime", "")

            if (scheduled.isNotBlank() && actual.isNotBlank()) {
                val scheduledTime = LocalTime.parse(scheduled)
                val actualTime = LocalTime.parse(actual)
                val rawDiffMinutes = Duration.between(scheduledTime, actualTime).toMinutes()

                if (rawDiffMinutes > 12L * 60L) {
                    referenceDate = referenceDate.minusDays(1)
                } else if (rawDiffMinutes < -12L * 60L) {
                    referenceDate = referenceDate.plusDays(1)
                }
            }

            val today = LocalDate.now(RIGA_ZONE)
            val dayOffset = ChronoUnit.DAYS.between(today, referenceDate).toInt() * 1440
            referenceMinutes + dayOffset
        } catch (_: Exception) {
            referenceMinutes + defaultDayOffset
        }
    }

    private fun normalizeArrivalOrder(points: List<ArrivalRadarView.ArrivalPoint>): List<ArrivalRadarView.ArrivalPoint> {
        if (points.isEmpty()) return emptyList()
        if (points.any { it.absoluteMinute < 0 || it.absoluteMinute >= 1440 }) {
            return points.sortedBy { it.absoluteMinute }
        }

        val normalized = mutableListOf<ArrivalRadarView.ArrivalPoint>()
        var dayOffset = 0
        var previous = points.first().absoluteMinute

        for ((index, point) in points.sortedBy { it.absoluteMinute }.withIndex()) {
            if (index > 0 && point.absoluteMinute + dayOffset < previous - 720) {
                dayOffset += 1440
            }

            val absolute = point.absoluteMinute + dayOffset
            normalized.add(ArrivalRadarView.ArrivalPoint(absolute))
            previous = absolute
        }

        return normalized.sortedBy { it.absoluteMinute }
    }

    private fun parseTimeToMinutes(value: String): Int? {
        val match = TIME_PATTERN.find(value) ?: return null
        val parts = match.value.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: return null

        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }

    companion object {
        private const val FLIGHTS_CACHE_PREFS = "rix_flights_cache"
        private const val KEY_FLIGHTS_JSON = "flights_json"
        private const val KEY_FLIGHTS_FETCHED_AT = "flights_fetched_at"
        private const val FLIGHTS_AUTO_REFRESH_MS = 60L * 60L * 1000L
        private val TIME_PATTERN = Regex("\\b\\d{1,2}:\\d{2}\\b")
        private val RIGA_ZONE: ZoneId = ZoneId.of("Europe/Riga")
    }
}
