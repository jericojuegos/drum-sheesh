package com.drumsheesh.core.coach

import com.drumsheesh.core.model.DrumKitId
import com.drumsheesh.core.model.DrumPadId
import kotlin.math.roundToLong

/**
 * Single quantized step inside a [GroovePattern].
 *
 * @property stepIndex 0-based step offset within the pattern (`0 until stepsPerBar * bars`).
 * @property padId Target drum pad to strike on this step.
 * @property velocity Expected dynamic velocity (`0.1f..1.0f`).
 */
data class GrooveStep(
    val stepIndex: Int,
    val padId: DrumPadId,
    val velocity: Float = 0.85f
)

/**
 * Pure-Kotlin immutable step sequence representing a playable drum groove (`Req 1.1, 1.2`).
 */
data class GroovePattern(
    val id: String,
    val title: String,
    val genre: String,
    val bpm: Int,
    val swingPercent: Int,
    val stepsPerBar: Int = 16,
    val bars: Int = 2,
    val kitId: DrumKitId = DrumKitId.ACOUSTIC_STUDIO,
    val steps: List<GrooveStep>
) {
    val totalSteps: Int
        get() = (stepsPerBar.coerceAtLeast(1)) * (bars.coerceAtLeast(1))
}

/**
 * Resolved step on the real-time Groove Highway timeline with exact millisecond target timestamp.
 */
data class ScheduledGrooveStep(
    val passIndex: Int,
    val barIndex: Int,
    val stepIndex: Int,
    val globalStepIndex: Int,
    val padId: DrumPadId,
    val velocity: Float,
    val expectedTimeMs: Long
)

/**
 * Curated catalog of 8 studio-grade genre grooves (`Req 1.1`).
 */
object BuiltInGrooveCatalog {

    val CLASSIC_ROCK_BACKBEAT = GroovePattern(
        id = "groove-classic-rock",
        title = "Classic Rock Backbeat",
        genre = "Rock",
        bpm = 112,
        swingPercent = 0,
        stepsPerBar = 16,
        bars = 2,
        kitId = DrumKitId.ACOUSTIC_STUDIO,
        steps = buildList {
            // Bar 1 & Bar 2: 8th-note closed hats, kick on 1 & 3+, snare backbeat on 2 & 4, crash on downbeat
            add(GrooveStep(0, DrumPadId.CRASH, 0.95f))
            for (bar in 0 until 2) {
                val base = bar * 16
                for (eightStep in 0 until 16 step 2) {
                    if (bar > 0 || eightStep > 0) {
                        add(GrooveStep(base + eightStep, DrumPadId.HI_HAT_CLOSED, 0.78f))
                    }
                }
                add(GrooveStep(base + 0, DrumPadId.KICK_RIGHT, 0.95f))
                add(GrooveStep(base + 4, DrumPadId.SNARE, 0.92f))
                add(GrooveStep(base + 8, DrumPadId.KICK_RIGHT, 0.90f))
                add(GrooveStep(base + 10, DrumPadId.KICK_RIGHT, 0.84f))
                add(GrooveStep(base + 12, DrumPadId.SNARE, 0.94f))
            }
        }
    )

    val NEO_SOUL_BOOM_BAP = GroovePattern(
        id = "groove-neo-soul-boom-bap",
        title = "92 BPM Neo-Soul Boom Bap",
        genre = "Hip-Hop / Neo-Soul",
        bpm = 92,
        swingPercent = 18,
        stepsPerBar = 16,
        bars = 2,
        kitId = DrumKitId.VINTAGE_BREAKBEAT,
        steps = buildList {
            for (bar in 0 until 2) {
                val base = bar * 16
                for (s in listOf(0, 2, 4, 6, 8, 10, 12, 14)) {
                    add(GrooveStep(base + s, DrumPadId.HI_HAT_CLOSED, 0.72f))
                }
                add(GrooveStep(base + 0, DrumPadId.KICK_RIGHT, 0.95f))
                add(GrooveStep(base + 3, DrumPadId.KICK_RIGHT, 0.76f))
                add(GrooveStep(base + 4, DrumPadId.SNARE, 0.90f))
                add(GrooveStep(base + 7, DrumPadId.SNARE, 0.52f))
                add(GrooveStep(base + 10, DrumPadId.KICK_RIGHT, 0.88f))
                add(GrooveStep(base + 12, DrumPadId.SNARE, 0.92f))
                add(GrooveStep(base + 15, DrumPadId.HI_HAT_OPEN, 0.80f))
            }
        }
    )

    val TRAP_HI_HAT_ROLL = GroovePattern(
        id = "groove-trap-hi-hat-roll",
        title = "140 BPM Trap Hi-Hat Roll",
        genre = "Trap",
        bpm = 140,
        swingPercent = 0,
        stepsPerBar = 16,
        bars = 2,
        kitId = DrumKitId.TRAP_808,
        steps = buildList {
            for (bar in 0 until 2) {
                val base = bar * 16
                // Rapid 16th-note hi-hat run with dynamic accents
                for (s in 0 until 16) {
                    val vel = if (s % 2 == 0) 0.85f else 0.62f
                    add(GrooveStep(base + s, DrumPadId.HI_HAT_CLOSED, vel))
                }
                add(GrooveStep(base + 0, DrumPadId.KICK_RIGHT, 0.98f))
                add(GrooveStep(base + 6, DrumPadId.KICK_RIGHT, 0.88f))
                add(GrooveStep(base + 8, DrumPadId.SNARE, 0.95f))
                add(GrooveStep(base + 11, DrumPadId.KICK_LEFT, 0.84f))
                add(GrooveStep(base + 14, DrumPadId.HI_HAT_OPEN, 0.82f))
            }
        }
    )

    val FUNK_GHOST_NOTE_POCKET = GroovePattern(
        id = "groove-funk-ghost-pocket",
        title = "Funk Ghost-Note Pocket",
        genre = "Funk",
        bpm = 98,
        swingPercent = 14,
        stepsPerBar = 16,
        bars = 2,
        kitId = DrumKitId.VINTAGE_BREAKBEAT,
        steps = buildList {
            for (bar in 0 until 2) {
                val base = bar * 16
                for (s in 0 until 16 step 2) {
                    add(GrooveStep(base + s, DrumPadId.HI_HAT_CLOSED, 0.76f))
                }
                add(GrooveStep(base + 0, DrumPadId.KICK_RIGHT, 0.95f))
                add(GrooveStep(base + 1, DrumPadId.SNARE, 0.48f))
                add(GrooveStep(base + 4, DrumPadId.SNARE, 0.94f))
                add(GrooveStep(base + 7, DrumPadId.SNARE, 0.50f))
                add(GrooveStep(base + 9, DrumPadId.SNARE, 0.52f))
                add(GrooveStep(base + 10, DrumPadId.KICK_RIGHT, 0.88f))
                add(GrooveStep(base + 12, DrumPadId.SNARE, 0.92f))
                add(GrooveStep(base + 14, DrumPadId.HI_HAT_OPEN, 0.84f))
                add(GrooveStep(base + 15, DrumPadId.KICK_RIGHT, 0.78f))
            }
        }
    )

    val POP_PUNK_DOUBLE_TIME = GroovePattern(
        id = "groove-pop-punk-double-time",
        title = "Pop-Punk Double Time",
        genre = "Punk / Alt-Rock",
        bpm = 176,
        swingPercent = 0,
        stepsPerBar = 16,
        bars = 2,
        kitId = DrumKitId.ACOUSTIC_STUDIO,
        steps = buildList {
            add(GrooveStep(0, DrumPadId.CRASH, 0.96f))
            for (bar in 0 until 2) {
                val base = bar * 16
                for (s in 0 until 16 step 4) {
                    add(GrooveStep(base + s, DrumPadId.RIDE, 0.82f))
                }
                add(GrooveStep(base + 0, DrumPadId.KICK_RIGHT, 0.95f))
                add(GrooveStep(base + 2, DrumPadId.SNARE, 0.92f))
                add(GrooveStep(base + 4, DrumPadId.KICK_RIGHT, 0.90f))
                add(GrooveStep(base + 6, DrumPadId.KICK_RIGHT, 0.88f))
                add(GrooveStep(base + 8, DrumPadId.KICK_RIGHT, 0.94f))
                add(GrooveStep(base + 10, DrumPadId.SNARE, 0.94f))
                add(GrooveStep(base + 12, DrumPadId.TOM_HIGH, 0.85f))
                add(GrooveStep(base + 14, DrumPadId.TOM_FLOOR, 0.90f))
            }
        }
    )

    val HEAVY_METAL_DOUBLE_KICK = GroovePattern(
        id = "groove-heavy-metal-double-kick",
        title = "Heavy Metal Double Kick",
        genre = "Metal",
        bpm = 150,
        swingPercent = 0,
        stepsPerBar = 16,
        bars = 2,
        kitId = DrumKitId.ACOUSTIC_STUDIO,
        steps = buildList {
            add(GrooveStep(0, DrumPadId.CRASH, 0.98f))
            for (bar in 0 until 2) {
                val base = bar * 16
                for (s in 0 until 16) {
                    val kickPad = if (s % 2 == 0) DrumPadId.KICK_RIGHT else DrumPadId.KICK_LEFT
                    add(GrooveStep(base + s, kickPad, 0.90f))
                }
                for (s in 0 until 16 step 4) {
                    add(GrooveStep(base + s, DrumPadId.RIDE, 0.85f))
                }
                add(GrooveStep(base + 4, DrumPadId.SNARE, 0.96f))
                add(GrooveStep(base + 12, DrumPadId.SNARE, 0.96f))
            }
        }
    )

    val FOUR_ON_THE_FLOOR_HOUSE = GroovePattern(
        id = "groove-four-on-the-floor-house",
        title = "Four-on-the-Floor House",
        genre = "House / Electronic",
        bpm = 124,
        swingPercent = 8,
        stepsPerBar = 16,
        bars = 2,
        kitId = DrumKitId.SYNTHWAVE_ELECTRONIC,
        steps = buildList {
            for (bar in 0 until 2) {
                val base = bar * 16
                // Kick on every quarter-note beat (0, 4, 8, 12)
                for (beat in listOf(0, 4, 8, 12)) {
                    add(GrooveStep(base + beat, DrumPadId.KICK_RIGHT, 0.95f))
                }
                // Offbeat open hi-hat on 2, 6, 10, 14
                for (offbeat in listOf(2, 6, 10, 14)) {
                    add(GrooveStep(base + offbeat, DrumPadId.HI_HAT_OPEN, 0.82f))
                }
                // Closed hat on 16th pickups
                for (chokeStep in listOf(3, 7, 11, 15)) {
                    add(GrooveStep(base + chokeStep, DrumPadId.HI_HAT_CLOSED, 0.68f))
                }
                add(GrooveStep(base + 4, DrumPadId.SNARE, 0.90f))
                add(GrooveStep(base + 12, DrumPadId.SNARE, 0.92f))
            }
        }
    )

    val BOSSA_NOVA_CLAVE = GroovePattern(
        id = "groove-bossa-nova-clave",
        title = "Bossa Nova Clave",
        genre = "Latin / Bossa Nova",
        bpm = 108,
        swingPercent = 10,
        stepsPerBar = 16,
        bars = 2,
        kitId = DrumKitId.ACOUSTIC_STUDIO,
        steps = buildList {
            for (bar in 0 until 2) {
                val base = bar * 16
                for (s in 0 until 16 step 2) {
                    add(GrooveStep(base + s, DrumPadId.HI_HAT_CLOSED, 0.70f))
                }
                // Surdo-inspired kick ostinato on 1, 1a, 3, 3a
                for (kickStep in listOf(0, 3, 4, 7, 8, 11, 12, 15)) {
                    add(GrooveStep(base + kickStep, DrumPadId.KICK_RIGHT, 0.82f))
                }
                // Bossa cross-stick snare pattern
                val snareSteps = if (bar == 0) listOf(0, 3, 6, 10, 13) else listOf(2, 6, 9, 12)
                for (s in snareSteps) {
                    add(GrooveStep(base + s, DrumPadId.SNARE, 0.78f))
                }
            }
        }
    )

    val allGrooves: List<GroovePattern> = listOf(
        CLASSIC_ROCK_BACKBEAT,
        NEO_SOUL_BOOM_BAP,
        TRAP_HI_HAT_ROLL,
        FUNK_GHOST_NOTE_POCKET,
        POP_PUNK_DOUBLE_TIME,
        HEAVY_METAL_DOUBLE_KICK,
        FOUR_ON_THE_FLOOR_HOUSE,
        BOSSA_NOVA_CLAVE
    )

    fun findById(id: String): GroovePattern =
        allGrooves.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: CLASSIC_ROCK_BACKBEAT
}

/**
 * Pure-Kotlin timeline calculator computing millisecond target timestamps for groove steps
 * across BPM (`40..240`) and Swing (`0..75%` applied to odd 16th-note offbeats) (`Req 1.2`).
 */
object GrooveTimelineCalculator {

    const val MIN_BPM: Int = 40
    const val MAX_BPM: Int = 240
    const val MIN_SWING_PERCENT: Int = 0
    const val MAX_SWING_PERCENT: Int = 75

    fun clampBpm(bpm: Int): Int = bpm.coerceIn(MIN_BPM, MAX_BPM)

    fun clampSwingPercent(swingPercent: Int): Int =
        swingPercent.coerceIn(MIN_SWING_PERCENT, MAX_SWING_PERCENT)

    /**
     * Exact duration of a single straight step in milliseconds (16 steps per 4-beat bar).
     */
    fun stepDurationMs(bpm: Int, stepsPerBar: Int = 16): Double {
        val safeBpm = clampBpm(bpm)
        val safeStepsPerBar = stepsPerBar.coerceAtLeast(1)
        val quarterNoteMs = 60_000.0 / safeBpm.toDouble()
        val stepsPerBeat = safeStepsPerBar.toDouble() / 4.0
        return quarterNoteMs / stepsPerBeat
    }

    /**
     * Additional millisecond delay applied to odd 16th-note offbeats (`globalStepIndex % 2 == 1`).
     * Even downbeat/8th steps (`globalStepIndex % 2 == 0`) always receive `0.0` swing offset.
     */
    fun swingOffsetMs(globalStepIndex: Int, baseStepDurationMs: Double, swingPercent: Int): Double {
        if (globalStepIndex % 2 == 0) return 0.0
        val clampedSwing = clampSwingPercent(swingPercent)
        return baseStepDurationMs * (clampedSwing.toDouble() / 100.0)
    }

    /**
     * Computes the exact target timestamp in milliseconds for [globalStepIndex] relative to [startTimeMs].
     */
    fun computeStepTimestampMs(
        startTimeMs: Long,
        globalStepIndex: Int,
        bpm: Int,
        swingPercent: Int,
        stepsPerBar: Int = 16
    ): Long {
        val safeStep = globalStepIndex.coerceAtLeast(0)
        val baseStepMs = stepDurationMs(bpm = bpm, stepsPerBar = stepsPerBar)
        val straightOffsetMs = safeStep.toDouble() * baseStepMs
        val swingExtraMs = swingOffsetMs(
            globalStepIndex = safeStep,
            baseStepDurationMs = baseStepMs,
            swingPercent = swingPercent
        )
        return startTimeMs + (straightOffsetMs + swingExtraMs).roundToLong()
    }

    /**
     * Total duration of one full pattern loop (`stepsPerBar * bars` steps) in milliseconds.
     */
    fun patternLoopDurationMs(
        pattern: GroovePattern,
        bpmOverride: Int = pattern.bpm,
        stepsPerBar: Int = pattern.stepsPerBar
    ): Long {
        val baseStepMs = stepDurationMs(bpm = bpmOverride, stepsPerBar = stepsPerBar)
        return (pattern.totalSteps.toDouble() * baseStepMs).roundToLong()
    }

    /**
     * Expands a [GroovePattern] into sorted [ScheduledGrooveStep] instances across [passes] loop passes.
     */
    fun expandTimeline(
        pattern: GroovePattern,
        startTimeMs: Long = 0L,
        bpmOverride: Int = pattern.bpm,
        swingOverride: Int = pattern.swingPercent,
        passes: Int = 1
    ): List<ScheduledGrooveStep> {
        val safePasses = passes.coerceAtLeast(1)
        val safeStepsPerBar = pattern.stepsPerBar.coerceAtLeast(1)
        val stepsPerPass = pattern.totalSteps
        val scheduled = ArrayList<ScheduledGrooveStep>(pattern.steps.size * safePasses)

        for (pass in 0 until safePasses) {
            val passStepBase = pass * stepsPerPass
            for (step in pattern.steps) {
                val clampedLocalStep = step.stepIndex.coerceIn(0, stepsPerPass - 1)
                val globalStep = passStepBase + clampedLocalStep
                val barIndex = globalStep / safeStepsPerBar
                val expectedMs = computeStepTimestampMs(
                    startTimeMs = startTimeMs,
                    globalStepIndex = globalStep,
                    bpm = bpmOverride,
                    swingPercent = swingOverride,
                    stepsPerBar = safeStepsPerBar
                )
                scheduled.add(
                    ScheduledGrooveStep(
                        passIndex = pass,
                        barIndex = barIndex,
                        stepIndex = clampedLocalStep,
                        globalStepIndex = globalStep,
                        padId = step.padId,
                        velocity = step.velocity.coerceIn(0.1f, 1.0f),
                        expectedTimeMs = expectedMs
                    )
                )
            }
        }

        return scheduled.sortedWith(
            compareBy<ScheduledGrooveStep> { it.expectedTimeMs }
                .thenBy { it.padId.ordinal }
        )
    }
}
