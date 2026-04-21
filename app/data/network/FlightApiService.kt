package com.rixradar.app.data.network

import com.rixradar.app.data.network.dto.FlightDto
import retrofit2.http.GET

interface FlightApiService {

    @GET("/")
    suspend fun getFlightsPlaceholder(): List<FlightDto>
}