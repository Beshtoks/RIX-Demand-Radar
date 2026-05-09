package com.rixradar.app

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONObject

class FlightsRefreshWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {

    override fun doWork(): Result {
        val response = ServerClient().fetch("/api/refresh-flights")

        if (!response.success || response.rawBody.isNullOrBlank()) {
            return Result.retry()
        }

        return try {
            val json = JSONObject(response.rawBody)
            val ok = json.optBoolean("ok", false)
            val flights = json.optJSONArray("flights")
            val cacheMode = json.optString("cacheMode", "")
            val cacheAgeSeconds = json.optLong("cacheAgeSeconds", 0L)

            if (
                !ok ||
                flights == null ||
                flights.length() == 0 ||
                cacheMode.startsWith("stale_after") ||
                cacheAgeSeconds >= FlightsCacheConfig.AUTO_REFRESH_MS / 1000L
            ) {
                Result.retry()
            } else {
                ServerRadarRepository.cacheFlightsRawBody(response.rawBody)

                applicationContext
                    .getSharedPreferences(FlightsCacheConfig.PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(FlightsCacheConfig.KEY_JSON, response.rawBody)
                    .putLong(FlightsCacheConfig.KEY_FETCHED_AT, System.currentTimeMillis())
                    .apply()

                Result.success()
            }
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object
}
