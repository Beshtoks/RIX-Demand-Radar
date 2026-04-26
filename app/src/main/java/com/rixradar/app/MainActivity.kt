package com.rixradar.app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var tvCity: TextView
    private lateinit var tvUpdated: TextView
    private lateinit var tvDataMode: TextView
    private lateinit var tvDemandValue: TextView
    private lateinit var tvDemandStatus: TextView
    private lateinit var tvFlight1: TextView
    private lateinit var tvFlight2: TextView
    private lateinit var tvFlight3: TextView
    private lateinit var tvEvent1: TextView
    private lateinit var tvEvent2: TextView
    private lateinit var tvEvent3: TextView
    private lateinit var tvAiText: TextView

    private lateinit var btnFlights: Button
    private lateinit var btnEvents: Button
    private lateinit var btnForecast: Button
    private lateinit var btnMap: Button
    private lateinit var btnAi: Button

    private val radarDataSource: RadarDataSource = RadarRepositoryProvider.dataSource
    private val startupFallbackDataSource: RadarDataSource = FakeServerRadarRepository()

    private var firstResumeHandled = false
    private var dashboardLoading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        bindButtons()

        render(startupFallbackDataSource.getDashboardState())
        renderDataMode("Режим данных: SERVER / загрузка...")
        loadDashboardFromServer()
    }

    override fun onResume() {
        super.onResume()

        if (!firstResumeHandled) {
            firstResumeHandled = true
            return
        }

        loadDashboardFromServer()
    }

    private fun bindViews() {
        tvCity = findViewById(R.id.tvCity)
        tvUpdated = findViewById(R.id.tvUpdated)
        tvDataMode = findViewById(R.id.tvDataMode)
        tvDemandValue = findViewById(R.id.tvDemandValue)
        tvDemandStatus = findViewById(R.id.tvDemandStatus)
        tvFlight1 = findViewById(R.id.tvFlight1)
        tvFlight2 = findViewById(R.id.tvFlight2)
        tvFlight3 = findViewById(R.id.tvFlight3)
        tvEvent1 = findViewById(R.id.tvEvent1)
        tvEvent2 = findViewById(R.id.tvEvent2)
        tvEvent3 = findViewById(R.id.tvEvent3)
        tvAiText = findViewById(R.id.tvAiText)

        btnFlights = findViewById(R.id.btnFlights)
        btnEvents = findViewById(R.id.btnEvents)
        btnForecast = findViewById(R.id.btnForecast)
        btnMap = findViewById(R.id.btnMap)
        btnAi = findViewById(R.id.btnAi)
    }

    private fun bindButtons() {
        btnFlights.setOnClickListener { startActivity(Intent(this, FlightsActivity::class.java)) }
        btnEvents.setOnClickListener { startActivity(Intent(this, EventsActivity::class.java)) }
        btnForecast.setOnClickListener { startActivity(Intent(this, ForecastActivity::class.java)) }
        btnMap.setOnClickListener { startActivity(Intent(this, MapActivity::class.java)) }
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
        tvDemandValue.text = state.demandValueText
        tvDemandStatus.text = state.demandStatusText
        tvFlight1.text = state.flight1Text
        tvFlight2.text = state.flight2Text
        tvFlight3.text = state.flight3Text
        tvEvent1.text = state.event1Text
        tvEvent2.text = state.event2Text
        tvEvent3.text = state.event3Text
        tvAiText.text = state.aiText
    }

    private fun renderDataMode(customText: String? = null) {
        tvDataMode.text = customText ?: "Режим данных: ${RadarRepositoryProvider.currentMode.name}"
    }
}
