package com.rixradar.app.data.network.dto

import com.google.gson.annotations.SerializedName

data class FlightDto(
    @SerializedName("flightNumber")
    val flightNumber: String? = null,

    @SerializedName("city")
    val city: String? = null,

    @SerializedName("scheduledTime")
    val scheduledTime: String? = null,

    @SerializedName("actualTime")
    val actualTime: String? = null,

    @SerializedName("status")
    val status: String? = null
)