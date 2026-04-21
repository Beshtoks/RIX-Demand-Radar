package com.rixradar.app.data.repository

import com.rixradar.app.data.model.FlightItem
import com.rixradar.app.data.network.NetworkModule
import com.rixradar.app.data.network.dto.FlightDto

class FlightRepository {

    private val api = NetworkModule.flightApiService

    fun isNetworkLayerReady(): Boolean {
        return api != null
    }

    fun mapDtoToItem(dto: FlightDto): FlightItem {
        return FlightItem(
            flightNumber = dto.flightNumber.orEmpty(),
            city = dto.city.orEmpty(),
            scheduledTime = dto.scheduledTime.orEmpty(),
            actualTime = dto.actualTime,
            status = dto.status.orEmpty()
        )
    }
}