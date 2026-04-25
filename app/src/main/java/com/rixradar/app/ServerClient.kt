package com.rixradar.app

import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

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
                        connectTimeout = 20_000
                        readTimeout = 120_000
                        setRequestProperty("Accept", "application/json")
                        setRequestProperty("User-Agent", "RIXDemandRadar-Android/1.0")
                        setRequestProperty("Cache-Control", "no-cache")
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
                            errorMessage = "HTTP $responseCode from $urlString"
                        )
                    }
                } catch (e: SocketTimeoutException) {
                    ServerFetchResult(
                        success = false,
                        url = urlString,
                        rawBody = null,
                        errorMessage = "Timeout while waiting for server response: $urlString"
                    )
                } catch (e: Exception) {
                    ServerFetchResult(
                        success = false,
                        url = urlString,
                        rawBody = null,
                        errorMessage = (e.message ?: e.javaClass.simpleName) + " URL: $urlString"
                    )
                } finally {
                    connection?.disconnect()
                }
            }
        )

        return try {
            future.get(150, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            future.cancel(true)
            ServerFetchResult(
                success = false,
                url = urlString,
                rawBody = null,
                errorMessage = "Client timeout while waiting for backend: $urlString"
            )
        } catch (e: Exception) {
            future.cancel(true)
            ServerFetchResult(
                success = false,
                url = urlString,
                rawBody = null,
                errorMessage = (e.message ?: e.javaClass.simpleName) + " URL: $urlString"
            )
        }
    }
}
