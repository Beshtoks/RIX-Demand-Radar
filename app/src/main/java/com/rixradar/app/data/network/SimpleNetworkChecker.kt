package com.rixradar.app.network

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class SimpleNetworkChecker {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    fun checkConnection(): String {
        val request = Request.Builder()
            .url("https://www.google.com")
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    "Сеть работает: HTTP ${response.code}"
                } else {
                    "Сеть ответила с ошибкой: HTTP ${response.code}"
                }
            }
        } catch (e: IOException) {
            "Ошибка сети: ${e.message ?: "неизвестная ошибка"}"
        } catch (e: Exception) {
            "Ошибка: ${e.message ?: "неизвестная ошибка"}"
        }
    }
}