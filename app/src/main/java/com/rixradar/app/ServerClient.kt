package com.rixradar.app

import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ServerClient {

    private val executor = Executors.newSingleThreadExecutor()

    fun fetch(path: String): ServerFetchResult {
        val urlString = ServerConfig.buildUrl(path)

        val future = executor.submit(
            Callable {
                var connection: HttpURLConnection? = null

                try {
                    val url = URL(urlString)
                    connection = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 5000
                        readTimeout = 5000
                        setRequestProperty("Accept", "application/json")
                        doInput = true
                    }

                    val responseCode = connection.responseCode
                    val inputStream = if (responseCode in 200..299) {
                        connection.inputStream
                    } else {
                        connection.errorStream
                    }

                    val body = inputStream?.bufferedReader()?.use { it.readText() }

                    if (responseCode in 200..299) {
                        ServerFetchResult(
                            success = true,
                            url = urlString,
                            rawBody = body,
                            errorMessage = null
                        )
                    } else {
                        ServerFetchResult(
                            success = false,
                            url = urlString,
                            rawBody = body,
                            errorMessage = "HTTP $responseCode"
                        )
                    }
                } catch (e: Exception) {
                    ServerFetchResult(
                        success = false,
                        url = urlString,
                        rawBody = null,
                        errorMessage = e.message ?: e.javaClass.simpleName
                    )
                } finally {
                    connection?.disconnect()
                }
            }
        )

        return try {
            future.get(6, TimeUnit.SECONDS)
        } catch (e: Exception) {
            future.cancel(true)
            ServerFetchResult(
                success = false,
                url = urlString,
                rawBody = null,
                errorMessage = e.message ?: e.javaClass.simpleName
            )
        }
    }
}