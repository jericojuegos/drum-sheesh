package com.drumsheesh.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.drumsheesh.feature.DrumSheeshRootHost
import com.drumsheesh.ui.theme.DrumSheeshTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as DrumSheeshApplication).appContainer
        lifecycle.addObserver(container.drumAudioAdapter)

        enableEdgeToEdge()
        setContent {
            DrumSheeshTheme(darkTheme = true) {
                DrumSheeshRootHost(
                    drumAudioPort = container.drumAudioAdapter,
                    clockPort = container.clockPort,
                    aiGrooveGeneratorPort = container.aiGrooveGeneratorPort,
                    grooveRepositoryPort = container.grooveRepositoryPort
                )
            }
        }
    }
}
