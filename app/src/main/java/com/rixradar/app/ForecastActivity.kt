package com.rixradar.app

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ForecastActivity : AppCompatActivity() {

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
    private lateinit var tvForecastAiTitle: TextView
    private lateinit var tvForecastAiText: TextView
    private lateinit var tvForecastHint: TextView

    private val radarDataSource: RadarDataSource = RadarRepositoryProvider.dataSource

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forecast)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Прогноз"

        bindViews()
        render(radarDataSource.getForecastState())
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun bindViews() {
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
        tvForecastAiTitle = findViewById(R.id.tvForecastAiTitle)
        tvForecastAiText = findViewById(R.id.tvForecastAiText)
        tvForecastHint = findViewById(R.id.tvForecastHint)
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
        tvForecastAiTitle.text = state.aiTitle
        tvForecastAiText.text = state.aiText
        tvForecastHint.text = state.hint
    }
}