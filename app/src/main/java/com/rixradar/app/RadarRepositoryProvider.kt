package com.rixradar.app

object RadarRepositoryProvider {

    private val CURRENT_MODE: DataMode = DataMode.SERVER

    val currentMode: DataMode
        get() = CURRENT_MODE

    val dataSource: RadarDataSource by lazy {
        when (CURRENT_MODE) {
            DataMode.LOCAL -> LocalRadarRepository()
            DataMode.FAKE_SERVER -> FakeServerRadarRepository()
            DataMode.SERVER -> ServerRadarRepository()
        }
    }
}