package com.rixradar.app

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class FlightsWebActivity : AppCompatActivity() {

    private lateinit var tvFlightsWebStatus: TextView
    private lateinit var webViewFlights: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_flights_web)

        tvFlightsWebStatus = findViewById(R.id.tvFlightsWebStatus)
        webViewFlights = findViewById(R.id.webViewFlights)

        setupWebView()
        webViewFlights.loadUrl(RIX_FLIGHT_SCHEDULE_URL)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val settings: WebSettings = webViewFlights.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.loadsImagesAutomatically = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.builtInZoomControls = true
        settings.displayZoomControls = false

        webViewFlights.webChromeClient = WebChromeClient()
        webViewFlights.webViewClient = object : WebViewClient() {

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                tvFlightsWebStatus.text = getString(R.string.flights_web_loading)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                tvFlightsWebStatus.text = url ?: ""
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    tvFlightsWebStatus.text = getString(R.string.flights_web_error)
                }
            }
        }
    }

    override fun onBackPressed() {
        if (webViewFlights.canGoBack()) {
            webViewFlights.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        webViewFlights.destroy()
        super.onDestroy()
    }

    companion object {
        private const val RIX_FLIGHT_SCHEDULE_URL = "https://www.riga-airport.com/en/flight-schedule"
    }
}