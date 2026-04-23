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
import java.time.Duration
import java.time.LocalTime

class FlightsActivity : AppCompatActivity() {

    private lateinit var tvFlightsTitle: TextView
    private lateinit var tvFlightsSubtitle: TextView
    private lateinit var tvFlightsBlockTitle: TextView
    private lateinit var layoutFlightsList: LinearLayout
    private lateinit var tvFlightsHint: TextView

    private val serverClient = ServerClient()

    private val handler = Handler(Looper.getMainLooper())
    private val refreshIntervalMs = 60_000L

    private val refreshRunnable = object : Runnable {
        override fun run() {
            loadFlights()
            handler.postDelayed(this, refreshIntervalMs)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_flights)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Рейсы"

        bindViews()
        loadFlights()
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
        tvFlightsTitle = findViewById(R.id.tvFlightsTitle)
        tvFlightsSubtitle = findViewById(R.id.tvFlightsSubtitle)
        tvFlightsBlockTitle = findViewById(R.id.tvFlightsBlockTitle)
        layoutFlightsList = findViewById(R.id.layoutFlightsList)
        tvFlightsHint = findViewById(R.id.tvFlightsHint)
    }

    private fun loadFlights() {
        tvFlightsTitle.text = "Прилёты RIX"
        tvFlightsSubtitle.text = "Загрузка..."
        tvFlightsBlockTitle.text = "Окно: от -1 часа до +24 часов"
        tvFlightsHint.text = "Подключение к backend..."

        Thread {
            val response = serverClient.fetch("/api/real-flights")

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread

                if (!response.success || response.rawBody.isNullOrBlank()) {
                    tvFlightsSubtitle.text = "Сервер временно недоступен"
                    tvFlightsHint.text = response.errorMessage ?: "Не удалось получить данные"
                    renderErrorRow("Не удалось загрузить рейсы")
                    return@runOnUiThread
                }

                try {
                    val json = JSONObject(response.rawBody)
                    val flights = json.optJSONArray("flights") ?: JSONArray()

                    tvFlightsTitle.text = "Прилёты RIX"
                    tvFlightsSubtitle.text = "Компактный рабочий поток"
                    tvFlightsBlockTitle.text = "Окно: от -1 часа до +24 часов"
                    tvFlightsHint.text = buildHintText(json, flights.length())

                    renderFlights(flights)
                } catch (e: Exception) {
                    tvFlightsSubtitle.text = "Ошибка чтения JSON"
                    tvFlightsHint.text = e.message ?: "Не удалось разобрать ответ"
                    renderErrorRow("Ошибка разбора списка рейсов")
                }
            }
        }.start()
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

    private fun buildHintText(json: JSONObject, count: Int): String {
        val sourceUrl = json.optString("sourceUrl", "")
        val windowStart = json.optString("windowStart", "")
        val windowEnd = json.optString("windowEnd", "")
        return "Источник: $sourceUrl\nОкно: $windowStart → $windowEnd\nРейсов: $count"
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
}