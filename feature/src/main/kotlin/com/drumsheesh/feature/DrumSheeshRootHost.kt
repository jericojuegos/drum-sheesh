package com.drumsheesh.feature

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.drumsheesh.core.ai.AiGrooveGeneratorPort
import com.drumsheesh.core.ai.GrooveRepositoryPort
import com.drumsheesh.core.ai.ProceduralGrooveGenerator
import com.drumsheesh.core.coach.GroovePattern as CoachGroovePattern
import com.drumsheesh.core.coach.GrooveStep as CoachGrooveStep
import com.drumsheesh.core.model.DrumKitId
import com.drumsheesh.core.model.DrumPadId
import com.drumsheesh.core.port.ClockPort
import com.drumsheesh.core.port.DrumAudioPort
import com.drumsheesh.feature.highway.GrooveHighwayOverlayPanel
import com.drumsheesh.feature.highway.GrooveHighwayUiEvent
import com.drumsheesh.feature.highway.GrooveHighwayViewModel
import com.drumsheesh.feature.recorder.AiGroovePromptModal
import com.drumsheesh.feature.recorder.AiGrooveRecorderUiEvent
import com.drumsheesh.feature.recorder.AiGrooveRecorderViewModel
import com.drumsheesh.feature.recorder.InMemoryGrooveRepositoryAdapter
import com.drumsheesh.feature.recorder.SessionLoopDrawer
import com.drumsheesh.feature.stage.DrumStageScreen
import com.drumsheesh.feature.stage.DrumStageUiEvent
import com.drumsheesh.feature.stage.DrumStageViewModel
import com.drumsheesh.ui.theme.StudioTokens

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
    aiGrooveGeneratorPort: AiGrooveGeneratorPort = ProceduralGrooveGenerator,
    grooveRepositoryPort: GrooveRepositoryPort = InMemoryGrooveRepositoryAdapter(),
    modifier: Modifier = Modifier
) {
    val highwayViewModel: GrooveHighwayViewModel = viewModel(
        factory = GrooveHighwayViewModel.provideFactory(clockPort = clockPort)
    )

    val recorderViewModel: AiGrooveRecorderViewModel = viewModel(
        factory = AiGrooveRecorderViewModel.provideFactory(
            aiGrooveGeneratorPort = aiGrooveGeneratorPort,
            grooveRepositoryPort = grooveRepositoryPort,
            drumAudioPort = drumAudioPort,
            clockPort = clockPort,
            onGrooveLoadedToHighway = { aiPattern ->
                val mappedCoachPattern = CoachGroovePattern(
                    id = aiPattern.id,
                    title = aiPattern.title,
                    genre = aiPattern.source.displayBadge,
                    bpm = aiPattern.bpm,
                    swingPercent = aiPattern.swingPercent,
                    stepsPerBar = aiPattern.stepsPerBar,
                    bars = aiPattern.bars,
                    kitId = aiPattern.kitId,
                    steps = aiPattern.steps.map { step ->
                        CoachGrooveStep(
                            stepIndex = step.stepIndex,
                            padId = step.padId,
                            velocity = step.velocity
                        )
                    }
                )
                highwayViewModel.onEvent(GrooveHighwayUiEvent.LoadCustomGroove(mappedCoachPattern))
            }
        )
    )

    val stageViewModel: DrumStageViewModel = viewModel(
        factory = DrumStageViewModel.provideFactory(
            drumAudioPort = drumAudioPort,
            clockPort = clockPort,
            onPadHitListener = { hitResult, nowMs ->
                highwayViewModel.onEvent(
                    GrooveHighwayUiEvent.RecordUserPadHit(
                        padId = hitResult.padId,
                        velocity = hitResult.velocity,
                        hitTimeMs = nowMs
                    )
                )
                recorderViewModel.onEvent(
                    AiGrooveRecorderUiEvent.RecordStageHit(
                        padId = hitResult.padId,
                        velocity = hitResult.velocity,
                        timestampMs = nowMs
                    )
                )
            }
        )
    )

    val recorderState by recorderViewModel.uiState.collectAsState()

    DrumStageScreen(
        viewModel = stageViewModel,
        clockPort = clockPort,
        topOverlayContent = {
            GrooveHighwayOverlayPanel(
                viewModel = highwayViewModel,
                clockPort = clockPort
            )
        },
        bottomOverlayContent = {
            if (recorderState.isLoopDrawerOpen || recorderState.isRecording) {
                SessionLoopDrawer(
                    uiState = recorderState,
                    onEvent = recorderViewModel::onEvent,
                    modifier = Modifier.padding(
                        horizontal = StudioTokens.SpaceCompact,
                        vertical = StudioTokens.SpaceMicro
                    )
                )
            }
        },
        onOpenAiModal = {
            recorderViewModel.onEvent(AiGrooveRecorderUiEvent.SetAiModalOpen(true))
        },
        onToggleLoopDrawer = {
            recorderViewModel.onEvent(
                AiGrooveRecorderUiEvent.SetLoopDrawerOpen(!recorderState.isLoopDrawerOpen)
            )
        },
        isLoopDrawerOpen = recorderState.isLoopDrawerOpen,
        isRecordingLoop = recorderState.isRecording,
        modifier = modifier
    )

    AiGroovePromptModal(
        uiState = recorderState,
        onEvent = recorderViewModel::onEvent
    )
}
