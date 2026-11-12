package com.drumsheesh.feature.stage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.drumsheesh.core.engine.PadHitResolver
import com.drumsheesh.core.engine.PadHitResult
import com.drumsheesh.core.engine.PadHitZone
import com.drumsheesh.core.model.DrumKitId
import com.drumsheesh.core.model.DrumPadId
import com.drumsheesh.core.port.ClockPort
import com.drumsheesh.core.port.DrumAudioPort
import com.drumsheesh.ui.components.MultiTouchDrumStageCanvas
import com.drumsheesh.ui.components.PadHitVisualState
import com.drumsheesh.ui.components.StudioTopBar
import com.drumsheesh.ui.theme.StudioColors
import com.drumsheesh.ui.theme.StudioTokens
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.roundToInt

/**
 * Active pad hit ripple tracked in [DrumStageUiState] with timestamp for decay calculation.
 */
data class ActivePadRipple(
    val padId: DrumPadId,
    val touchXNorm: Float,
    val touchYNorm: Float,
    val velocity: Float,
    val triggeredAtMs: Long,
    val progress: Float = 0f
)

/**
 * Immutable UDF UI State for the 10-pad Multi-Touch Drum Stage (`01-drum-kit-stage`).
 */
data class DrumStageUiState(
    val selectedKit: DrumKitId = DrumKitId.ACOUSTIC_STUDIO,
    val isLeftHanded: Boolean = false,
    val bpm: Int = DrumKitId.ACOUSTIC_STUDIO.defaultBpm,
    val padZones: List<PadHitZone> = PadHitResolver.getZonesForHandedness(isLeftHanded = false),
    val activeRipples: List<ActivePadRipple> = emptyList(),
    val lastTriggeredPad: DrumPadId? = null,
    val lastVelocity: Float? = null,
    val totalHitsInSession: Int = 0,
    val openHiHatRinging: Boolean = false,
    val lastChokeAtMs: Long? = null,
    val isChokeFlashActive: Boolean = false
)

/**
 * Discrete UDF UI Events emitted by [DrumStageScreen].
 */
sealed interface DrumStageUiEvent {
    data class PointerTouchDown(val xNorm: Float, val yNorm: Float) : DrumStageUiEvent
    data class DirectPadTrigger(val padId: DrumPadId, val velocity: Float = 0.85f) : DrumStageUiEvent
    data class SelectKit(val kitId: DrumKitId) : DrumStageUiEvent
    data object ToggleHandedness : DrumStageUiEvent
    data class AdjustBpm(val delta: Int) : DrumStageUiEvent
    data class StepAnimationFrame(val frameTimeMs: Long) : DrumStageUiEvent
}

/**
 * UDF ViewModel for the Multi-Touch Drum Stage (`01-drum-kit-stage`).
 *
 * Resolves pointer-down hits via [PadHitResolver], triggers low-latency audio via [DrumAudioPort],
 * tracks open-hi-hat choke state, and manages visual hit ripples.
 */
class DrumStageViewModel(
    private val drumAudioPort: DrumAudioPort,
    private val clockPort: ClockPort = ClockPort.System,
    private val onPadHitListener: ((PadHitResult, Long) -> Unit)? = null
) : ViewModel() {

    companion object {
        const val MIN_BPM: Int = 40
        const val MAX_BPM: Int = 240
        const val CHOKE_FLASH_DURATION_MS: Long = 220L

        fun provideFactory(
            drumAudioPort: DrumAudioPort,
            clockPort: ClockPort = ClockPort.System,
            onPadHitListener: ((PadHitResult, Long) -> Unit)? = null
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return DrumStageViewModel(drumAudioPort, clockPort, onPadHitListener) as T
            }
        }
    }

    private val _uiState = MutableStateFlow(DrumStageUiState())
    val uiState: StateFlow<DrumStageUiState> = _uiState.asStateFlow()

    init {
        drumAudioPort.preloadKit(_uiState.value.selectedKit)
    }

    fun onEvent(event: DrumStageUiEvent) {
        when (event) {
            is DrumStageUiEvent.PointerTouchDown -> handlePointerTouchDown(event.xNorm, event.yNorm)
            is DrumStageUiEvent.DirectPadTrigger -> handleDirectPadTrigger(event.padId, event.velocity)
            is DrumStageUiEvent.SelectKit -> handleSelectKit(event.kitId)
            is DrumStageUiEvent.ToggleHandedness -> handleToggleHandedness()
            is DrumStageUiEvent.AdjustBpm -> handleAdjustBpm(event.delta)
            is DrumStageUiEvent.StepAnimationFrame -> handleStepAnimationFrame(event.frameTimeMs)
        }
    }

    private fun handlePointerTouchDown(xNorm: Float, yNorm: Float) {
        val currentState = _uiState.value
        val hitResult = PadHitResolver.resolveHit(
            xNorm = xNorm,
            yNorm = yNorm,
            isLeftHanded = currentState.isLeftHanded
        ) ?: return

        dispatchResolvedHit(hitResult)
    }

    private fun handleDirectPadTrigger(padId: DrumPadId, velocity: Float) {
        val currentState = _uiState.value
        val zone = currentState.padZones.firstOrNull { it.padId == padId }
        val hitResult = PadHitResult(
            padId = padId,
            velocity = velocity.coerceIn(
                PadHitResolver.MIN_RADIAL_VELOCITY,
                PadHitResolver.MAX_RADIAL_VELOCITY
            ),
            normalizedX = zone?.centerX ?: 0.5f,
            normalizedY = zone?.centerY ?: 0.5f,
            isChokeTrigger = padId.chokesOpenHiHat
        )
        dispatchResolvedHit(hitResult)
    }

    private fun dispatchResolvedHit(hitResult: PadHitResult) {
        val nowMs = clockPort.currentTimeMillis()
        val currentKit = _uiState.value.selectedKit

        // Trigger low-latency audio immediately on pointer-down
        drumAudioPort.triggerPad(
            kitId = currentKit,
            padId = hitResult.padId,
            velocity = hitResult.velocity
        )

        onPadHitListener?.invoke(hitResult, nowMs)

        _uiState.update { state ->
            val didChokeOpenHat = hitResult.isChokeTrigger && state.openHiHatRinging
            val isNowOpenHatRinging = when {
                hitResult.padId == DrumPadId.HI_HAT_OPEN -> true
                hitResult.isChokeTrigger -> false
                else -> state.openHiHatRinging
            }

            val updatedRipples = state.activeRipples
                .filterNot { it.padId == hitResult.padId } +
                ActivePadRipple(
                    padId = hitResult.padId,
                    touchXNorm = hitResult.normalizedX,
                    touchYNorm = hitResult.normalizedY,
                    velocity = hitResult.velocity,
                    triggeredAtMs = nowMs,
                    progress = 0f
                )

            state.copy(
                activeRipples = updatedRipples,
                lastTriggeredPad = hitResult.padId,
                lastVelocity = hitResult.velocity,
                totalHitsInSession = state.totalHitsInSession + 1,
                openHiHatRinging = isNowOpenHatRinging,
                lastChokeAtMs = if (didChokeOpenHat) nowMs else state.lastChokeAtMs,
                isChokeFlashActive = didChokeOpenHat || state.isChokeFlashActive
            )
        }
    }

    private fun handleSelectKit(kitId: DrumKitId) {
        drumAudioPort.preloadKit(kitId)
        _uiState.update { state ->
            state.copy(
                selectedKit = kitId,
                bpm = kitId.defaultBpm
            )
        }
    }

    private fun handleToggleHandedness() {
        _uiState.update { state ->
            val nextLeftHanded = !state.isLeftHanded
            state.copy(
                isLeftHanded = nextLeftHanded,
                padZones = PadHitResolver.getZonesForHandedness(isLeftHanded = nextLeftHanded),
                activeRipples = emptyList()
            )
        }
    }

    private fun handleAdjustBpm(delta: Int) {
        _uiState.update { state ->
            state.copy(bpm = (state.bpm + delta).coerceIn(MIN_BPM, MAX_BPM))
        }
    }

    private fun handleStepAnimationFrame(frameTimeMs: Long) {
        _uiState.update { state ->
            if (state.activeRipples.isEmpty() && !state.isChokeFlashActive) {
                return@update state
            }

            val decayedRipples = state.activeRipples.mapNotNull { ripple ->
                val elapsed = (frameTimeMs - ripple.triggeredAtMs).coerceAtLeast(0L)
                val progress = elapsed.toFloat() / StudioTokens.PAD_IMPULSE_DECAY_MS.toFloat()
                if (progress >= 1.0f) null else ripple.copy(progress = progress)
            }

            val chokeStillActive = state.lastChokeAtMs?.let { chokeAt ->
                (frameTimeMs - chokeAt) in 0L..CHOKE_FLASH_DURATION_MS
            } ?: false

            state.copy(
                activeRipples = decayedRipples,
                isChokeFlashActive = chokeStillActive
            )
        }
    }
}

/**
 * Full-screen landscape Drum Stage Composable (`DrumStageScreen`).
 */
@Composable
fun DrumStageScreen(
    viewModel: DrumStageViewModel,
    clockPort: ClockPort = ClockPort.System,
    topOverlayContent: (@Composable () -> Unit)? = null,
    bottomOverlayContent: (@Composable () -> Unit)? = null,
    onOpenAiModal: (() -> Unit)? = null,
    onToggleLoopDrawer: (() -> Unit)? = null,
    isLoopDrawerOpen: Boolean = false,
    isRecordingLoop: Boolean = false,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    // Drive smooth decay of active pad hit ripples only while ripples or choke flash are active
    LaunchedEffect(uiState.activeRipples.isNotEmpty(), uiState.isChokeFlashActive) {
        while (uiState.activeRipples.isNotEmpty() || uiState.isChokeFlashActive) {
            withFrameMillis {
                viewModel.onEvent(DrumStageUiEvent.StepAnimationFrame(clockPort.currentTimeMillis()))
            }
        }
    }

    val visualHits = uiState.activeRipples.map { ripple ->
        PadHitVisualState(
            padId = ripple.padId,
            touchXNorm = ripple.touchXNorm,
            touchYNorm = ripple.touchYNorm,
            velocity = ripple.velocity,
            progress = ripple.progress
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(StudioColors.StageBackgroundDark)
            .displayCutoutPadding()
    ) {
        StudioTopBar(
            selectedKit = uiState.selectedKit,
            isLeftHanded = uiState.isLeftHanded,
            bpm = uiState.bpm,
            lastVelocityPercent = uiState.lastVelocity?.let { (it * 100f).roundToInt() },
            isChokeFlashActive = uiState.isChokeFlashActive,
            onSelectKit = { kit -> viewModel.onEvent(DrumStageUiEvent.SelectKit(kit)) },
            onToggleHandedness = { viewModel.onEvent(DrumStageUiEvent.ToggleHandedness) },
            onAdjustBpm = { delta -> viewModel.onEvent(DrumStageUiEvent.AdjustBpm(delta)) },
            onOpenAiModal = onOpenAiModal,
            onToggleLoopDrawer = onToggleLoopDrawer,
            isLoopDrawerOpen = isLoopDrawerOpen,
            isRecordingLoop = isRecordingLoop
        )

        topOverlayContent?.invoke()

        MultiTouchDrumStageCanvas(
            padZones = uiState.padZones,
            selectedKit = uiState.selectedKit,
            isLeftHanded = uiState.isLeftHanded,
            activeHits = visualHits,
            isChokeFlashActive = uiState.isChokeFlashActive,
            onPointerTouchHit = { xNorm, yNorm ->
                viewModel.onEvent(DrumStageUiEvent.PointerTouchDown(xNorm, yNorm))
            },
            modifier = Modifier.weight(1f)
        )

        bottomOverlayContent?.invoke()
    }
}
