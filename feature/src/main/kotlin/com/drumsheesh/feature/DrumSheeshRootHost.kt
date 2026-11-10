package com.drumsheesh.feature

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.drumsheesh.core.model.DrumKitId
import com.drumsheesh.core.model.DrumPadId
import com.drumsheesh.core.port.ClockPort
import com.drumsheesh.core.port.DrumAudioPort
import com.drumsheesh.feature.stage.DrumStageScreen
import com.drumsheesh.feature.stage.DrumStageViewModel

private object NoOpDrumAudioPort : DrumAudioPort {
    override fun preloadKit(kitId: DrumKitId) = Unit
    override fun triggerPad(kitId: DrumKitId, padId: DrumPadId, velocity: Float) = Unit
    override fun stopAllStreams() = Unit
    override fun release() = Unit
}

@Composable
fun DrumSheeshRootHost(
    drumAudioPort: DrumAudioPort = NoOpDrumAudioPort,
    clockPort: ClockPort = ClockPort.System,
    modifier: Modifier = Modifier
) {
    val stageViewModel: DrumStageViewModel = viewModel(
        factory = DrumStageViewModel.provideFactory(
            drumAudioPort = drumAudioPort,
            clockPort = clockPort
        )
    )

    DrumStageScreen(
        viewModel = stageViewModel,
        clockPort = clockPort,
        modifier = modifier
    )
}
