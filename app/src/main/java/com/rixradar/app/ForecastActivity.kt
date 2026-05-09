package com.rixradar.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ForecastActivity : AppCompatActivity() {

    private lateinit var scrollForecastRoot: ScrollView
    private lateinit var blockForecast15Days: LinearLayout
    private lateinit var tvForecastTitle: TextView
    private lateinit var tvForecastSubtitle: TextView
    private lateinit var tvForecastNowTitle: TextView
    private lateinit var tvForecastNowValue: TextView
    private lateinit var tvForecastNowMeta: TextView
    private lateinit var tvForecastHour1Title: TextView
    private lateinit var tvForecastHour1Value: TextView
    private lateinit var tvForecastHour1Meta: TextView
    private lateinit var tvForecastHour3Title: TextView
    private lateinit var tvForecastHour3Value: TextView
    private lateinit var tvForecastHour3Meta: TextView
    private lateinit var tvForecastBestWindowTitle: TextView
    private lateinit var tvForecastBestWindowValue: TextView
    private lateinit var tvForecastBestWindowMeta: TextView
    private lateinit var tvForecastHint: TextView
    private lateinit var btnNavRadar: Button
    private lateinit var btnNavFlights: Button
    private lateinit var btnNavEvents: Button

    private val weatherExecutor = Executors.newSingleThreadExecutor()

    private var pullStartY = 0f
    private var isLoadingWeather = false

    private val cachePrefs by lazy {
        getSharedPreferences(WEATHER_CACHE_PREFS, MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forecast)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Прогноз погоды"

        bindViews()
        bindPullToRefresh()
        bindClicks()
        bindBottomNavigation()
        renderCachedWeatherOrEmpty()
        refreshIfCacheExpired()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        weatherExecutor.shutdownNow()
    }

    private fun bindViews() {
        scrollForecastRoot = findViewById(R.id.scrollForecastRoot)
        blockForecast15Days = findViewById(R.id.blockForecast15Days)
        tvForecastTitle = findViewById(R.id.tvForecastTitle)
        tvForecastSubtitle = findViewById(R.id.tvForecastSubtitle)
        tvForecastNowTitle = findViewById(R.id.tvForecastNowTitle)
        tvForecastNowValue = findViewById(R.id.tvForecastNowValue)
        tvForecastNowMeta = findViewById(R.id.tvForecastNowMeta)
        tvForecastHour1Title = findViewById(R.id.tvForecastHour1Title)
        tvForecastHour1Value = findViewById(R.id.tvForecastHour1Value)
        tvForecastHour1Meta = findViewById(R.id.tvForecastHour1Meta)
        tvForecastHour3Title = findViewById(R.id.tvForecastHour3Title)
        tvForecastHour3Value = findViewById(R.id.tvForecastHour3Value)
        tvForecastHour3Meta = findViewById(R.id.tvForecastHour3Meta)
        tvForecastBestWindowTitle = findViewById(R.id.tvForecastBestWindowTitle)
        tvForecastBestWindowValue = findViewById(R.id.tvForecastBestWindowValue)
        tvForecastBestWindowMeta = findViewById(R.id.tvForecastBestWindowMeta)
        tvForecastHint = findViewById(R.id.tvForecastHint)
        btnNavRadar = findViewById(R.id.btnNavRadar)
        btnNavFlights = findViewById(R.id.btnNavFlights)
        btnNavEvents = findViewById(R.id.btnNavEvents)
    }

    private fun bindPullToRefresh() {
        scrollForecastRoot.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> pullStartY = event.rawY
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val pulledDown = event.rawY - pullStartY
                    if (scrollForecastRoot.scrollY == 0 && pulledDown > dp(PULL_REFRESH_DISTANCE_DP)) {
                        loadWeather(force = true, showLoadingText = true)
                    }
                }
            }
            false
        }
    }

    private fun bindClicks() {
        blockForecast15Days.setOnClickListener {
            startActivity(Intent(this, Weather15Activity::class.java))
        }
    }

    private fun bindBottomNavigation() {
        btnNavRadar.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
        btnNavFlights.setOnClickListener {
            startActivity(Intent(this, FlightsActivity::class.java))
        }
        btnNavEvents.setOnClickListener {
            startActivity(Intent(this, EventsActivity::class.java))
        }
    }

    private fun renderCachedWeatherOrEmpty() {
        val cachedBody = cachePrefs.getString(KEY_WEATHER_JSON, null)

        if (cachedBody.isNullOrBlank()) {
            render(
                ForecastUiState(
                    title = "Прогноз погоды",
                    subtitle = "Сохранённого прогноза пока нет",
                    nowTitle = "Сегодня",
                    nowValue = "—",
                    nowMeta = "Потяни вниз, чтобы загрузить прогноз",
                    hour1Title = "Завтра",
                    hour1Value = "—",
                    hour1Meta = "Нет данных",
                    hour3Title = "Послезавтра",
                    hour3Value = "—",
                    hour3Meta = "Нет данных",
                    bestWindowTitle = "15 дней",
                    bestWindowValue = "Нет данных",
                    bestWindowMeta = "Нажми для подробного прогноза после загрузки",
                    hint = "Потяни экран вниз, чтобы обновить прогноз"
                )
            )
            return
        }

        renderWeatherFromRawBody(cachedBody, fromCache = true)
    }

    private fun refreshIfCacheExpired() {
        val lastFetchAt = cachePrefs.getLong(KEY_WEATHER_FETCHED_AT, 0L)
        val hasCache = !cachePrefs.getString(KEY_WEATHER_JSON, null).isNullOrBlank()
        val expired = System.currentTimeMillis() - lastFetchAt >= WEATHER_AUTO_REFRESH_MS

        if (!hasCache || expired) {
            loadWeather(force = false, showLoadingText = !hasCache)
        }
    }

    private fun loadWeather(force: Boolean, showLoadingText: Boolean) {
        if (isLoadingWeather) return

        val lastFetchAt = cachePrefs.getLong(KEY_WEATHER_FETCHED_AT, 0L)
        val hasCache = !cachePrefs.getString(KEY_WEATHER_JSON, null).isNullOrBlank()
        val freshEnough = System.currentTimeMillis() - lastFetchAt < WEATHER_AUTO_REFRESH_MS

        if (!force && hasCache && freshEnough) return

        isLoadingWeather = true

        if (showLoadingText) {
            tvForecastTitle.text = "Прогноз погоды"
            tvForecastSubtitle.text = "Обновление..."
            tvForecastHint.text = "Подключение к Open-Meteo..."
        } else {
            tvForecastHint.text = appendUpdateStatus(tvForecastHint.text.toString(), "Фоновое обновление прогноза...")
        }

        Thread {
            val result = fetchWeatherFromOpenMeteo()

            runOnUiThread {
                isLoadingWeather = false

                if (!result.success || result.rawBody.isNullOrBlank()) {
                    if (!hasCache) {
                        render(
                            ForecastUiState(
                                title = "Прогноз погоды",
                                subtitle = "Погода временно недоступна",
                                nowTitle = "Сегодня",
                                nowValue = "—",
                                nowMeta = result.errorMessage ?: "Не удалось получить прогноз",
                                hour1Title = "Завтра",
                                hour1Value = "—",
                                hour1Meta = "Нет данных",
                                hour3Title = "Послезавтра",
                                hour3Value = "—",
                                hour3Meta = "Нет данных",
                                bestWindowTitle = "15 дней",
                                bestWindowValue = "Нет данных",
                                bestWindowMeta = "Нажми для подробного прогноза после загрузки",
                                hint = result.errorMessage ?: "Ошибка сети"
                            )
                        )
                    } else {
                        tvForecastHint.text = appendUpdateStatus(
                            tvForecastHint.text.toString(),
                            "Обновление не удалось: ${result.errorMessage ?: "ошибка сети"}"
                        )
                    }
                    return@runOnUiThread
                }

                cachePrefs.edit()
                    .putString(KEY_WEATHER_JSON, result.rawBody)
                    .putLong(KEY_WEATHER_FETCHED_AT, System.currentTimeMillis())
                    .apply()

                renderWeatherFromRawBody(result.rawBody, fromCache = false)
            }
        }.start()
    }

    private fun fetchWeatherFromOpenMeteo(): WeatherFetchResult {
        val future = weatherExecutor.submit(
            Callable {
                var connection: HttpURLConnection? = null
                try {
                    val url = URL(OPEN_METEO_URL)
                    connection = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 15000
                        readTimeout = 15000
                        setRequestProperty("Accept", "application/json")
                        doInput = true
                    }

                    val responseCode = connection.responseCode
                    val inputStream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
                    val body = inputStream?.bufferedReader()?.use { it.readText() }

                    if (responseCode in 200..299) {
                        WeatherFetchResult(true, body, null)
                    } else {
                        WeatherFetchResult(false, body, "HTTP $responseCode")
                    }
                } catch (e: Exception) {
                    WeatherFetchResult(false, null, e.message ?: e.javaClass.simpleName)
                } finally {
                    connection?.disconnect()
                }
            }
        )

        return try {
            future.get(20, TimeUnit.SECONDS)
        } catch (e: Exception) {
            future.cancel(true)
            WeatherFetchResult(false, null, e.message ?: e.javaClass.simpleName)
        }
    }

    private fun renderWeatherFromRawBody(rawBody: String, fromCache: Boolean) {
        try {
            val daily = JSONObject(rawBody).optJSONObject("daily") ?: JSONObject()
            val days = parseDailyWeather(daily)

            if (days.isEmpty()) {
                tvForecastSubtitle.text = "Прогноз пустой"
                tvForecastHint.text = "Open-Meteo не вернул дневной прогноз"
                return
            }

            val today = days.getOrNull(0)
            val tomorrow = days.getOrNull(1)
            val afterTomorrow = days.getOrNull(2)
            val summary = buildFifteenDaysSummary(days)

            render(
                ForecastUiState(
                    title = "Прогноз погоды",
                    subtitle = if (fromCache) "Сохранённый прогноз" else "Прогноз обновлён",
                    nowTitle = "Сегодня",
                    nowValue = with(WeatherUtils) { today?.temperatureLine() } ?: "—",
                    nowMeta = with(WeatherUtils) { today?.metaLine() } ?: "Нет данных",
                    hour1Title = "Завтра",
                    hour1Value = with(WeatherUtils) { tomorrow?.temperatureLine() } ?: "—",
                    hour1Meta = with(WeatherUtils) { tomorrow?.metaLine() } ?: "Нет данных",
                    hour3Title = "Послезавтра",
                    hour3Value = with(WeatherUtils) { afterTomorrow?.temperatureLine() } ?: "—",
                    hour3Meta = with(WeatherUtils) { afterTomorrow?.metaLine() } ?: "Нет данных",
                    bestWindowTitle = "15 дней",
                    bestWindowValue = summary.first,
                    bestWindowMeta = summary.second,
                    hint = buildHint(fromCache)
                )
            )

            applyTemperatureRange(tvForecastNowValue, today)
            applyTemperatureRange(tvForecastHour1Value, tomorrow)
            applyTemperatureRange(tvForecastHour3Value, afterTomorrow)
        } catch (e: Exception) {
            tvForecastSubtitle.text = "Ошибка чтения прогноза"
            tvForecastHint.text = e.message ?: "Не удалось разобрать прогноз"
        }
    }

    private fun render(state: ForecastUiState) {
        tvForecastTitle.text = state.title
        tvForecastSubtitle.text = state.subtitle
        tvForecastNowTitle.text = state.nowTitle
        tvForecastNowValue.text = state.nowValue
        tvForecastNowMeta.text = state.nowMeta
        tvForecastHour1Title.text = state.hour1Title
        tvForecastHour1Value.text = state.hour1Value
        tvForecastHour1Meta.text = state.hour1Meta
        tvForecastHour3Title.text = state.hour3Title
        tvForecastHour3Value.text = state.hour3Value
        tvForecastHour3Meta.text = state.hour3Meta
        tvForecastBestWindowTitle.text = state.bestWindowTitle
        tvForecastBestWindowValue.text = state.bestWindowValue
        tvForecastBestWindowMeta.text = state.bestWindowMeta
        tvForecastHint.text = state.hint
    }

    private fun applyTemperatureRange(target: TextView, day: WeatherDay?) {
        if (day == null || day.maxTemp.isNaN() || day.minTemp.isNaN()) return
        target.text = buildTemperatureRangeSpannable(day.maxTemp, day.minTemp)
    }

    private fun buildTemperatureRangeSpannable(maxTemp: Double, minTemp: Double): SpannableString {
        val maxText = WeatherUtils.formatTemperature(maxTemp)
        val minText = WeatherUtils.formatTemperature(minTemp)
        val slashText = " / "
        val fullText = maxText + slashText + minText
        val result = SpannableString(fullText)

        val maxStart = 0
        val maxEnd = maxText.length
        val slashStart = maxEnd
        val slashEnd = slashStart + slashText.length
        val minStart = slashEnd
        val middleTemp = (maxTemp + minTemp) / 2.0

        result.setSpan(ForegroundColorSpan(WeatherUtils.temperatureToColor(maxTemp)), maxStart, maxEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        result.setSpan(ForegroundColorSpan(WeatherUtils.temperatureToColor(middleTemp)), slashStart, slashEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        result.setSpan(ForegroundColorSpan(WeatherUtils.temperatureToColor(minTemp)), minStart, fullText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return result
    }

    private fun parseDailyWeather(daily: JSONObject): List<WeatherDay> =
        WeatherUtils.parseDailyWeather(daily)

    private fun temperatureToColor(value: Double): Int = WeatherUtils.temperatureToColor(value)

    private fun buildFifteenDaysSummary(days: List<WeatherDay>): Pair<String, String> {
        val wetDays = days.count { it.precipitationMm >= 1.0 || with(WeatherUtils) { it.isRainLike() } }
        val windyDays = days.count { it.windKmh >= 35.0 }
        val warmDays = days.count { it.maxTemp >= 18.0 }
        val coldDays = days.count { it.maxTemp <= 3.0 }
        val main = when {
            wetDays >= 7 -> "Много дождливых дней"
            warmDays >= 7 -> "Много тёплых дней"
            coldDays >= 5 -> "Холодный период"
            windyDays >= 5 -> "Ветрено"
            else -> "Смешанный прогноз"
        }
        val meta = "Дождь: $wetDays/15 • Ветер: $windyDays/15 • Тёплых: $warmDays/15"
        return main to meta
    }

    private fun buildHint(fromCache: Boolean): String {
        val prefix = if (fromCache) "Сохранено локально" else "Обновлено из Open-Meteo"
        return "$prefix • автообновление не чаще 1 раза в 30 минут • потяни вниз для ручного обновления"
    }

    private fun appendUpdateStatus(current: String, status: String): String {
        val base = current.substringBefore(" • Обновление")
        return "$base • $status"
    }

    private fun dp(value: Int): Float = value * resources.displayMetrics.density

    private data class WeatherFetchResult(
        val success: Boolean,
        val rawBody: String?,
        val errorMessage: String?
    )

    companion object {
        private const val WEATHER_CACHE_PREFS = "weather_cache"
        private const val KEY_WEATHER_JSON = "weather_json"
        private const val KEY_WEATHER_FETCHED_AT = "weather_fetched_at"
        private const val WEATHER_AUTO_REFRESH_MS = 30L * 60L * 1000L
        private const val PULL_REFRESH_DISTANCE_DP = 72
        private const val OPEN_METEO_URL: String = "https://api.open-meteo.com/v1/forecast?latitude=56.9236&longitude=23.9711&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum,wind_speed_10m_max&forecast_days=15&timezone=Europe%2FRiga"
    }
}
