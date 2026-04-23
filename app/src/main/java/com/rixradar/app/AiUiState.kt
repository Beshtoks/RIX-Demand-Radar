package com.rixradar.app

data class AiUiState(
    val title: String,
    val subtitle: String,
    val nowTitle: String,
    val nowValue: String,
    val nowMeta: String,
    val windowTitle: String,
    val windowValue: String,
    val windowMeta: String,
    val riskTitle: String,
    val riskValue: String,
    val riskMeta: String,
    val routesTitle: String,
    val routesValue: String,
    val routesMeta: String,
    val commentTitle: String,
    val commentText: String,
    val hint: String
)