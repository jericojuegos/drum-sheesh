package com.drumsheesh.core.ai

import com.drumsheesh.core.model.DrumKitId
import com.drumsheesh.core.model.DrumPadId
import com.drumsheesh.core.port.ClockPort
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Single live pad hit event captured during a session recording (`Req 2.1`).
 */
data class RecordedHitEvent(
    val padId: DrumPadId,
    val offsetMs: Long,
    val velocity: Float
)

/**
 * Recorded multi-bar session take supporting non-destructive `1/16` quantization (`Req 2.1, 2.2, 2.3`).
 *
 * [originalHits] always preserves the exact unquantized performance for non-destructive undo,
 * while [activeHits] reflects either the quantized or unquantized hits currently active.
 */
data class RecordedLoop(
    val id: String,
    val title: String,
    val bpm: Int,
    val swingPercent: Int = 0,
    val bars: Int = 2,
    val kitId: DrumKitId = DrumKitId.ACOUSTIC_STUDIO,
    val originalHits: List<RecordedHitEvent>,
    val activeHits: List<RecordedHitEvent> = originalHits,
    val isQuantized: Boolean = false,
    val createdAtMs: Long = 0L
) {
    val loopDurationMs: Long
        get() = LoopQuantizer.calculateLoopDurationMs(bpm = bpm, bars = bars)
}

/**
 * Pure-Kotlin live session recorder engine (`Req 2.1`).
 *
 * Captures triggered [DrumPadId] events with their relative `offsetMs` from recording start
 * and `velocity` (`0.1f..1.0f`) without requiring `android.permission.RECORD_AUDIO`.
 */
class SessionRecorderEngine(
    private val clockPort: ClockPort = ClockPort.System
) {
    private var activeRecordingStartMs: Long? = null
    private var recordingBpm: Int = 110
    private var recordingSwingPercent: Int = 0
    private var recordingBars: Int = 2
    private var recordingKitId: DrumKitId = DrumKitId.ACOUSTIC_STUDIO
    private val recordedHits = mutableListOf<RecordedHitEvent>()
    private var takeCounter: Int = 0

    val isRecording: Boolean
        get() = activeRecordingStartMs != null

    fun startRecording(
        bpm: Int,
        swingPercent: Int = 0,
        bars: Int = 2,
        kitId: DrumKitId = DrumKitId.ACOUSTIC_STUDIO,
        startTimestampMs: Long = clockPort.currentTimeMillis()
    ) {
        activeRecordingStartMs = startTimestampMs
        recordingBpm = bpm.coerceIn(GroovePattern.MIN_BPM, GroovePattern.MAX_BPM)
        recordingSwingPercent = swingPercent.coerceIn(
            GroovePattern.MIN_SWING_PERCENT,
            GroovePattern.MAX_SWING_PERCENT
        )
        recordingBars = bars.coerceIn(1, 8)
        recordingKitId = kitId
        recordedHits.clear()
    }

    fun recordHit(
        padId: DrumPadId,
        velocity: Float,
        timestampMs: Long = clockPort.currentTimeMillis()
    ): RecordedHitEvent? {
        val startMs = activeRecordingStartMs ?: return null
        val offsetMs = (timestampMs - startMs).coerceAtLeast(0L)
        val event = RecordedHitEvent(
            padId = padId,
            offsetMs = offsetMs,
            velocity = velocity.coerceIn(GroovePattern.MIN_VELOCITY, GroovePattern.MAX_VELOCITY)
        )
        recordedHits.add(event)
        return event
    }

    fun elapsedRecordingMs(nowMs: Long = clockPort.currentTimeMillis()): Long {
        val startMs = activeRecordingStartMs ?: return 0L
        return (nowMs - startMs).coerceAtLeast(0L)
    }

    fun currentCapturedHits(): List<RecordedHitEvent> = recordedHits.toList()

    fun stopRecording(
        customTitle: String? = null,
        stoppedAtMs: Long = clockPort.currentTimeMillis()
    ): RecordedLoop {
        takeCounter += 1
        val snapshot = recordedHits.sortedBy { it.offsetMs }
        activeRecordingStartMs = null
        recordedHits.clear()

        val resolvedTitle = customTitle?.trim()?.takeIf { it.isNotEmpty() }
            ?: "Studio Take #$takeCounter (${recordingBpm} BPM)"

        return RecordedLoop(
            id = "loop-take-$stoppedAtMs-$takeCounter",
            title = resolvedTitle,
            bpm = recordingBpm,
            swingPercent = recordingSwingPercent,
            bars = recordingBars,
            kitId = recordingKitId,
            originalHits = snapshot,
            activeHits = snapshot,
            isQuantized = false,
            createdAtMs = stoppedAtMs
        )
    }
}

/**
 * Pure-Kotlin non-destructive `1/16`-note grid quantizer (`Req 2.2`).
 *
 * Snaps recorded `offsetMs` hit timestamps to the nearest 16th-note (or swung 16th-note) grid
 * position for the loop's BPM and swing while preserving [RecordedLoop.originalHits] untouched.
 */
object LoopQuantizer {

    const val STEPS_PER_BAR: Int = 16

    /**
     * Computes the exact millisecond grid positions for all `0..(bars * 16)` 16th-note steps,
     * applying swing delay to odd-numbered 16th-note subdivisions (`stepIndex % 2 == 1`).
     */
    fun buildSixteenthGridTimestampsMs(
        bpm: Int,
        swingPercent: Int = 0,
        bars: Int = 2
    ): List<Long> {
        val clampedBpm = bpm.coerceIn(GroovePattern.MIN_BPM, GroovePattern.MAX_BPM)
        val clampedSwing = swingPercent.coerceIn(
            GroovePattern.MIN_SWING_PERCENT,
            GroovePattern.MAX_SWING_PERCENT
        )
        val clampedBars = bars.coerceIn(1, 8)
        val totalSteps = clampedBars * STEPS_PER_BAR

        val sixteenthDurationMs = (60_000.0 / clampedBpm.toDouble()) / 4.0
        // Swing shifts odd 16th notes up to 50% of a 16th note at 75% swing
        val oddStepSwingDelayMs = sixteenthDurationMs * (clampedSwing.toDouble() / 100.0) * (2.0 / 3.0)

        return (0..totalSteps).map { stepIndex ->
            val straightMs = stepIndex * sixteenthDurationMs
            val swungMs = if (stepIndex % 2 == 1 && stepIndex < totalSteps) {
                straightMs + oddStepSwingDelayMs
            } else {
                straightMs
            }
            swungMs.roundToLong()
        }
    }

    fun calculateLoopDurationMs(bpm: Int, bars: Int = 2): Long {
        val clampedBpm = bpm.coerceIn(GroovePattern.MIN_BPM, GroovePattern.MAX_BPM)
        val clampedBars = bars.coerceIn(1, 8)
        val totalSixteenths = clampedBars * STEPS_PER_BAR
        return ((60_000.0 / clampedBpm.toDouble()) / 4.0 * totalSixteenths).roundToLong()
    }

    /**
     * Snaps every hit in [loop.originalHits] to the nearest `1/16`-note grid interval (`Req 2.2`).
     * Preserves [loop.originalHits] identically so [restoreUnquantized] is 100% lossless.
     */
    fun quantizeToSixteenthGrid(loop: RecordedLoop): RecordedLoop {
        val gridMs = buildSixteenthGridTimestampsMs(
            bpm = loop.bpm,
            swingPercent = loop.swingPercent,
            bars = loop.bars
        )
        val loopDurationMs = gridMs.last().coerceAtLeast(1L)

        val quantizedHits = loop.originalHits.map { hit ->
            val wrappedOffset = hit.offsetMs.coerceAtLeast(0L) % loopDurationMs
            val nearestGridMs = gridMs.minByOrNull { gridPoint ->
                abs(gridPoint - wrappedOffset)
            } ?: wrappedOffset
            // Wrap end-of-loop boundary back to step 0 if it snapped to the terminal bar line
            val finalOffsetMs = if (nearestGridMs >= loopDurationMs) 0L else nearestGridMs
            hit.copy(offsetMs = finalOffsetMs)
        }.sortedWith(compareBy<RecordedHitEvent> { it.offsetMs }.thenBy { it.padId.ordinal })

        return loop.copy(
            activeHits = quantizedHits,
            isQuantized = true
        )
    }

    /**
     * Non-destructively restores the original unquantized performance (`Req 2.2`).
     */
    fun restoreUnquantized(loop: RecordedLoop): RecordedLoop =
        loop.copy(
            activeHits = loop.originalHits,
            isQuantized = false
        )

    /**
     * Converts a [RecordedLoop] into a 16-step-per-bar [GroovePattern] so a user's recorded loop
     * can also be loaded directly into the Groove Highway or visual step preview.
     */
    fun toGroovePattern(loop: RecordedLoop): GroovePattern {
        val clampedBpm = loop.bpm.coerceIn(GroovePattern.MIN_BPM, GroovePattern.MAX_BPM)
        val clampedBars = loop.bars.coerceIn(1, 8)
        val totalSteps = clampedBars * STEPS_PER_BAR
        val sixteenthDurationMs = (60_000.0 / clampedBpm.toDouble()) / 4.0
        val loopDurationMs = calculateLoopDurationMs(clampedBpm, clampedBars).coerceAtLeast(1L)

        val convertedSteps = loop.activeHits.map { hit ->
            val wrappedMs = hit.offsetMs.coerceAtLeast(0L) % loopDurationMs
            val stepIndex = (wrappedMs.toDouble() / sixteenthDurationMs).roundToInt() % totalSteps
            GrooveStep(
                stepIndex = stepIndex.coerceIn(0, totalSteps - 1),
                padId = hit.padId,
                velocity = hit.velocity.coerceIn(GroovePattern.MIN_VELOCITY, GroovePattern.MAX_VELOCITY)
            )
        }.distinctBy { it.stepIndex to it.padId }
            .sortedWith(compareBy<GrooveStep> { it.stepIndex }.thenBy { it.padId.ordinal })

        return GroovePattern(
            id = loop.id,
            title = loop.title,
            bpm = clampedBpm,
            swingPercent = loop.swingPercent,
            stepsPerBar = STEPS_PER_BAR,
            bars = clampedBars,
            kitId = loop.kitId,
            steps = convertedSteps,
            source = GrooveSource.RECORDED_LOOP
        )
    }
}
