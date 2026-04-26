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
                cacheAgeSeconds >= MAX_ACCEPTED_CACHE_AGE_SECONDS
            ) {
                Result.retry()
            } else {
                ServerRadarRepository.cacheFlightsRawBody(response.rawBody)

                applicationContext
                    .getSharedPreferences(FLIGHTS_CACHE_PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_FLIGHTS_JSON, response.rawBody)
                    .putLong(KEY_FLIGHTS_FETCHED_AT, System.currentTimeMillis())
                    .apply()

                Result.success()
            }
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val FLIGHTS_CACHE_PREFS = "rix_flights_cache"
        private const val KEY_FLIGHTS_JSON = "flights_json"
        private const val KEY_FLIGHTS_FETCHED_AT = "flights_fetched_at"
        private const val MAX_ACCEPTED_CACHE_AGE_SECONDS = 60L * 60L
    }
}
