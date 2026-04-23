package com.rixradar.app

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class AiActivity : AppCompatActivity() {

    private lateinit var tvAiScreenTitle: TextView
    private lateinit var tvAiScreenSubtitle: TextView
    private lateinit var tvAiNowTitle: TextView
    private lateinit var tvAiNowValue: TextView
    private lateinit var tvAiNowMeta: TextView
    private lateinit var tvAiWindowTitle: TextView
    private lateinit var tvAiWindowValue: TextView
    private lateinit var tvAiWindowMeta: TextView
    private lateinit var tvAiRiskTitle: TextView
    private lateinit var tvAiRiskValue: TextView
    private lateinit var tvAiRiskMeta: TextView
    private lateinit var tvAiRoutesTitle: TextView
    private lateinit var tvAiRoutesValue: TextView
    private lateinit var tvAiRoutesMeta: TextView
    private lateinit var tvAiCommentTitle: TextView
    private lateinit var tvAiCommentText: TextView
    private lateinit var tvAiHint: TextView

    private val radarDataSource: RadarDataSource = RadarRepositoryProvider.dataSource

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "ИИ"

        bindViews()
        render(radarDataSource.getAiState())
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun bindViews() {
        tvAiScreenTitle = findViewById(R.id.tvAiScreenTitle)
        tvAiScreenSubtitle = findViewById(R.id.tvAiScreenSubtitle)
        tvAiNowTitle = findViewById(R.id.tvAiNowTitle)
        tvAiNowValue = findViewById(R.id.tvAiNowValue)
        tvAiNowMeta = findViewById(R.id.tvAiNowMeta)
        tvAiWindowTitle = findViewById(R.id.tvAiWindowTitle)
        tvAiWindowValue = findViewById(R.id.tvAiWindowValue)
        tvAiWindowMeta = findViewById(R.id.tvAiWindowMeta)
        tvAiRiskTitle = findViewById(R.id.tvAiRiskTitle)
        tvAiRiskValue = findViewById(R.id.tvAiRiskValue)
        tvAiRiskMeta = findViewById(R.id.tvAiRiskMeta)
        tvAiRoutesTitle = findViewById(R.id.tvAiRoutesTitle)
        tvAiRoutesValue = findViewById(R.id.tvAiRoutesValue)
        tvAiRoutesMeta = findViewById(R.id.tvAiRoutesMeta)
        tvAiCommentTitle = findViewById(R.id.tvAiCommentTitle)
        tvAiCommentText = findViewById(R.id.tvAiCommentText)
        tvAiHint = findViewById(R.id.tvAiHint)
    }

    private fun render(state: AiUiState) {
        tvAiScreenTitle.text = state.title
        tvAiScreenSubtitle.text = state.subtitle
        tvAiNowTitle.text = state.nowTitle
        tvAiNowValue.text = state.nowValue
        tvAiNowMeta.text = state.nowMeta
        tvAiWindowTitle.text = state.windowTitle
        tvAiWindowValue.text = state.windowValue
        tvAiWindowMeta.text = state.windowMeta
        tvAiRiskTitle.text = state.riskTitle
        tvAiRiskValue.text = state.riskValue
        tvAiRiskMeta.text = state.riskMeta
        tvAiRoutesTitle.text = state.routesTitle
        tvAiRoutesValue.text = state.routesValue
        tvAiRoutesMeta.text = state.routesMeta
        tvAiCommentTitle.text = state.commentTitle
        tvAiCommentText.text = state.commentText
        tvAiHint.text = state.hint
    }
}