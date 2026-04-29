package com.rixradar.app

data class ForecastUiState(
    val title: String,
    val subtitle: String,
    val nowTitle: String,
    val nowValue: String,
    val nowMeta: String,
    val hour1Title: String,
    val hour1Value: String,
    val hour1Meta: String,
    val hour3Title: String,
    val hour3Value: String,
    val hour3Meta: String,
    val bestWindowTitle: String,
    val bestWindowValue: String,
    val bestWindowMeta: String,
    val hint: String
)