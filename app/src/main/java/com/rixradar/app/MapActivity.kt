package com.rixradar.app

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MapActivity : AppCompatActivity() {

    private lateinit var tvMapTitle: TextView
    private lateinit var tvMapSubtitle: TextView
    private lateinit var tvMapSchemeTitle: TextView
    private lateinit var tvMapZoneRixTitle: TextView
    private lateinit var tvMapZoneRixValue: TextView
    private lateinit var tvMapZoneRixMeta: TextView
    private lateinit var tvMapZoneRigaTitle: TextView
    private lateinit var tvMapZoneRigaValue: TextView
    private lateinit var tvMapZoneRigaMeta: TextView
    private lateinit var tvMapZoneJurmalaTitle: TextView
    private lateinit var tvMapZoneJurmalaValue: TextView
    private lateinit var tvMapZoneJurmalaMeta: TextView
    private lateinit var tvMapZoneSiguldaTitle: TextView
    private lateinit var tvMapZoneSiguldaValue: TextView
    private lateinit var tvMapZoneSiguldaMeta: TextView
    private lateinit var tvMapZoneOgreTitle: TextView
    private lateinit var tvMapZoneOgreValue: TextView
    private lateinit var tvMapZoneOgreMeta: TextView
    private lateinit var tvMapHint: TextView

    private val radarDataSource: RadarDataSource = RadarRepositoryProvider.dataSource

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Карта"

        bindViews()
        render(radarDataSource.getMapState())
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun bindViews() {
        tvMapTitle = findViewById(R.id.tvMapTitle)
        tvMapSubtitle = findViewById(R.id.tvMapSubtitle)
        tvMapSchemeTitle = findViewById(R.id.tvMapSchemeTitle)
        tvMapZoneRixTitle = findViewById(R.id.tvMapZoneRixTitle)
        tvMapZoneRixValue = findViewById(R.id.tvMapZoneRixValue)
        tvMapZoneRixMeta = findViewById(R.id.tvMapZoneRixMeta)
        tvMapZoneRigaTitle = findViewById(R.id.tvMapZoneRigaTitle)
        tvMapZoneRigaValue = findViewById(R.id.tvMapZoneRigaValue)
        tvMapZoneRigaMeta = findViewById(R.id.tvMapZoneRigaMeta)
        tvMapZoneJurmalaTitle = findViewById(R.id.tvMapZoneJurmalaTitle)
        tvMapZoneJurmalaValue = findViewById(R.id.tvMapZoneJurmalaValue)
        tvMapZoneJurmalaMeta = findViewById(R.id.tvMapZoneJurmalaMeta)
        tvMapZoneSiguldaTitle = findViewById(R.id.tvMapZoneSiguldaTitle)
        tvMapZoneSiguldaValue = findViewById(R.id.tvMapZoneSiguldaValue)
        tvMapZoneSiguldaMeta = findViewById(R.id.tvMapZoneSiguldaMeta)
        tvMapZoneOgreTitle = findViewById(R.id.tvMapZoneOgreTitle)
        tvMapZoneOgreValue = findViewById(R.id.tvMapZoneOgreValue)
        tvMapZoneOgreMeta = findViewById(R.id.tvMapZoneOgreMeta)
        tvMapHint = findViewById(R.id.tvMapHint)
    }

    private fun render(state: MapUiState) {
        tvMapTitle.text = state.title
        tvMapSubtitle.text = state.subtitle
        tvMapSchemeTitle.text = state.schemeTitle
        tvMapZoneRixTitle.text = state.zoneRixTitle
        tvMapZoneRixValue.text = state.zoneRixValue
        tvMapZoneRixMeta.text = state.zoneRixMeta
        tvMapZoneRigaTitle.text = state.zoneRigaTitle
        tvMapZoneRigaValue.text = state.zoneRigaValue
        tvMapZoneRigaMeta.text = state.zoneRigaMeta
        tvMapZoneJurmalaTitle.text = state.zoneJurmalaTitle
        tvMapZoneJurmalaValue.text = state.zoneJurmalaValue
        tvMapZoneJurmalaMeta.text = state.zoneJurmalaMeta
        tvMapZoneSiguldaTitle.text = state.zoneSiguldaTitle
        tvMapZoneSiguldaValue.text = state.zoneSiguldaValue
        tvMapZoneSiguldaMeta.text = state.zoneSiguldaMeta
        tvMapZoneOgreTitle.text = state.zoneOgreTitle
        tvMapZoneOgreValue.text = state.zoneOgreValue
        tvMapZoneOgreMeta.text = state.zoneOgreMeta
        tvMapHint.text = state.hint
    }
}