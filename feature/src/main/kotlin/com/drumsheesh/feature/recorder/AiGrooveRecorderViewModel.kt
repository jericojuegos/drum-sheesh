package com.drumsheesh.feature.recorder

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.drumsheesh.core.ai.AiGrooveGeneratorPort
import com.drumsheesh.core.ai.AiGroovePromptBuilder
import com.drumsheesh.core.ai.AiGrooveRequest
import com.drumsheesh.core.ai.GroovePattern
import com.drumsheesh.core.ai.GrooveRepositoryPort
import com.drumsheesh.core.ai.GrooveSource
import com.drumsheesh.core.ai.LoopQuantizer
import com.drumsheesh.core.ai.ProceduralGrooveGenerator
import com.drumsheesh.core.ai.RecordedLoop
import com.drumsheesh.core.ai.SessionRecorderEngine
import com.drumsheesh.core.model.DrumKitId
import com.drumsheesh.core.model.DrumPadId
import com.drumsheesh.core.port.ClockPort
import com.drumsheesh.core.port.DrumAudioPort
import com.drumsheesh.ui.theme.StudioColors
import com.drumsheesh.ui.theme.StudioTokens
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Default in-memory implementation of [GrooveRepositoryPort] for previews and isolated ViewModel instances.
 */
class InMemoryGrooveRepositoryAdapter : GrooveRepositoryPort {
    private val savedGrooves = LinkedHashMap<String, GroovePattern>()
    private val savedLoops = LinkedHashMap<String, RecordedLoop>()

    override suspend fun saveGroove(pattern: GroovePattern) {
        savedGrooves[pattern.id] = pattern
    }

    override suspend fun loadSavedGrooves(): List<GroovePattern> =
        savedGrooves.values.toList().reversed()

    override suspend fun saveRecordedLoop(loop: RecordedLoop) {
        savedLoops[loop.id] = loop
    }

    override suspend fun loadRecordedLoops(): List<RecordedLoop> =
        savedLoops.values.toList().reversed()
}

/**
 * Immutable UDF state for AI Text-to-Groove generation and live Session Loop Recording (`03-ai-groove-recorder`).
 */
data class AiGrooveRecorderUiState(
    val isAiModalOpen: Boolean = false,
    val isLoopDrawerOpen: Boolean = false,
    val promptText: String = "94 BPM J Dilla neo-soul boom bap with swung hi-hats and ghost snares",
    val promptValidationError: String? = null,
    val isGeneratingGroove: Boolean = false,
    val selectedTargetKit: DrumKitId = DrumKitId.VINTAGE_BREAKBEAT,
    val currentBpm: Int = 94,
    val currentSwingPercent: Int = 24,
    val generatedGroovePreview: GroovePattern? = null,
    val activeLoadedGroove: GroovePattern? = null,
    val savedGrooves: List<GroovePattern> = emptyList(),
    val isRecording: Boolean = false,
    val recordingElapsedMs: Long = 0L,
    val recordedHitCountInTake: Int = 0,
    val currentRecordedLoop: RecordedLoop? = null,
    val isLoopPlaying: Boolean = false,
    val savedLoops: List<RecordedLoop> = emptyList(),
    val statusBadgeText: String = GrooveSource.SMART_STUDIO_ENGINE.displayBadge
)

/**
 * Discrete UDF events for [AiGrooveRecorderViewModel].
 */
sealed interface AiGrooveRecorderUiEvent {
    data class SetAiModalOpen(val isOpen: Boolean) : AiGrooveRecorderUiEvent
    data class SetLoopDrawerOpen(val isOpen: Boolean) : AiGrooveRecorderUiEvent
    data class UpdatePromptText(val text: String) : AiGrooveRecorderUiEvent
    data class SelectQuickVibeChip(val vibePrompt: String) : AiGrooveRecorderUiEvent
    data class SetContextKitAndTempo(
        val kitId: DrumKitId,
        val bpm: Int,
        val swingPercent: Int = 0
    ) : AiGrooveRecorderUiEvent
    data object SubmitAiPrompt : AiGrooveRecorderUiEvent
    data object LoadPreviewIntoHighway : AiGrooveRecorderUiEvent
    data object SavePreviewGroove : AiGrooveRecorderUiEvent
    data class SelectSavedGroove(val pattern: GroovePattern) : AiGrooveRecorderUiEvent
    data object ToggleRecording : AiGrooveRecorderUiEvent
    data class RecordStageHit(
        val padId: DrumPadId,
        val velocity: Float,
        val timestampMs: Long? = null
    ) : AiGrooveRecorderUiEvent
    data class TickRecordingClock(val nowMs: Long) : AiGrooveRecorderUiEvent
    data object ToggleQuantizeSixteenth : AiGrooveRecorderUiEvent
    data object TogglePlayLoop : AiGrooveRecorderUiEvent
    data object SaveCurrentLoop : AiGrooveRecorderUiEvent
    data class SelectSavedLoop(val loop: RecordedLoop) : AiGrooveRecorderUiEvent
}

/**
 * UDF ViewModel for AI Text-to-Groove Generator & Session Loop Recorder (`03-ai-groove-recorder`).
 */
class AiGrooveRecorderViewModel(
    private val aiGrooveGeneratorPort: AiGrooveGeneratorPort = ProceduralGrooveGenerator,
    private val grooveRepositoryPort: GrooveRepositoryPort = InMemoryGrooveRepositoryAdapter(),
    private val drumAudioPort: DrumAudioPort? = null,
    private val clockPort: ClockPort = ClockPort.System,
    private val onGrooveLoadedToHighway: ((GroovePattern) -> Unit)? = null
) : ViewModel() {

    companion object {
        fun provideFactory(
            aiGrooveGeneratorPort: AiGrooveGeneratorPort = ProceduralGrooveGenerator,
            grooveRepositoryPort: GrooveRepositoryPort = InMemoryGrooveRepositoryAdapter(),
            drumAudioPort: DrumAudioPort? = null,
            clockPort: ClockPort = ClockPort.System,
            onGrooveLoadedToHighway: ((GroovePattern) -> Unit)? = null
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AiGrooveRecorderViewModel(
                    aiGrooveGeneratorPort = aiGrooveGeneratorPort,
                    grooveRepositoryPort = grooveRepositoryPort,
                    drumAudioPort = drumAudioPort,
                    clockPort = clockPort,
                    onGrooveLoadedToHighway = onGrooveLoadedToHighway
                ) as T
            }
        }
    }

    private val recorderEngine = SessionRecorderEngine(clockPort)
    private var loopPlaybackJob: Job? = null

    private val initialPreview = ProceduralGrooveGenerator.generate(
        AiGrooveRequest(
            prompt = "94 BPM J Dilla neo-soul boom bap with swung hi-hats and ghost snares",
            targetKit = DrumKitId.VINTAGE_BREAKBEAT
        )
    )

    private val _uiState = MutableStateFlow(
        AiGrooveRecorderUiState(
            generatedGroovePreview = initialPreview,
            currentBpm = initialPreview.bpm,
            currentSwingPercent = initialPreview.swingPercent,
            selectedTargetKit = initialPreview.kitId,
            statusBadgeText = initialPreview.source.displayBadge
        )
    )
    val uiState: StateFlow<AiGrooveRecorderUiState> = _uiState.asStateFlow()

    init {
        refreshSavedItems()
    }

    fun onEvent(event: AiGrooveRecorderUiEvent) {
        when (event) {
            is AiGrooveRecorderUiEvent.SetAiModalOpen -> {
                _uiState.update { it.copy(isAiModalOpen = event.isOpen, promptValidationError = null) }
            }
            is AiGrooveRecorderUiEvent.SetLoopDrawerOpen -> {
                _uiState.update { it.copy(isLoopDrawerOpen = event.isOpen) }
            }
            is AiGrooveRecorderUiEvent.UpdatePromptText -> {
                _uiState.update {
                    it.copy(
                        promptText = event.text,
                        promptValidationError = null
                    )
                }
            }
            is AiGrooveRecorderUiEvent.SelectQuickVibeChip -> {
                _uiState.update {
                    it.copy(
                        promptText = event.vibePrompt,
                        promptValidationError = null
                    )
                }
                handleSubmitAiPrompt()
            }
            is AiGrooveRecorderUiEvent.SetContextKitAndTempo -> {
                _uiState.update {
                    it.copy(
                        selectedTargetKit = event.kitId,
                        currentBpm = event.bpm.coerceIn(GroovePattern.MIN_BPM, GroovePattern.MAX_BPM),
                        currentSwingPercent = event.swingPercent.coerceIn(
                            GroovePattern.MIN_SWING_PERCENT,
                            GroovePattern.MAX_SWING_PERCENT
                        )
                    )
                }
            }
            is AiGrooveRecorderUiEvent.SubmitAiPrompt -> handleSubmitAiPrompt()
            is AiGrooveRecorderUiEvent.LoadPreviewIntoHighway -> handleLoadPreviewIntoHighway()
            is AiGrooveRecorderUiEvent.SavePreviewGroove -> handleSavePreviewGroove()
            is AiGrooveRecorderUiEvent.SelectSavedGroove -> {
                _uiState.update {
                    it.copy(
                        generatedGroovePreview = event.pattern,
                        activeLoadedGroove = event.pattern,
                        currentBpm = event.pattern.bpm,
                        currentSwingPercent = event.pattern.swingPercent,
                        selectedTargetKit = event.pattern.kitId,
                        statusBadgeText = event.pattern.source.displayBadge
                    )
                }
                onGrooveLoadedToHighway?.invoke(event.pattern)
            }
            is AiGrooveRecorderUiEvent.ToggleRecording -> handleToggleRecording()
            is AiGrooveRecorderUiEvent.RecordStageHit -> handleRecordStageHit(
                padId = event.padId,
                velocity = event.velocity,
                timestampMs = event.timestampMs
            )
            is AiGrooveRecorderUiEvent.TickRecordingClock -> {
                if (recorderEngine.isRecording) {
                    _uiState.update {
                        it.copy(recordingElapsedMs = recorderEngine.elapsedRecordingMs(event.nowMs))
                    }
                }
            }
            is AiGrooveRecorderUiEvent.ToggleQuantizeSixteenth -> handleToggleQuantizeSixteenth()
            is AiGrooveRecorderUiEvent.TogglePlayLoop -> handleTogglePlayLoop()
            is AiGrooveRecorderUiEvent.SaveCurrentLoop -> handleSaveCurrentLoop()
            is AiGrooveRecorderUiEvent.SelectSavedLoop -> {
                stopLoopPlayback()
                _uiState.update {
                    it.copy(
                        currentRecordedLoop = event.loop,
                        currentBpm = event.loop.bpm,
                        currentSwingPercent = event.loop.swingPercent,
                        selectedTargetKit = event.loop.kitId
                    )
                }
            }
        }
    }

    private fun handleSubmitAiPrompt() {
        val currentState = _uiState.value
        val validationError = AiGrooveRequest.validatePrompt(currentState.promptText)
        if (validationError != null) {
            _uiState.update {
                it.copy(
                    promptValidationError = validationError,
                    isGeneratingGroove = false
                )
            }
            return
        }

        val request = AiGrooveRequest(
            prompt = currentState.promptText.trim(),
            targetKit = null
        )

        _uiState.update {
            it.copy(
                promptValidationError = null,
                isGeneratingGroove = true
            )
        }

        viewModelScope.launch {
            val pattern = aiGrooveGeneratorPort.generateGroove(request)
            _uiState.update { state ->
                state.copy(
                    isGeneratingGroove = false,
                    generatedGroovePreview = pattern,
                    currentBpm = pattern.bpm,
                    currentSwingPercent = pattern.swingPercent,
                    selectedTargetKit = pattern.kitId,
                    statusBadgeText = pattern.source.displayBadge
                )
            }
        }
    }

    private fun handleLoadPreviewIntoHighway() {
        val preview = _uiState.value.generatedGroovePreview ?: return
        _uiState.update {
            it.copy(
                activeLoadedGroove = preview,
                isAiModalOpen = false
            )
        }
        onGrooveLoadedToHighway?.invoke(preview)
    }

    private fun handleSavePreviewGroove() {
        val preview = _uiState.value.generatedGroovePreview ?: return
        viewModelScope.launch {
            grooveRepositoryPort.saveGroove(preview)
            val grooves = grooveRepositoryPort.loadSavedGrooves()
            _uiState.update { it.copy(savedGrooves = grooves) }
        }
    }

    private fun handleToggleRecording() {
        val state = _uiState.value
        if (!recorderEngine.isRecording) {
            stopLoopPlayback()
            val startMs = clockPort.currentTimeMillis()
            recorderEngine.startRecording(
                bpm = state.currentBpm,
                swingPercent = state.currentSwingPercent,
                bars = 2,
                kitId = state.selectedTargetKit,
                startTimestampMs = startMs
            )
            _uiState.update {
                it.copy(
                    isRecording = true,
                    recordingElapsedMs = 0L,
                    recordedHitCountInTake = 0,
                    isLoopPlaying = false
                )
            }
        } else {
            val stopMs = clockPort.currentTimeMillis()
            val recordedLoop = recorderEngine.stopRecording(stoppedAtMs = stopMs)
            _uiState.update {
                it.copy(
                    isRecording = false,
                    recordingElapsedMs = 0L,
                    currentRecordedLoop = recordedLoop
                )
            }
        }
    }

    private fun handleRecordStageHit(padId: DrumPadId, velocity: Float, timestampMs: Long?) {
        if (!recorderEngine.isRecording) return
        val hitTime = timestampMs ?: clockPort.currentTimeMillis()
        recorderEngine.recordHit(padId = padId, velocity = velocity, timestampMs = hitTime)
        _uiState.update {
            it.copy(
                recordedHitCountInTake = recorderEngine.currentCapturedHits().size,
                recordingElapsedMs = recorderEngine.elapsedRecordingMs(hitTime)
            )
        }
    }

    private fun handleToggleQuantizeSixteenth() {
        val currentLoop = _uiState.value.currentRecordedLoop ?: return
        val updatedLoop = if (currentLoop.isQuantized) {
            LoopQuantizer.restoreUnquantized(currentLoop)
        } else {
            LoopQuantizer.quantizeToSixteenthGrid(currentLoop)
        }
        _uiState.update { it.copy(currentRecordedLoop = updatedLoop) }
    }

    private fun handleTogglePlayLoop() {
        val currentLoop = _uiState.value.currentRecordedLoop ?: return
        if (_uiState.value.isLoopPlaying) {
            stopLoopPlayback()
            return
        }

        _uiState.update { it.copy(isLoopPlaying = true) }
        loopPlaybackJob?.cancel()
        loopPlaybackJob = viewModelScope.launch {
            val durationMs = currentLoop.loopDurationMs.coerceAtLeast(250L)
            while (isActive && _uiState.value.isLoopPlaying) {
                val loopSnapshot = _uiState.value.currentRecordedLoop ?: break
                val sortedHits = loopSnapshot.activeHits.sortedBy { it.offsetMs }
                var previousOffsetMs = 0L
                for (hit in sortedHits) {
                    val waitMs = (hit.offsetMs - previousOffsetMs).coerceAtLeast(0L)
                    if (waitMs > 0L) delay(waitMs)
                    if (!_uiState.value.isLoopPlaying) break
                    drumAudioPort?.triggerPad(
                        kitId = loopSnapshot.kitId,
                        padId = hit.padId,
                        velocity = hit.velocity
                    )
                    previousOffsetMs = hit.offsetMs
                }
                val remainingMs = (durationMs - previousOffsetMs).coerceAtLeast(20L)
                delay(remainingMs)
            }
        }
    }

    private fun stopLoopPlayback() {
        loopPlaybackJob?.cancel()
        loopPlaybackJob = null
        _uiState.update { it.copy(isLoopPlaying = false) }
    }

    private fun handleSaveCurrentLoop() {
        val loop = _uiState.value.currentRecordedLoop ?: return
        viewModelScope.launch {
            grooveRepositoryPort.saveRecordedLoop(loop)
            val loops = grooveRepositoryPort.loadRecordedLoops()
            _uiState.update { it.copy(savedLoops = loops) }
        }
    }

    private fun refreshSavedItems() {
        viewModelScope.launch {
            val grooves = grooveRepositoryPort.loadSavedGrooves()
            val loops = grooveRepositoryPort.loadRecordedLoops()
            _uiState.update {
                it.copy(
                    savedGrooves = grooves,
                    savedLoops = loops
                )
            }
        }
    }

    override fun onCleared() {
        stopLoopPlayback()
        super.onCleared()
    }
}

/**
 * AI Text-to-Groove Modal Dialog (`Req 1.1, 1.2, 1.3`).
 *
 * Matches `docs/specs/03-ai-groove-recorder/design-reference/stitch-prompt.md` and `docs/foundation/design-system.md`.
 */
@Composable
fun AiGroovePromptModal(
    uiState: AiGrooveRecorderUiState,
    onEvent: (AiGrooveRecorderUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!uiState.isAiModalOpen) return

    Dialog(onDismissRequest = { onEvent(AiGrooveRecorderUiEvent.SetAiModalOpen(false)) }) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(StudioTokens.RadiusLg))
                .background(StudioColors.SurfaceElevatedDark)
                .border(1.dp, StudioColors.BorderDark, RoundedCornerShape(StudioTokens.RadiusLg))
                .padding(StudioTokens.SpaceDefault)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(StudioTokens.SpaceCompact)
        ) {
            // 1. Header with #818CF8 Sparkle Accent & Status Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(StudioTokens.SpaceSmall)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(StudioColors.AiCoachIndigo.copy(alpha = 0.2f))
                            .border(1.dp, StudioColors.AiCoachIndigo, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✦",
                            color = StudioColors.AiCoachIndigo,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "AI Text-to-Groove Generator",
                        color = StudioColors.TextPrimaryDark,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(StudioTokens.RadiusSm))
                        .background(StudioColors.SurfaceDark)
                        .border(1.dp, StudioColors.AiCoachIndigo.copy(alpha = 0.5f), RoundedCornerShape(StudioTokens.RadiusSm))
                        .padding(horizontal = StudioTokens.SpaceSmall, vertical = StudioTokens.SpaceMicro)
                ) {
                    Text(
                        text = uiState.statusBadgeText,
                        color = StudioColors.AiCoachIndigo,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // 2. Prompt Input Box + Validation Message (Req 1.3)
            OutlinedTextField(
                value = uiState.promptText,
                onValueChange = { onEvent(AiGrooveRecorderUiEvent.UpdatePromptText(it)) },
                label = {
                    Text(
                        text = "Describe your groove vibe, BPM, swing, or genre",
                        color = StudioColors.TextSecondaryDark,
                        fontSize = 12.sp
                    )
                },
                isError = uiState.promptValidationError != null,
                singleLine = false,
                maxLines = 2,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = StudioColors.TextPrimaryDark,
                    unfocusedTextColor = StudioColors.TextPrimaryDark,
                    focusedBorderColor = StudioColors.AiCoachIndigo,
                    unfocusedBorderColor = StudioColors.BorderDark,
                    errorBorderColor = StudioColors.MissOrRecRed
                ),
                modifier = Modifier.fillMaxWidth()
            )

            if (uiState.promptValidationError != null) {
                Text(
                    text = uiState.promptValidationError,
                    color = StudioColors.MissOrRecRed,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // 3. Quick Vibe Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(StudioTokens.SpaceSmall)
            ) {
                AiGroovePromptBuilder.QuickVibePresets.forEach { preset ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(StudioTokens.RadiusSm))
                            .background(StudioColors.SurfaceDark)
                            .border(1.dp, StudioColors.BorderDark, RoundedCornerShape(StudioTokens.RadiusSm))
                            .clickable { onEvent(AiGrooveRecorderUiEvent.SelectQuickVibeChip(preset)) }
                            .padding(horizontal = StudioTokens.SpaceCompact, vertical = 6.dp)
                    ) {
                        Text(
                            text = preset,
                            color = StudioColors.TextPrimaryDark,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Generate Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = { onEvent(AiGrooveRecorderUiEvent.SubmitAiPrompt) },
                    enabled = !uiState.isGeneratingGroove,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StudioColors.AiCoachIndigo,
                        contentColor = StudioColors.StageBackgroundDark
                    )
                ) {
                    Text(
                        text = if (uiState.isGeneratingGroove) "Generating Groove..." else "✦ Generate Groove",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            // 4. Generated 16-Step Mini Grid Preview Card
            uiState.generatedGroovePreview?.let { preview ->
                MiniGroovePreviewCard(
                    pattern = preview,
                    onLoadIntoHighway = { onEvent(AiGrooveRecorderUiEvent.LoadPreviewIntoHighway) },
                    onSaveGroove = { onEvent(AiGrooveRecorderUiEvent.SavePreviewGroove) }
                )
            }
        }
    }
}

@Composable
private fun MiniGroovePreviewCard(
    pattern: GroovePattern,
    onLoadIntoHighway: () -> Unit,
    onSaveGroove: () -> Unit
) {
    val previewRows = listOf(
        DrumPadId.KICK_RIGHT to "KICK",
        DrumPadId.SNARE to "SNARE",
        DrumPadId.HI_HAT_CLOSED to "CL HH",
        DrumPadId.HI_HAT_OPEN to "OP HH"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(StudioTokens.RadiusMd))
            .background(StudioColors.SurfaceDark)
            .border(1.dp, StudioColors.BorderDark, RoundedCornerShape(StudioTokens.RadiusMd))
            .padding(StudioTokens.SpaceCompact),
        verticalArrangement = Arrangement.spacedBy(StudioTokens.SpaceSmall)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = pattern.title,
                color = StudioColors.TextPrimaryDark,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${pattern.bpm} BPM · ${pattern.swingPercent}% SWING · ${pattern.kitId.displayName}",
                color = StudioColors.ElectricAmber,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // 16-Step Bar 1 Preview Grid
        previewRows.forEach { (padId, label) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = label,
                    color = StudioColors.TextSecondaryDark,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    modifier = Modifier.width(44.dp)
                )
                for (step in 0 until 16) {
                    val stepMatch = pattern.steps.firstOrNull { it.stepIndex == step && it.padId == padId }
                    val isActive = stepMatch != null
                    val cellColor = when {
                        isActive -> StudioTokens.categoryRingColor(padId.category)
                        step % 4 == 0 -> StudioColors.SurfaceElevatedDark
                        else -> StudioColors.SurfaceInnerDark
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(cellColor)
                            .border(
                                width = 0.5.dp,
                                color = if (isActive) StudioColors.ElectricAmber else StudioColors.BorderDark,
                                shape = RoundedCornerShape(3.dp)
                            )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(StudioTokens.SpaceMicro))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(StudioTokens.SpaceSmall, Alignment.End)
        ) {
            OutlinedButton(
                onClick = onSaveGroove
            ) {
                Text(
                    text = "Save Groove",
                    color = StudioColors.TextPrimaryDark,
                    fontSize = 12.sp
                )
            }
            Button(
                onClick = onLoadIntoHighway,
                colors = ButtonDefaults.buttonColors(
                    containerColor = StudioColors.ElectricAmber,
                    contentColor = StudioColors.StageBackgroundDark
                )
            ) {
                Text(
                    text = "Load into Groove Highway",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

/**
 * Session Recorder Transport & Drawer (`Req 2.1, 2.2, 2.3`).
 *
 * Displays the pulsing Red `"REC 00:04.2"` pill, `"Play Loop"`, `"1/16 Quantize"` toggle switch,
 * and `"Save Loop"` button.
 */
@Composable
fun SessionLoopDrawer(
    uiState: AiGrooveRecorderUiState,
    onEvent: (AiGrooveRecorderUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeLoop = uiState.currentRecordedLoop
    val elapsedSeconds = String.format(Locale.US, "%02d:%04.1f",
        (uiState.recordingElapsedMs / 60_000L),
        (uiState.recordingElapsedMs % 60_000L) / 1000.0
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(StudioTokens.RadiusLg))
            .background(StudioColors.SurfaceElevatedDark)
            .border(1.dp, StudioColors.BorderDark, RoundedCornerShape(StudioTokens.RadiusLg))
            .padding(StudioTokens.SpaceCompact),
        verticalArrangement = Arrangement.spacedBy(StudioTokens.SpaceSmall)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Pulsing Red REC Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(StudioTokens.SpaceSmall),
                modifier = Modifier
                    .clip(RoundedCornerShape(StudioTokens.RadiusSm))
                    .background(
                        if (uiState.isRecording) StudioColors.MissOrRecRed.copy(alpha = 0.22f)
                        else StudioColors.SurfaceDark
                    )
                    .border(
                        width = 1.dp,
                        color = if (uiState.isRecording) StudioColors.MissOrRecRed else StudioColors.BorderDark,
                        shape = RoundedCornerShape(StudioTokens.RadiusSm)
                    )
                    .clickable { onEvent(AiGrooveRecorderUiEvent.ToggleRecording) }
                    .padding(horizontal = StudioTokens.SpaceCompact, vertical = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(StudioColors.MissOrRecRed)
                )
                Text(
                    text = if (uiState.isRecording) "REC $elapsedSeconds" else "REC",
                    color = if (uiState.isRecording) StudioColors.MissOrRecRed else StudioColors.TextPrimaryDark,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }

            // Play Loop Button
            OutlinedButton(
                onClick = { onEvent(AiGrooveRecorderUiEvent.TogglePlayLoop) },
                enabled = activeLoop != null && !uiState.isRecording
            ) {
                Text(
                    text = if (uiState.isLoopPlaying) "Stop Loop" else "Play Loop",
                    color = if (uiState.isLoopPlaying) StudioColors.ElectricAmber else StudioColors.TextPrimaryDark,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // 1/16 Quantize Toggle Switch (Req 2.2)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "1/16 Quantize",
                    color = StudioColors.TextPrimaryDark,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Switch(
                    checked = activeLoop?.isQuantized == true,
                    onCheckedChange = { onEvent(AiGrooveRecorderUiEvent.ToggleQuantizeSixteenth) },
                    enabled = activeLoop != null && !uiState.isRecording,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = StudioColors.StageBackgroundDark,
                        checkedTrackColor = StudioColors.ElectricAmber
                    )
                )
            }

            // Save Loop Button (Req 2.3)
            Button(
                onClick = { onEvent(AiGrooveRecorderUiEvent.SaveCurrentLoop) },
                enabled = activeLoop != null && !uiState.isRecording,
                colors = ButtonDefaults.buttonColors(
                    containerColor = StudioColors.ElectricAmber,
                    contentColor = StudioColors.StageBackgroundDark
                )
            ) {
                Text(
                    text = "Save Loop",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }

        // Active Take / Saved Loops Status Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val summaryText = when {
                uiState.isRecording ->
                    "Recording live finger-drumming hits (${uiState.recordedHitCountInTake} hits captured)..."
                activeLoop != null ->
                    "${activeLoop.title} · ${activeLoop.activeHits.size} hits · " +
                        if (activeLoop.isQuantized) "Snapped to 1/16 Grid (Non-Destructive)" else "Original Unquantized Timing"
                else -> "Tap REC and play any drum pads to capture a multi-take studio loop."
            }
            Text(
                text = summaryText,
                color = StudioColors.TextSecondaryDark,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )

            if (uiState.savedLoops.isNotEmpty()) {
                Text(
                    text = "Saved Loops: ${uiState.savedLoops.size}",
                    color = StudioColors.PerfectGreen,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
