package com.rixradar.app

import android.graphics.Typeface
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class Weather15Activity : AppCompatActivity() {

    private lateinit var tvWeather15Subtitle: TextView
    private lateinit var layoutWeather15List: LinearLayout

    private val cachePrefs by lazy {
        getSharedPreferences(WEATHER_CACHE_PREFS, MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_weather_15)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Прогноз на 15 дней"

        tvWeather15Subtitle = findViewById(R.id.tvWeather15Subtitle)
        layoutWeather15List = findViewById(R.id.layoutWeather15List)

        renderFromCache()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun renderFromCache() {
        val raw = cachePrefs.getString(KEY_WEATHER_JSON, null)
        if (raw.isNullOrBlank()) {
            tvWeather15Subtitle.text = "Сохранённого прогноза пока нет"
            renderMessage("Вернись в прогноз погоды и потяни вниз для обновления")
            return
        }

        try {
            val daily = JSONObject(raw).optJSONObject("daily") ?: JSONObject()
            val days = parseDailyWeather(daily)

            if (days.isEmpty()) {
                tvWeather15Subtitle.text = "Прогноз пустой"
                renderMessage("Open-Meteo не вернул дневной прогноз")
                return
            }

            tvWeather15Subtitle.text = "Сохранённый прогноз • ${days.size} дней"
            renderDays(days)
        } catch (e: Exception) {
            tvWeather15Subtitle.text = "Ошибка чтения прогноза"
            renderMessage(e.message ?: "Не удалось разобрать прогноз")
        }
    }

    private fun renderDays(days: List<WeatherDay>) {
        layoutWeather15List.removeAllViews()
        days.forEach { day ->
            layoutWeather15List.addView(createWeatherRow(day))
        }
    }

    private fun renderMessage(message: String) {
        layoutWeather15List.removeAllViews()
        val tv = TextView(this).apply {
            text = message
            setTextColor(getColor(R.color.rr_text_primary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = getDrawable(R.color.rr_surface_2)
        }
        layoutWeather15List.addView(tv)
    }

    private fun createWeatherRow(day: WeatherDay): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = getDrawable(R.color.rr_surface_2)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(7)
            }
        }

        val top = TextView(this).apply {
            text = buildWeatherRowTitle(day)
            setTextColor(getColor(R.color.rr_text_primary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setTypeface(typeface, Typeface.BOLD)
        }

        val meta = TextView(this).apply {
            text = day.metaLine()
            setTextColor(getColor(R.color.rr_text_secondary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(0, dp(4), 0, 0)
        }

        row.addView(top)
        row.addView(meta)
        return row
    }

    private fun buildWeatherRowTitle(day: WeatherDay): SpannableString {
        val prefix = "${day.displayDate}   "
        val maxText = formatTemperature(day.maxTemp)
        val slashText = " / "
        val minText = formatTemperature(day.minTemp)
        val fullText = prefix + maxText + slashText + minText
        val result = SpannableString(fullText)

        if (!day.maxTemp.isNaN() && !day.minTemp.isNaN()) {
            val maxStart = prefix.length
            val maxEnd = maxStart + maxText.length
            val slashStart = maxEnd
            val slashEnd = slashStart + slashText.length
            val minStart = slashEnd
            val minEnd = fullText.length
            val middleTemp = (day.maxTemp + day.minTemp) / 2.0

            result.setSpan(ForegroundColorSpan(temperatureToColor(day.maxTemp)), maxStart, maxEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            result.setSpan(ForegroundColorSpan(temperatureToColor(middleTemp)), slashStart, slashEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            result.setSpan(ForegroundColorSpan(temperatureToColor(day.minTemp)), minStart, minEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        return result
    }

    private fun temperatureToColor(value: Double): Int {
        if (value.isNaN()) return getColor(R.color.rr_text_primary)

        val clamped = value.coerceIn(-40.0, 40.0)
        return if (clamped < 0.0) {
            val t = (-clamped / 40.0).toFloat()
            interpolateColor(Color.WHITE, Color.rgb(0, 70, 220), t)
        } else {
            val t = (clamped / 40.0).toFloat()
            when {
                t <= 0.5f -> interpolateColor(Color.WHITE, Color.rgb(255, 215, 0), t / 0.5f)
                t <= 0.75f -> interpolateColor(Color.rgb(255, 215, 0), Color.rgb(255, 130, 0), (t - 0.5f) / 0.25f)
                else -> interpolateColor(Color.rgb(255, 130, 0), Color.rgb(220, 0, 0), (t - 0.75f) / 0.25f)
            }
        }
    }

    private fun interpolateColor(startColor: Int, endColor: Int, fraction: Float): Int {
        val f = fraction.coerceIn(0f, 1f)
        val a = Color.alpha(startColor) + ((Color.alpha(endColor) - Color.alpha(startColor)) * f).toInt()
        val r = Color.red(startColor) + ((Color.red(endColor) - Color.red(startColor)) * f).toInt()
        val g = Color.green(startColor) + ((Color.green(endColor) - Color.green(startColor)) * f).toInt()
        val b = Color.blue(startColor) + ((Color.blue(endColor) - Color.blue(startColor)) * f).toInt()
        return Color.argb(a, r, g, b)
    }

    private fun parseDailyWeather(daily: JSONObject): List<WeatherDay> {
        val dates = daily.optJSONArray("time") ?: JSONArray()
        val codes = daily.optJSONArray("weather_code") ?: JSONArray()
        val tempMax = daily.optJSONArray("temperature_2m_max") ?: JSONArray()
        val tempMin = daily.optJSONArray("temperature_2m_min") ?: JSONArray()
        val precipitation = daily.optJSONArray("precipitation_sum") ?: JSONArray()
        val wind = daily.optJSONArray("wind_speed_10m_max") ?: JSONArray()

        val result = mutableListOf<WeatherDay>()
        for (i in 0 until dates.length()) {
            val isoDate = dates.optString(i, "")
            if (isoDate.isBlank()) continue
            result.add(
                WeatherDay(
                    isoDate = isoDate,
                    displayDate = formatDate(isoDate),
                    code = codes.optInt(i, 0),
                    minTemp = tempMin.optDouble(i, Double.NaN),
                    maxTemp = tempMax.optDouble(i, Double.NaN),
                    precipitationMm = precipitation.optDouble(i, 0.0),
                    windKmh = wind.optDouble(i, 0.0)
                )
            )
        }
        return result
    }

    private fun formatDate(isoDate: String): String {
        return try {
            LocalDate.parse(isoDate).format(DateTimeFormatter.ofPattern("dd.MM"))
        } catch (_: Exception) {
            isoDate
        }
    }

    private fun WeatherDay.temperatureLine(): String {
        return "${formatTemperature(maxTemp)} / ${formatTemperature(minTemp)}"
    }

    private fun WeatherDay.metaLine(): String {
        val parts = mutableListOf<String>()
        parts.add(weatherDescription(code))
        if (precipitationMm >= 0.1) parts.add("осадки ${formatOneDecimal(precipitationMm)} мм")
        if (windKmh >= 25.0) parts.add("ветер ${windKmh.toInt()} км/ч")
        return parts.joinToString(" • ")
    }

    private fun weatherDescription(code: Int): String {
        return when (code) {
            0 -> "ясно"
            1, 2 -> "переменная облачность"
            3 -> "пасмурно"
            45, 48 -> "туман"
            51, 53, 55 -> "морось"
            56, 57 -> "ледяная морось"
            61 -> "небольшой дождь"
            63 -> "дождь"
            65 -> "сильный дождь"
            66, 67 -> "ледяной дождь"
            71 -> "небольшой снег"
            73 -> "снег"
            75 -> "сильный снег"
            77 -> "снежные зёрна"
            80 -> "кратковременный дождь"
            81 -> "ливни"
            82 -> "сильные ливни"
            85, 86 -> "снежные заряды"
            95 -> "гроза"
            96, 99 -> "гроза с градом"
            else -> "погода без уточнения"
        }
    }

    private fun formatTemperature(value: Double): String {
        if (value.isNaN()) return "—"
        val rounded = value.toInt()
        return if (rounded > 0) "+$rounded°" else "$rounded°"
    }

    private fun formatOneDecimal(value: Double): String {
        return String.format(Locale.US, "%.1f", value)
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private data class WeatherDay(
        val isoDate: String,
        val displayDate: String,
        val code: Int,
        val minTemp: Double,
        val maxTemp: Double,
        val precipitationMm: Double,
        val windKmh: Double
    )

    companion object {
        private const val WEATHER_CACHE_PREFS = "weather_cache"
        private const val KEY_WEATHER_JSON = "weather_json"
    }
}
