package com.techducat.macrotrack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.techducat.macrotrack.ui.nav.MacroTrackNavHost
import com.techducat.macrotrack.ui.theme.MacroTrackTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * MainActivity — MacroTrack
 *
 * Unlike buzzr-p2p's MainActivity (a 500+ line legacy-view Activity juggling
 * I2P client callbacks, location updates, and phone-number dialogs), this is a
 * thin Compose host. All state lives in per-screen Hilt ViewModels; navigation
 * lives in MacroTrackNavHost.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MacroTrackTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MacroTrackNavHost()
                }
            }
        }
    }
}
