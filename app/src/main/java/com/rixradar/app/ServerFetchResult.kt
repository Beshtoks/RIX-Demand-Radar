package com.rixradar.app

data class ServerFetchResult(
    val success: Boolean,
    val url: String,
    val rawBody: String?,
    val errorMessage: String?
)