package com.drumsheesh.feature.highway

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
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
import com.drumsheesh.core.coach.ActualPadHit
import com.drumsheesh.core.coach.BuiltInGrooveCatalog
import com.drumsheesh.core.coach.GroovePattern
import com.drumsheesh.core.coach.GrooveTimelineCalculator
import com.drumsheesh.core.coach.HitJudgement
import com.drumsheesh.core.coach.PocketCoachReport
import com.drumsheesh.core.coach.RhythmPocketCoach
import com.drumsheesh.core.coach.ScheduledGrooveStep
import com.drumsheesh.core.coach.TimingTendency
import com.drumsheesh.core.model.DrumPadId
import com.drumsheesh.core.port.ClockPort
import com.drumsheesh.ui.components.GrooveHighwayRenderer
import com.drumsheesh.ui.theme.StudioColors
import com.drumsheesh.ui.theme.StudioTokens
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Immutable UDF state for the Interactive Groove Highway & Rhythm Pocket Coach (`02-groove-highway-coach`).
 */
data class GrooveHighwayUiState(
    val availableGrooves: List<GroovePattern> = BuiltInGrooveCatalog.allGrooves,
    val selectedGroove: GroovePattern = BuiltInGrooveCatalog.CLASSIC_ROCK_BACKBEAT,
    val bpm: Int = BuiltInGrooveCatalog.CLASSIC_ROCK_BACKBEAT.bpm,
    val swingPercent: Int = BuiltInGrooveCatalog.CLASSIC_ROCK_BACKBEAT.swingPercent,
    val passes: Int = 2,
    val isHighwayExpanded: Boolean = true,
    val isPlaying: Boolean = false,
    val sessionStartTimeMs: Long = 0L,
    val playheadTimeMs: Long = 0L,
    val scheduledTimeline: List<ScheduledGrooveStep> = GrooveTimelineCalculator.expandTimeline(
        pattern = BuiltInGrooveCatalog.CLASSIC_ROCK_BACKBEAT,
        startTimeMs = 0L,
        passes = 2
    ),
    val recordedHits: List<ActualPadHit> = emptyList(),
    val lastJudgement: HitJudgement? = null,
    val lastJudgementAtMs: Long? = null,
    val isNowLineFlashing: Boolean = false,
    val coachReport: PocketCoachReport? = null,
    val isCoachModalVisible: Boolean = false
)

/**
 * Discrete UDF events for [GrooveHighwayViewModel].
 */
sealed interface GrooveHighwayUiEvent {
    data class SelectGroove(val grooveId: String) : GrooveHighwayUiEvent
    data class LoadCustomGroove(val pattern: GroovePattern) : GrooveHighwayUiEvent
    data class AdjustBpm(val delta: Int) : GrooveHighwayUiEvent
    data class SetBpm(val bpm: Int) : GrooveHighwayUiEvent
    data class AdjustSwing(val delta: Int) : GrooveHighwayUiEvent
    data class SetSwing(val swingPercent: Int) : GrooveHighwayUiEvent
    data object ToggleHighwayExpanded : GrooveHighwayUiEvent
    data object TogglePlayback : GrooveHighwayUiEvent
    data class StepPlayhead(val currentTimeMs: Long) : GrooveHighwayUiEvent
    data class RecordUserPadHit(
        val padId: DrumPadId,
        val velocity: Float = 0.85f,
        val hitTimeMs: Long? = null
    ) : GrooveHighwayUiEvent
    data object FinishAndCoach : GrooveHighwayUiEvent
    data object DismissCoachModal : GrooveHighwayUiEvent
    data object RetryGroove : GrooveHighwayUiEvent
    data object SlowTempoAndRetry : GrooveHighwayUiEvent
}

/**
 * UDF ViewModel for the Interactive Groove Highway & Rhythm Pocket Coach (`02-groove-highway-coach`).
 */
class GrooveHighwayViewModel(
    private val clockPort: ClockPort = ClockPort.System
) : ViewModel() {

    companion object {
        const val NOW_LINE_FLASH_DURATION_MS: Long = 180L
        const val SLOW_TEMPO_STEP_BPM: Int = -10

        fun provideFactory(
            clockPort: ClockPort = ClockPort.System
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return GrooveHighwayViewModel(clockPort) as T
            }
        }
    }

    private val _uiState = MutableStateFlow(GrooveHighwayUiState())
    val uiState: StateFlow<GrooveHighwayUiState> = _uiState.asStateFlow()

    fun onEvent(event: GrooveHighwayUiEvent) {
        when (event) {
            is GrooveHighwayUiEvent.SelectGroove -> handleSelectGroove(event.grooveId)
            is GrooveHighwayUiEvent.LoadCustomGroove -> handleLoadCustomGroove(event.pattern)
            is GrooveHighwayUiEvent.AdjustBpm -> handleSetBpm(_uiState.value.bpm + event.delta)
            is GrooveHighwayUiEvent.SetBpm -> handleSetBpm(event.bpm)
            is GrooveHighwayUiEvent.AdjustSwing -> handleSetSwing(_uiState.value.swingPercent + event.delta)
            is GrooveHighwayUiEvent.SetSwing -> handleSetSwing(event.swingPercent)
            is GrooveHighwayUiEvent.ToggleHighwayExpanded -> {
                _uiState.update { it.copy(isHighwayExpanded = !it.isHighwayExpanded) }
            }
            is GrooveHighwayUiEvent.TogglePlayback -> handleTogglePlayback()
            is GrooveHighwayUiEvent.StepPlayhead -> handleStepPlayhead(event.currentTimeMs)
            is GrooveHighwayUiEvent.RecordUserPadHit -> {
                handleRecordUserPadHit(event.padId, event.velocity, event.hitTimeMs)
            }
            is GrooveHighwayUiEvent.FinishAndCoach -> handleFinishAndCoach()
            is GrooveHighwayUiEvent.DismissCoachModal -> {
                _uiState.update { it.copy(isCoachModalVisible = false) }
            }
            is GrooveHighwayUiEvent.RetryGroove -> restartSessionWithBpm(_uiState.value.bpm)
            is GrooveHighwayUiEvent.SlowTempoAndRetry -> {
                restartSessionWithBpm(_uiState.value.bpm + SLOW_TEMPO_STEP_BPM)
            }
        }
    }

    private fun handleSelectGroove(grooveId: String) {
        val nextGroove = BuiltInGrooveCatalog.findById(grooveId)
        val nowMs = clockPort.currentTimeMillis()
        _uiState.update { state ->
            val timeline = GrooveTimelineCalculator.expandTimeline(
                pattern = nextGroove,
                startTimeMs = nowMs,
                bpmOverride = nextGroove.bpm,
                swingOverride = nextGroove.swingPercent,
                passes = state.passes
            )
            state.copy(
                selectedGroove = nextGroove,
                bpm = nextGroove.bpm,
                swingPercent = nextGroove.swingPercent,
                isPlaying = false,
                sessionStartTimeMs = nowMs,
                playheadTimeMs = nowMs,
                scheduledTimeline = timeline,
                recordedHits = emptyList(),
                lastJudgement = null,
                lastJudgementAtMs = null,
                isNowLineFlashing = false,
                coachReport = null,
                isCoachModalVisible = false
            )
        }
    }

    private fun handleLoadCustomGroove(customPattern: GroovePattern) {
        val nowMs = clockPort.currentTimeMillis()
        _uiState.update { state ->
            val updatedList = listOf(customPattern) + state.availableGrooves.filterNot { it.id == customPattern.id }
            val timeline = GrooveTimelineCalculator.expandTimeline(
                pattern = customPattern,
                startTimeMs = nowMs,
                bpmOverride = customPattern.bpm,
                swingOverride = customPattern.swingPercent,
                passes = state.passes
            )
            state.copy(
                availableGrooves = updatedList,
                selectedGroove = customPattern,
                bpm = customPattern.bpm,
                swingPercent = customPattern.swingPercent,
                isHighwayExpanded = true,
                isPlaying = false,
                sessionStartTimeMs = nowMs,
                playheadTimeMs = nowMs,
                scheduledTimeline = timeline,
                recordedHits = emptyList(),
                lastJudgement = null,
                lastJudgementAtMs = null,
                isNowLineFlashing = false,
                coachReport = null,
                isCoachModalVisible = false
            )
        }
    }

    private fun handleSetBpm(targetBpm: Int) {
        val clampedBpm = GrooveTimelineCalculator.clampBpm(targetBpm)
        val nowMs = clockPort.currentTimeMillis()
        _uiState.update { state ->
            val anchorMs = if (state.isPlaying) state.sessionStartTimeMs else nowMs
            val timeline = GrooveTimelineCalculator.expandTimeline(
                pattern = state.selectedGroove,
                startTimeMs = anchorMs,
                bpmOverride = clampedBpm,
                swingOverride = state.swingPercent,
                passes = state.passes
            )
            state.copy(
                bpm = clampedBpm,
                scheduledTimeline = timeline
            )
        }
    }

    private fun handleSetSwing(targetSwing: Int) {
        val clampedSwing = GrooveTimelineCalculator.clampSwingPercent(targetSwing)
        val nowMs = clockPort.currentTimeMillis()
        _uiState.update { state ->
            val anchorMs = if (state.isPlaying) state.sessionStartTimeMs else nowMs
            val timeline = GrooveTimelineCalculator.expandTimeline(
                pattern = state.selectedGroove,
                startTimeMs = anchorMs,
                bpmOverride = state.bpm,
                swingOverride = clampedSwing,
                passes = state.passes
            )
            state.copy(
                swingPercent = clampedSwing,
                scheduledTimeline = timeline
            )
        }
    }

    private fun handleTogglePlayback() {
        val nowMs = clockPort.currentTimeMillis()
        _uiState.update { state ->
            if (state.isPlaying) {
                state.copy(isPlaying = false, isNowLineFlashing = false)
            } else {
                val timeline = GrooveTimelineCalculator.expandTimeline(
                    pattern = state.selectedGroove,
                    startTimeMs = nowMs,
                    bpmOverride = state.bpm,
                    swingOverride = state.swingPercent,
                    passes = state.passes
                )
                state.copy(
                    isPlaying = true,
                    sessionStartTimeMs = nowMs,
                    playheadTimeMs = nowMs,
                    scheduledTimeline = timeline,
                    recordedHits = emptyList(),
                    lastJudgement = null,
                    lastJudgementAtMs = null,
                    isNowLineFlashing = false,
                    isCoachModalVisible = false
                )
            }
        }
    }

    private fun handleStepPlayhead(currentTimeMs: Long) {
        _uiState.update { state ->
            if (!state.isPlaying) return@update state

            val flashStillActive = state.lastJudgementAtMs?.let { hitAt ->
                (currentTimeMs - hitAt) in 0L..NOW_LINE_FLASH_DURATION_MS
            } ?: false

            val lastStepMs = state.scheduledTimeline.maxOfOrNull { it.expectedTimeMs } ?: currentTimeMs
            val passFinished = currentTimeMs > (lastStepMs + RhythmPocketCoach.MAX_MATCH_WINDOW_MS)

            if (passFinished) {
                val report = RhythmPocketCoach.evaluate(
                    expectedSteps = state.scheduledTimeline,
                    actualHits = state.recordedHits
                )
                state.copy(
                    isPlaying = false,
                    playheadTimeMs = currentTimeMs,
                    isNowLineFlashing = false,
                    coachReport = report,
                    isCoachModalVisible = true
                )
            } else {
                state.copy(
                    playheadTimeMs = currentTimeMs,
                    isNowLineFlashing = flashStillActive
                )
            }
        }
    }

    private fun handleRecordUserPadHit(
        padId: DrumPadId,
        velocity: Float,
        explicitTimeMs: Long?
    ) {
        val hitTimeMs = explicitTimeMs ?: clockPort.currentTimeMillis()
        _uiState.update { state ->
            val liveJudgement = RhythmPocketCoach.judgeLiveHit(
                padId = padId,
                actualTimeMs = hitTimeMs,
                timeline = state.scheduledTimeline
            )
            val newHit = ActualPadHit(
                padId = padId,
                actualTimeMs = hitTimeMs,
                velocity = velocity.coerceIn(0.1f, 1.0f)
            )
            state.copy(
                playheadTimeMs = hitTimeMs,
                recordedHits = state.recordedHits + newHit,
                lastJudgement = liveJudgement,
                lastJudgementAtMs = hitTimeMs,
                isNowLineFlashing = true
            )
        }
    }

    private fun handleFinishAndCoach() {
        _uiState.update { state ->
            val report = RhythmPocketCoach.evaluate(
                expectedSteps = state.scheduledTimeline,
                actualHits = state.recordedHits
            )
            state.copy(
                isPlaying = false,
                isNowLineFlashing = false,
                coachReport = report,
                isCoachModalVisible = true
            )
        }
    }

    private fun restartSessionWithBpm(targetBpm: Int) {
        val clampedBpm = GrooveTimelineCalculator.clampBpm(targetBpm)
        val nowMs = clockPort.currentTimeMillis()
        _uiState.update { state ->
            val timeline = GrooveTimelineCalculator.expandTimeline(
                pattern = state.selectedGroove,
                startTimeMs = nowMs,
                bpmOverride = clampedBpm,
                swingOverride = state.swingPercent,
                passes = state.passes
            )
            state.copy(
                bpm = clampedBpm,
                isPlaying = true,
                sessionStartTimeMs = nowMs,
                playheadTimeMs = nowMs,
                scheduledTimeline = timeline,
                recordedHits = emptyList(),
                lastJudgement = null,
                lastJudgementAtMs = null,
                isNowLineFlashing = false,
                coachReport = null,
                isCoachModalVisible = false
            )
        }
    }
}

/**
 * Interactive Groove Highway Split-Top Panel & Rhythm Pocket Coach Modal (`Req 1.1, 1.2, 1.3, 2.1, 2.2`).
 */
@Composable
fun GrooveHighwayOverlayPanel(
    viewModel: GrooveHighwayViewModel,
    clockPort: ClockPort = ClockPort.System,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var grooveMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isPlaying) {
        while (uiState.isPlaying) {
            withFrameMillis {
                viewModel.onEvent(GrooveHighwayUiEvent.StepPlayhead(clockPort.currentTimeMillis()))
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(StudioColors.SurfaceDark)
            .border(1.dp, StudioColors.BorderDark)
            .padding(horizontal = StudioTokens.SpaceCompact, vertical = StudioTokens.SpaceSmall),
        verticalArrangement = Arrangement.spacedBy(StudioTokens.SpaceSmall)
    ) {
        // Highway Transport & Groove Selector Header Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Groove Selector Dropdown
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(StudioTokens.SpaceSmall)
            ) {
                Box {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(StudioTokens.RadiusMd))
                            .background(StudioColors.SurfaceElevatedDark)
                            .border(1.dp, StudioColors.ElectricAmber, RoundedCornerShape(StudioTokens.RadiusMd))
                            .clickable { grooveMenuExpanded = true }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "GROOVE: ${uiState.selectedGroove.title.uppercase()} ▾",
                            color = StudioColors.TextPrimaryDark,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    DropdownMenu(
                        expanded = grooveMenuExpanded,
                        onDismissRequest = { grooveMenuExpanded = false },
                        modifier = Modifier.background(StudioColors.SurfaceElevatedDark)
                    ) {
                        uiState.availableGrooves.forEach { groove ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "${groove.title} (${groove.bpm} BPM · ${groove.genre})",
                                        color = if (groove.id == uiState.selectedGroove.id) {
                                            StudioColors.ElectricAmber
                                        } else {
                                            StudioColors.TextPrimaryDark
                                        },
                                        fontSize = 13.sp
                                    )
                                },
                                onClick = {
                                    grooveMenuExpanded = false
                                    viewModel.onEvent(GrooveHighwayUiEvent.SelectGroove(groove.id))
                                }
                            )
                        }
                    }
                }
            }

            // Center: Monospace BPM & Swing Controls
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(StudioTokens.SpaceSmall)
            ) {
                // BPM Pill
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(StudioTokens.RadiusMd))
                        .background(StudioColors.SurfaceElevatedDark)
                        .border(1.dp, StudioColors.BorderDark, RoundedCornerShape(StudioTokens.RadiusMd))
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "−",
                        color = StudioColors.ElectricAmber,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { viewModel.onEvent(GrooveHighwayUiEvent.AdjustBpm(-5)) }
                            .padding(horizontal = 4.dp)
                    )
                    Text(
                        text = "${uiState.bpm} BPM",
                        color = StudioColors.TextPrimaryDark,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "+",
                        color = StudioColors.ElectricAmber,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { viewModel.onEvent(GrooveHighwayUiEvent.AdjustBpm(5)) }
                            .padding(horizontal = 4.dp)
                    )
                }

                // Swing Pill (0..75%)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(StudioTokens.RadiusMd))
                        .background(StudioColors.SurfaceElevatedDark)
                        .border(1.dp, StudioColors.BorderDark, RoundedCornerShape(StudioTokens.RadiusMd))
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "−",
                        color = StudioColors.AiCoachIndigo,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { viewModel.onEvent(GrooveHighwayUiEvent.AdjustSwing(-5)) }
                            .padding(horizontal = 4.dp)
                    )
                    Text(
                        text = "SWING ${uiState.swingPercent}%",
                        color = StudioColors.TextPrimaryDark,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "+",
                        color = StudioColors.AiCoachIndigo,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { viewModel.onEvent(GrooveHighwayUiEvent.AdjustSwing(5)) }
                            .padding(horizontal = 4.dp)
                    )
                }
            }

            // Right: Play/Stop, Finish & Coach, Collapse Toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(StudioTokens.SpaceSmall)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(StudioTokens.RadiusMd))
                        .background(
                            if (uiState.isPlaying) StudioColors.ElectricAmberSubtle else StudioColors.ElectricAmber
                        )
                        .border(1.dp, StudioColors.ElectricAmber, RoundedCornerShape(StudioTokens.RadiusMd))
                        .clickable { viewModel.onEvent(GrooveHighwayUiEvent.TogglePlayback) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (uiState.isPlaying) "PAUSE" else "PLAY GROOVE",
                        color = if (uiState.isPlaying) StudioColors.ElectricAmber else StudioColors.StageBackgroundDark,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(StudioTokens.RadiusMd))
                        .background(StudioColors.SurfaceElevatedDark)
                        .border(1.dp, StudioColors.AiCoachIndigo, RoundedCornerShape(StudioTokens.RadiusMd))
                        .clickable { viewModel.onEvent(GrooveHighwayUiEvent.FinishAndCoach) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "FINISH & COACH",
                        color = StudioColors.AiCoachIndigo,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(StudioTokens.RadiusSm))
                        .background(StudioColors.SurfaceElevatedDark)
                        .border(1.dp, StudioColors.BorderDark, RoundedCornerShape(StudioTokens.RadiusSm))
                        .clickable { viewModel.onEvent(GrooveHighwayUiEvent.ToggleHighwayExpanded) }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (uiState.isHighwayExpanded) "HIDE ▲" else "SHOW ▼",
                        color = StudioColors.TextSecondaryDark,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        if (uiState.isHighwayExpanded) {
            GrooveHighwayRenderer(
                scheduledSteps = uiState.scheduledTimeline,
                playheadTimeMs = uiState.playheadTimeMs,
                lastJudgement = uiState.lastJudgement,
                isNowLineFlashing = uiState.isNowLineFlashing
            )
        }
    }

    val activeReport = uiState.coachReport
    if (uiState.isCoachModalVisible && activeReport != null) {
        PocketCoachReportDialog(
            grooveTitle = uiState.selectedGroove.title,
            bpm = uiState.bpm,
            report = activeReport,
            onRetryGroove = { viewModel.onEvent(GrooveHighwayUiEvent.RetryGroove) },
            onSlowTempoRetry = { viewModel.onEvent(GrooveHighwayUiEvent.SlowTempoAndRetry) },
            onDismiss = { viewModel.onEvent(GrooveHighwayUiEvent.DismissCoachModal) }
        )
    }
}

/**
 * Post-session Rhythm Pocket Coach Modal (`stitch-prompt.md` Screen 1, `Req 2.1, 2.2`).
 */
@Composable
fun PocketCoachReportDialog(
    grooveTitle: String,
    bpm: Int,
    report: PocketCoachReport,
    onRetryGroove: () -> Unit,
    onSlowTempoRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val scoreColor = when {
        report.pocketScore >= 85 -> StudioColors.PerfectGreen
        report.pocketScore >= 65 -> StudioColors.GoodBlue
        report.pocketScore >= 45 -> StudioColors.DriftWarning
        else -> StudioColors.MissOrRecRed
    }

    val signedDriftText = if (report.meanDriftMs >= 0) "+${report.meanDriftMs}ms" else "${report.meanDriftMs}ms"
    val weakestLabel = report.weakestPad?.shortLabel ?: "NONE"

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(StudioTokens.RadiusLg))
                .background(StudioColors.SurfaceElevatedDark)
                .border(1.5.dp, StudioColors.BorderDark, RoundedCornerShape(StudioTokens.RadiusLg))
                .padding(StudioTokens.SpaceComfortable),
            verticalArrangement = Arrangement.spacedBy(StudioTokens.SpaceDefault)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "RHYTHM POCKET COACH",
                        color = StudioColors.AiCoachIndigo,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "$grooveTitle · $bpm BPM",
                        color = StudioColors.TextPrimaryDark,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(StudioTokens.RadiusSm))
                        .background(StudioColors.StageBackgroundDark)
                        .border(1.dp, scoreColor, RoundedCornerShape(StudioTokens.RadiusSm))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "${report.pocketScore} / 100 — ${report.tendency.displayLabel}",
                        color = scoreColor,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Mean Timing Drift & Weakest Pad Pill Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(StudioTokens.SpaceSmall)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(StudioTokens.RadiusSm))
                        .background(StudioColors.SurfaceDark)
                        .border(1.dp, StudioColors.BorderDark, RoundedCornerShape(StudioTokens.RadiusSm))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    val tendencyHint = when (report.tendency) {
                        TimingTendency.RUSHING -> "Early / Rushing"
                        TimingTendency.DRAGGING -> "Late / Dragging"
                        TimingTendency.LOCKED_IN -> "In the Pocket"
                    }
                    Text(
                        text = "MEAN DRIFT: $signedDriftText ($tendencyHint)",
                        color = StudioColors.TextPrimaryDark,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(StudioTokens.RadiusSm))
                        .background(StudioColors.SurfaceDark)
                        .border(1.dp, StudioColors.BorderDark, RoundedCornerShape(StudioTokens.RadiusSm))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "FOCUS PAD: $weakestLabel",
                        color = StudioColors.ElectricAmber,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Breakdown Bar Summary
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(StudioTokens.RadiusSm))
                    .background(StudioColors.StageBackgroundDark)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "${report.perfectPercent}% Perfect · ${report.goodPercent}% Good · ${report.earlyLatePercent}% Early/Late · ${report.missPercent}% Miss",
                    color = StudioColors.TextSecondaryDark,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            // 2-Sentence Coach Tip Card (#818CF8 border)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(StudioTokens.RadiusMd))
                    .background(StudioColors.StageBackgroundDark)
                    .border(1.dp, StudioColors.AiCoachIndigo, RoundedCornerShape(StudioTokens.RadiusMd))
                    .padding(StudioTokens.SpaceCompact),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "COACH DRILL RECOMMENDATION",
                    color = StudioColors.AiCoachIndigo,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = report.coachTip,
                    color = StudioColors.TextPrimaryDark,
                    fontSize = 13.sp
                )
            }

            // Action Buttons: "Retry Groove" (#FBBF24 primary) & "Slow Tempo -10 BPM" (secondary)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CLOSE",
                    color = StudioColors.TextSecondaryDark,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable { onDismiss() }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(StudioTokens.SpaceSmall)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(StudioTokens.RadiusMd))
                            .background(StudioColors.SurfaceDark)
                            .border(1.dp, StudioColors.BorderDark, RoundedCornerShape(StudioTokens.RadiusMd))
                            .clickable { onSlowTempoRetry() }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Slow Tempo -10 BPM",
                            color = StudioColors.TextPrimaryDark,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(StudioTokens.RadiusMd))
                            .background(StudioColors.ElectricAmber)
                            .clickable { onRetryGroove() }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Retry Groove",
                            color = StudioColors.StageBackgroundDark,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
