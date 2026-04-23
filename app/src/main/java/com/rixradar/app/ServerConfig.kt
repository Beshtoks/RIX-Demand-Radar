package com.rixradar.app

object ServerConfig {

    const val BASE_URL = "http://204.168.153.141:8010"

    const val DASHBOARD_PATH = "/api/dashboard"
    const val FLIGHTS_PATH = "/api/flights"
    const val EVENTS_PATH = "/api/events"
    const val FORECAST_PATH = "/api/forecast"
    const val MAP_PATH = "/api/map"
    const val AI_PATH = "/api/ai"

    fun buildUrl(path: String): String {
        return BASE_URL.trimEnd('/') + path
    }
}