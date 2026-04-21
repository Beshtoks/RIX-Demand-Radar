package com.rixradar.app

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class MainActivity : AppCompatActivity() {

    private lateinit var tvCity: TextView
    private lateinit var tvUpdated: TextView
    private lateinit var tvDemandValue: TextView
    private lateinit var tvDemandStatus: TextView
    private lateinit var tvAiText: TextView

    private lateinit var btnRefresh: Button
    private lateinit var btnFlights: Button
    private lateinit var btnEvents: Button
    private lateinit var btnForecast: Button
    private lateinit var btnMap: Button
    private lateinit var btnAi: Button

    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        bindButtons()

        render(buildInitialState())
    }

    private fun bindViews() {
        tvCity = findViewById(R.id.tvCity)
        tvUpdated = findViewById(R.id.tvUpdated)
        tvDemandValue = findViewById(R.id.tvDemandValue)
        tvDemandStatus = findViewById(R.id.tvDemandStatus)
        tvAiText = findViewById(R.id.tvAiText)

        btnRefresh = findViewById(R.id.btnRefresh)
        btnFlights = findViewById(R.id.btnFlights)
        btnEvents = findViewById(R.id.btnEvents)
        btnForecast = findViewById(R.id.btnForecast)
        btnMap = findViewById(R.id.btnMap)
        btnAi = findViewById(R.id.btnAi)
    }

    private fun bindButtons() {
        btnRefresh.setOnClickListener {
            performRefresh()
        }

        btnFlights.setOnClickListener {
            showStub("Экран «Рейсы» будет подключён следующим этапом")
        }

        btnEvents.setOnClickListener {
            showStub("Экран «События» будет подключён следующим этапом")
        }

        btnForecast.setOnClickListener {
            showStub("Экран «Прогноз» будет подключён следующим этапом")
        }

        btnMap.setOnClickListener {
            showStub("Экран «Карта» будет подключён следующим этапом")
        }

        btnAi.setOnClickListener {
            showStub("Блок ИИ будет подключён следующим этапом")
        }
    }

    private fun buildInitialState(): DashboardUiState {
        return DashboardUiState(
            cityText = getString(R.string.label_city),
            updatedText = buildUpdatedText(),
            demandValueText = getString(R.string.label_demand_value),
            demandStatusText = getString(R.string.label_demand_status),
            aiText = getString(R.string.ai_text)
        )
    }

    private fun performRefresh() {
        setRefreshInProgress(true)

        btnRefresh.postDelayed({
            val refreshedState = buildInitialState()
            render(refreshedState)
            setRefreshInProgress(false)
            showStub("Данные обновлены")
        }, 900)
    }

    private fun setRefreshInProgress(inProgress: Boolean) {
        btnRefresh.isEnabled = !inProgress
        btnRefresh.text = if (inProgress) {
            "Обновление..."
        } else {
            getString(R.string.button_refresh)
        }
    }

    private fun render(state: DashboardUiState) {
        tvCity.text = state.cityText
        tvUpdated.text = state.updatedText
        tvDemandValue.text = state.demandValueText
        tvDemandStatus.text = state.demandStatusText
        tvAiText.text = state.aiText
    }

    private fun buildUpdatedText(): String {
        val currentTime = LocalTime.now().format(timeFormatter)
        return "Обновлено: $currentTime"
    }

    private fun showStub(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}