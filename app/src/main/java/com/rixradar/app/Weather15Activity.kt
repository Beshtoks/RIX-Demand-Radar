package com.rixradar.app

import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject

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

        val topLine = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val top = TextView(this).apply {
            text = buildWeatherRowTitle(day)
            setTextColor(getColor(R.color.rr_text_primary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        val weekDay = TextView(this).apply {
            text = weekDayLabel(day.isoDate)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        topLine.addView(top)
        topLine.addView(weekDay)

        val meta = TextView(this).apply {
            text = day.metaLine()
            setTextColor(getColor(R.color.rr_text_secondary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(0, dp(4), 0, 0)
        }

        row.addView(topLine)
        row.addView(meta)
        return row
    }

    private fun buildWeatherRowTitle(day: WeatherDay): SpannableString {
        val prefix = "${day.displayDate}   "
        val maxText = WeatherUtils.formatTemperature(day.maxTemp)
        val slashText = " / "
        val minText = WeatherUtils.formatTemperature(day.minTemp)
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

            result.setSpan(ForegroundColorSpan(WeatherUtils.temperatureToColor(day.maxTemp)), maxStart, maxEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            result.setSpan(RelativeSizeSpan(1.25f), maxStart, maxEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            result.setSpan(ForegroundColorSpan(WeatherUtils.temperatureToColor(middleTemp)), slashStart, slashEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            result.setSpan(RelativeSizeSpan(1.25f), slashStart, slashEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            result.setSpan(ForegroundColorSpan(WeatherUtils.temperatureToColor(day.minTemp)), minStart, minEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            result.setSpan(RelativeSizeSpan(1.25f), minStart, minEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        return result
    }

    private fun temperatureToColor(value: Double): Int = WeatherUtils.temperatureToColor(value)

    private fun parseDailyWeather(daily: JSONObject): List<WeatherDay> =
        WeatherUtils.parseDailyWeather(daily)

    private fun weekDayLabel(isoDate: String): String {
        return try {
            when (java.time.LocalDate.parse(isoDate).dayOfWeek) {
                java.time.DayOfWeek.MONDAY -> "Пн"
                java.time.DayOfWeek.TUESDAY -> "Вт"
                java.time.DayOfWeek.WEDNESDAY -> "Ср"
                java.time.DayOfWeek.THURSDAY -> "Чт"
                java.time.DayOfWeek.FRIDAY -> "Пт"
                java.time.DayOfWeek.SATURDAY -> "Сб"
                java.time.DayOfWeek.SUNDAY -> "Вс"
            }
        } catch (_: Exception) { "" }
    }

    private fun WeatherDay.temperatureLine(): String = with(WeatherUtils) { temperatureLine() }
    private fun WeatherDay.metaLine(): String = with(WeatherUtils) { metaLine() }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val WEATHER_CACHE_PREFS = "weather_cache"
        private const val KEY_WEATHER_JSON = "weather_json"
    }
}
