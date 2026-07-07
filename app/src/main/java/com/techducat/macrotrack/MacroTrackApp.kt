package com.techducat.macrotrack

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

/**
 * MacroTrackApp
 *
 * Deliberately minimal compared to BuzzrApp: no I2P router bootstrap, no
 * crash-reporting SDK init (Bugfender was removed — MacroTrack sends no
 * telemetry anywhere). Timber is planted in debug only for local logcat use.
 */
@HiltAndroidApp
class MacroTrackApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
