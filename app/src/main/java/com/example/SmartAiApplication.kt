package com.example

import android.app.Application
import com.example.ads.AdManager

/**
 * Application entry point for SmartAI App Locker.
 * Initializes Google Mobile Ads SDK once on a background thread during startup
 * while keeping all vault storage 100% offline and local-only.
 */
class SmartAiApplication : Application() {

    val adManager: AdManager by lazy { AdManager.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
        adManager.initializeInBackground()
    }

    override fun onTerminate() {
        adManager.destroyAll()
        super.onTerminate()
    }
}
