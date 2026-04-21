package com.rixradar.app.data.model

data class FlightItem(
    val flightNumber: String,
    val city: String,
    val scheduledTime: String,
    val actualTime: String?,
    val status: String
)