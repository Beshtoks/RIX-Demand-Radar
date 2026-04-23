package com.rixradar.app

import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class EventsActivity : AppCompatActivity() {

    private lateinit var tvEventsTitle: TextView
    private lateinit var tvEventsSubtitle: TextView
    private lateinit var tvEventsBlockTitle: TextView
    private lateinit var layoutEventsList: LinearLayout
    private lateinit var tvEventsHint: TextView

    private val radarDataSource: RadarDataSource = RadarRepositoryProvider.dataSource

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_events)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "События"

        bindViews()
        render(radarDataSource.getEventsState())
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

    private fun render(state: EventsUiState) {
        tvEventsTitle.text = state.title
        tvEventsSubtitle.text = state.subtitle
        tvEventsBlockTitle.text = state.blockTitle
        tvEventsHint.text = state.hint

        layoutEventsList.removeAllViews()

        addEventRow(
            city = state.city1,
            title = state.title1,
            meta = state.meta1
        )

        addEventRow(
            city = state.city2,
            title = state.title2,
            meta = state.meta2
        )

        addEventRow(
            city = state.city3,
            title = state.title3,
            meta = state.meta3
        )

        addEventRow(
            city = state.city4,
            title = state.title4,
            meta = state.meta4
        )
    }

    private fun addEventRow(
        city: String,
        title: String,
        meta: String
    ) {
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
            setBackgroundColor(getColor(eventSquareColor(meta)))
            layoutParams = LinearLayout.LayoutParams(dp(10), dp(10)).apply {
                rightMargin = dp(8)
            }
        }

        val tvCity = TextView(this).apply {
            text = city
            setTextColor(getColor(R.color.rr_text_primary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        val eventType = extractEventType(title)
        val tvType = TextView(this).apply {
            text = eventType
            setTextColor(getColor(R.color.rr_text_secondary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        }

        topLine.addView(square)
        topLine.addView(tvCity)
        topLine.addView(tvType)

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

        val (startText, endText, zoneText) = splitMeta(meta)

        val tvStart = TextView(this).apply {
            text = startText
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
            text = endText
            setTextColor(getColor(R.color.rr_text_primary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        val tvZone = TextView(this).apply {
            text = zoneText
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

        bottomLine.addView(tvStart)
        bottomLine.addView(tvEnd)
        bottomLine.addView(tvZone)

        row.addView(topLine)
        row.addView(bottomLine)
        layoutEventsList.addView(row)
    }

    private fun extractEventType(title: String): String {
        val t = title.lowercase()

        return when {
            "концерт" in t -> "Концерт"
            "хоккей" in t -> "Хоккей"
            "футбол" in t -> "Футбол"
            "фестиваль" in t -> "Фестиваль"
            "шоу" in t -> "Шоу"
            "ярмар" in t -> "Ярмарка"
            else -> "Событие"
        }
    }

    private fun splitMeta(meta: String): Triple<String, String, String> {
        val m = meta.trim()

        return when {
            "высокий" in m.lowercase() -> Triple("Сегодня", "Пик", "Высокий")
            "средний" in m.lowercase() -> Triple("Сегодня", "Пик", "Средний")
            else -> Triple("Сегодня", "Пик", "Локально")
        }
    }

    private fun eventSquareColor(meta: String): Int {
        val m = meta.lowercase()

        return when {
            "высокий" in m -> R.color.rr_red
            "средний" in m -> R.color.rr_yellow
            else -> R.color.rr_green
        }
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
}