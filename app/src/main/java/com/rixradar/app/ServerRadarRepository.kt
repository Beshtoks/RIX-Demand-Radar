package com.rixradar.app

import org.json.JSONObject

class ServerRadarRepository : RadarDataSource {

    private val fallbackDataSource: RadarDataSource = FakeServerRadarRepository()
    private val serverClient = ServerClient()

    override fun getDashboardState(): DashboardUiState {
        val response = serverClient.fetch(ServerConfig.DASHBOARD_PATH)
        val fallback = fallbackDataSource.getDashboardState()

        if (!response.success) {
            return fallback.copy(
                updatedText = "Обновлено: server dashboard error"
            )
        }

        return try {
            val json = JSONObject(response.rawBody ?: "")

            DashboardUiState(
                cityText = json.optStringOrDefault("cityText", fallback.cityText),
                updatedText = json.optStringOrDefault("updatedText", fallback.updatedText),
                demandValueText = json.optStringOrDefault("demandValueText", fallback.demandValueText),
                demandStatusText = json.optStringOrDefault("demandStatusText", fallback.demandStatusText),
                reason1Text = json.optStringOrDefault("reason1Text", fallback.reason1Text),
                reason2Text = json.optStringOrDefault("reason2Text", fallback.reason2Text),
                reason3Text = json.optStringOrDefault("reason3Text", fallback.reason3Text),
                flight1Text = json.optStringOrDefault("flight1Text", fallback.flight1Text),
                flight2Text = json.optStringOrDefault("flight2Text", fallback.flight2Text),
                flight3Text = json.optStringOrDefault("flight3Text", fallback.flight3Text),
                event1Text = json.optStringOrDefault("event1Text", fallback.event1Text),
                event2Text = json.optStringOrDefault("event2Text", fallback.event2Text),
                event3Text = json.optStringOrDefault("event3Text", fallback.event3Text),
                aiText = json.optStringOrDefault("aiText", fallback.aiText)
            )
        } catch (_: Exception) {
            fallback.copy(
                updatedText = "Обновлено: dashboard JSON error"
            )
        }
    }

    override fun getFlightsState(): FlightsUiState {
        val response = serverClient.fetch(ServerConfig.FLIGHTS_PATH)
        val fallback = fallbackDataSource.getFlightsState()

        if (!response.success) {
            return fallback.copy(
                subtitle = "Сервер временно недоступен"
            )
        }

        return try {
            val json = JSONObject(response.rawBody ?: "")

            FlightsUiState(
                title = json.optStringOrDefault("title", fallback.title),
                subtitle = json.optStringOrDefault("subtitle", fallback.subtitle),
                blockTitle = json.optStringOrDefault("blockTitle", fallback.blockTitle),

                route1 = json.optStringOrDefault("route1", fallback.route1),
                meta1 = json.optStringOrDefault("meta1", fallback.meta1),
                time1 = json.optStringOrDefault("time1", fallback.time1),
                status1 = json.optStringOrDefault("status1", fallback.status1),

                route2 = json.optStringOrDefault("route2", fallback.route2),
                meta2 = json.optStringOrDefault("meta2", fallback.meta2),
                time2 = json.optStringOrDefault("time2", fallback.time2),
                status2 = json.optStringOrDefault("status2", fallback.status2),

                route3 = json.optStringOrDefault("route3", fallback.route3),
                meta3 = json.optStringOrDefault("meta3", fallback.meta3),
                time3 = json.optStringOrDefault("time3", fallback.time3),
                status3 = json.optStringOrDefault("status3", fallback.status3),

                route4 = json.optStringOrDefault("route4", fallback.route4),
                meta4 = json.optStringOrDefault("meta4", fallback.meta4),
                time4 = json.optStringOrDefault("time4", fallback.time4),
                status4 = json.optStringOrDefault("status4", fallback.status4),

                hint = json.optStringOrDefault("hint", fallback.hint)
            )
        } catch (_: Exception) {
            fallback.copy(
                subtitle = "Ошибка чтения JSON рейсов"
            )
        }
    }

    override fun getEventsState(): EventsUiState {
        val response = serverClient.fetch(ServerConfig.EVENTS_PATH)
        val fallback = fallbackDataSource.getEventsState()

        if (!response.success) {
            return fallback.copy(
                subtitle = "Сервер событий временно недоступен",
                hint = "Работаем на резервных данных"
            )
        }

        return try {
            val json = JSONObject(response.rawBody ?: "")

            EventsUiState(
                title = json.optStringOrDefault("title", fallback.title),
                subtitle = json.optStringOrDefault("subtitle", fallback.subtitle),
                blockTitle = json.optStringOrDefault("blockTitle", fallback.blockTitle),

                city1 = json.optStringOrDefault("city1", fallback.city1),
                title1 = json.optStringOrDefault("title1", fallback.title1),
                meta1 = json.optStringOrDefault("meta1", fallback.meta1),

                city2 = json.optStringOrDefault("city2", fallback.city2),
                title2 = json.optStringOrDefault("title2", fallback.title2),
                meta2 = json.optStringOrDefault("meta2", fallback.meta2),

                city3 = json.optStringOrDefault("city3", fallback.city3),
                title3 = json.optStringOrDefault("title3", fallback.title3),
                meta3 = json.optStringOrDefault("meta3", fallback.meta3),

                city4 = json.optStringOrDefault("city4", fallback.city4),
                title4 = json.optStringOrDefault("title4", fallback.title4),
                meta4 = json.optStringOrDefault("meta4", fallback.meta4),

                hint = json.optStringOrDefault("hint", fallback.hint)
            )
        } catch (_: Exception) {
            fallback.copy(
                subtitle = "Ошибка чтения JSON событий",
                hint = "Работаем на резервных данных"
            )
        }
    }

    override fun getForecastState(): ForecastUiState {
        return fallbackDataSource.getForecastState()
    }

    override fun getMapState(): MapUiState {
        return fallbackDataSource.getMapState()
    }

    override fun getAiState(): AiUiState {
        return fallbackDataSource.getAiState()
    }

    private fun JSONObject.optStringOrDefault(
        key: String,
        defaultValue: String
    ): String {
        val value = optString(key, "")
        return if (value.isBlank()) defaultValue else value
    }
}