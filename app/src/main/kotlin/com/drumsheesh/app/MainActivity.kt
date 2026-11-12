package com.drumsheesh.app

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.drumsheesh.feature.DrumSheeshRootHost
import com.drumsheesh.ui.theme.DrumSheeshTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as DrumSheeshApplication).appContainer
        lifecycle.addObserver(container.drumAudioAdapter)

        enableEdgeToEdge()
        configureImmersiveStudioStage()

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

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            configureImmersiveStudioStage()
        }
    }

    private fun configureImmersiveStudioStage() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }
}
