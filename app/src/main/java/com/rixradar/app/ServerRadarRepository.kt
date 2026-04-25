package com.rixradar.app

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.roundToInt

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
            val airportDemandLines = buildAirportDemandLines(fallback)

            DashboardUiState(
                cityText = json.optStringOrDefault("cityText", fallback.cityText),
                updatedText = json.optStringOrDefault("updatedText", fallback.updatedText),
                demandValueText = json.optStringOrDefault("demandValueText", fallback.demandValueText),
                demandStatusText = json.optStringOrDefault("demandStatusText", fallback.demandStatusText),
                reason1Text = json.optStringOrDefault("reason1Text", fallback.reason1Text),
                reason2Text = json.optStringOrDefault("reason2Text", fallback.reason2Text),
                reason3Text = json.optStringOrDefault("reason3Text", fallback.reason3Text),
                flight1Text = airportDemandLines.getOrElse(0) { fallback.flight1Text },
                flight2Text = airportDemandLines.getOrElse(1) { fallback.flight2Text },
                flight3Text = airportDemandLines.getOrElse(2) { fallback.flight3Text },
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
        return fallbackDataSource.getEventsState()
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

    private fun buildAirportDemandLines(fallback: DashboardUiState): List<String> {
        val arrivals = fetchArrivalsForDemand()

        if (arrivals.size < MIN_POINTS_FOR_DEMAND) {
            return listOf(
                "🟥 Высокий поток: недостаточно данных",
                "🟨 Средний поток: недостаточно данных",
                "🟩 Низкий поток: недостаточно данных"
            )
        }

        val windows = buildDensityWindows(arrivals.take(MAX_ARRIVALS_FOR_DEMAND))
        if (windows.isEmpty()) {
            return listOf(
                fallback.flight1Text,
                fallback.flight2Text,
                fallback.flight3Text
            )
        }

        val selected = selectDemandWindows(windows)
            .sortedBy { it.window.startAbsoluteMinute }

        if (selected.size < 3) {
            return listOf(
                fallback.flight1Text,
                fallback.flight2Text,
                fallback.flight3Text
            )
        }

        return selected.map { formatDemandWindow(it.level, it.window) }
    }

    private fun selectDemandWindows(windows: List<DensityWindow>): List<LabeledDemandWindow> {
        val high = windows.minByOrNull { it.averageIntervalMinutes } ?: return emptyList()
        val selected = mutableListOf(
            LabeledDemandWindow(DemandLevel.HIGH, high)
        )

        val medianAverage = windows
            .map { it.averageIntervalMinutes }
            .sorted()
            .let { values -> values[values.size / 2] }

        val mediumCandidates = windows
            .filter { !it.overlapsAny(selected.map { selectedItem -> selectedItem.window }) }
            .ifEmpty { windows.filter { it.startAbsoluteMinute != high.startAbsoluteMinute } }

        val medium = mediumCandidates
            .minByOrNull { abs(it.averageIntervalMinutes - medianAverage) }

        if (medium != null) {
            selected.add(LabeledDemandWindow(DemandLevel.MEDIUM, medium))
        }

        val lowCandidates = windows
            .filter { !it.overlapsAny(selected.map { selectedItem -> selectedItem.window }) }
            .ifEmpty {
                windows.filter { candidate ->
                    selected.none { selectedItem -> selectedItem.window.startAbsoluteMinute == candidate.startAbsoluteMinute }
                }
            }

        val low = lowCandidates.maxByOrNull { it.averageIntervalMinutes }
        if (low != null) {
            selected.add(LabeledDemandWindow(DemandLevel.LOW, low))
        }

        if (selected.size >= 3) {
            return selected.take(3)
        }

        for (candidate in windows.sortedBy { it.startAbsoluteMinute }) {
            if (selected.size >= 3) break
            if (selected.none { it.window.startAbsoluteMinute == candidate.startAbsoluteMinute }) {
                selected.add(LabeledDemandWindow(classifyDemandLevel(candidate), candidate))
            }
        }

        return selected.take(3)
    }

    private fun fetchArrivalsForDemand(): List<ArrivalPoint> {
        val nowMillis = System.currentTimeMillis()
        val cachedRaw = cachedFlightsRawBody

        if (!cachedRaw.isNullOrBlank() && nowMillis - cachedFlightsSavedAtMillis < AIRPORT_FLOW_CACHE_MS) {
            val parsed = tryParseArrivals(cachedRaw)
            if (parsed.isNotEmpty()) {
                return normalizeArrivalOrder(parsed).take(MAX_ARRIVALS_FOR_DEMAND)
            }
        }

        val responses = listOf(
            serverClient.fetch("/api/real-flights"),
            serverClient.fetch(ServerConfig.FLIGHTS_PATH)
        )

        for (response in responses) {
            if (!response.success || response.rawBody.isNullOrBlank()) continue

            val parsed = tryParseArrivals(response.rawBody)
            if (parsed.isNotEmpty()) {
                cacheFlightsRawBody(response.rawBody)
                return normalizeArrivalOrder(parsed).take(MAX_ARRIVALS_FOR_DEMAND)
            }
        }

        return emptyList()
    }

    private fun tryParseArrivals(rawBody: String): List<ArrivalPoint> {
        return try {
            val json = JSONObject(rawBody)
            val directArray = json.optJSONArray("flights")
            if (directArray != null && directArray.length() > 0) {
                return parseFlightsArray(directArray)
            }

            parseCompactFlights(json)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseFlightsArray(array: JSONArray): List<ArrivalPoint> {
        val result = mutableListOf<ArrivalPoint>()

        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val time = item.optString("actualTime").ifBlank {
                item.optString("scheduledTime")
            }
            val minutes = parseTimeToMinutes(time) ?: continue

            result.add(
                ArrivalPoint(
                    timeText = time,
                    minuteOfDay = minutes
                )
            )
        }

        return result
    }

    private fun parseCompactFlights(json: JSONObject): List<ArrivalPoint> {
        val result = mutableListOf<ArrivalPoint>()

        for (i in 1..4) {
            val timeLine = json.optString("time$i", "")
            val statusLine = json.optString("status$i", "")
            val raw = "$timeLine $statusLine"
            val times = TIME_PATTERN.findAll(raw).map { it.value }.toList()
            val selected = times.lastOrNull() ?: times.firstOrNull() ?: continue
            val minutes = parseTimeToMinutes(selected) ?: continue

            result.add(
                ArrivalPoint(
                    timeText = selected,
                    minuteOfDay = minutes
                )
            )
        }

        return result
    }

    private fun normalizeArrivalOrder(points: List<ArrivalPoint>): List<ArrivalPoint> {
        if (points.isEmpty()) return emptyList()

        val normalized = mutableListOf<ArrivalPoint>()
        var dayOffset = 0
        var previous = points.first().minuteOfDay

        for ((index, point) in points.withIndex()) {
            if (index > 0 && point.minuteOfDay + dayOffset < previous - 720) {
                dayOffset += 1440
            }

            val absolute = point.minuteOfDay + dayOffset
            normalized.add(point.copy(absoluteMinute = absolute))
            previous = absolute
        }

        return normalized.sortedBy { it.absoluteMinute }
    }

    private fun buildDensityWindows(points: List<ArrivalPoint>): List<DensityWindow> {
        val windows = mutableListOf<DensityWindow>()

        for (size in MIN_WINDOW_SIZE..MAX_WINDOW_SIZE) {
            if (points.size < size) continue

            for (startIndex in 0..(points.size - size)) {
                val group = points.subList(startIndex, startIndex + size)
                val first = group.first()
                val last = group.last()
                val duration = (last.absoluteMinute - first.absoluteMinute).coerceAtLeast(1)
                val average = duration.toDouble() / (group.size - 1).coerceAtLeast(1)

                windows.add(
                    DensityWindow(
                        startTime = first.timeText,
                        endTime = last.timeText,
                        arrivalsCount = group.size,
                        averageIntervalMinutes = average,
                        startAbsoluteMinute = first.absoluteMinute,
                        endAbsoluteMinute = last.absoluteMinute
                    )
                )
            }
        }

        return windows
    }

    private fun formatDemandWindow(level: DemandLevel, window: DensityWindow): String {
        val average = window.averageIntervalMinutes.roundToInt().coerceAtLeast(1)
        return "${level.square} ${window.startTime}–${window.endTime} • ${level.label} • ${window.arrivalsCount} прилётов • интервал $average мин"
    }

    private fun classifyDemandLevel(window: DensityWindow): DemandLevel {
        return when {
            window.arrivalsCount >= 6 && window.averageIntervalMinutes <= 8.0 -> DemandLevel.HIGH
            window.arrivalsCount >= 5 && window.averageIntervalMinutes <= 12.0 -> DemandLevel.MEDIUM
            else -> DemandLevel.LOW
        }
    }

    private fun DensityWindow.overlapsAny(otherWindows: List<DensityWindow>): Boolean {
        return otherWindows.any { other ->
            startAbsoluteMinute <= other.endAbsoluteMinute && endAbsoluteMinute >= other.startAbsoluteMinute
        }
    }

    private fun parseTimeToMinutes(value: String): Int? {
        val match = TIME_PATTERN.find(value) ?: return null
        val parts = match.value.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: return null

        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }

    private fun JSONObject.optStringOrDefault(
        key: String,
        defaultValue: String
    ): String {
        val value = optString(key, "")
        return if (value.isBlank()) defaultValue else value
    }

    private data class ArrivalPoint(
        val timeText: String,
        val minuteOfDay: Int,
        val absoluteMinute: Int = minuteOfDay
    )

    private data class DensityWindow(
        val startTime: String,
        val endTime: String,
        val arrivalsCount: Int,
        val averageIntervalMinutes: Double,
        val startAbsoluteMinute: Int,
        val endAbsoluteMinute: Int
    )

    private data class LabeledDemandWindow(
        val level: DemandLevel,
        val window: DensityWindow
    )

    private enum class DemandLevel(
        val label: String,
        val square: String
    ) {
        HIGH("Высокий поток", "🟥"),
        MEDIUM("Средний поток", "🟨"),
        LOW("Низкий поток", "🟩")
    }

    companion object {
        private const val AIRPORT_FLOW_CACHE_MS = 60L * 60L * 1000L
        private const val MAX_ARRIVALS_FOR_DEMAND = 30
        private const val MIN_POINTS_FOR_DEMAND = 5
        private const val MIN_WINDOW_SIZE = 5
        private const val MAX_WINDOW_SIZE = 8
        private val TIME_PATTERN = Regex("\\b\\d{1,2}:\\d{2}\\b")

        private var cachedFlightsRawBody: String? = null
        private var cachedFlightsSavedAtMillis: Long = 0L

        fun cacheFlightsRawBody(rawBody: String) {
            if (rawBody.isBlank()) return
            cachedFlightsRawBody = rawBody
            cachedFlightsSavedAtMillis = System.currentTimeMillis()
        }
    }
}
