package com.rixradar.app

interface RadarDataSource {
    fun getDashboardState(): DashboardUiState
    fun getFlightsState(): FlightsUiState
    fun getEventsState(): EventsUiState
    fun getForecastState(): ForecastUiState
    fun getAiState(): AiUiState
}