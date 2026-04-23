package com.rixradar.app

interface RadarDataSource {
    fun getDashboardState(): DashboardUiState
    fun getFlightsState(): FlightsUiState
    fun getEventsState(): EventsUiState
    fun getForecastState(): ForecastUiState
    fun getMapState(): MapUiState
    fun getAiState(): AiUiState
}