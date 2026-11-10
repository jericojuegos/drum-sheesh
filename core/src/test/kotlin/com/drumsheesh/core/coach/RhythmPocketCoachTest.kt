package com.drumsheesh.core.coach

import com.drumsheesh.core.model.DrumPadId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RhythmPocketCoachTest {

    @Test
    fun `BuiltInGrooveCatalog exposes at least 8 distinct genre grooves with valid steps`() {
        val grooves = BuiltInGrooveCatalog.allGrooves
        assertTrue("Expected at least 8 curated genre grooves", grooves.size >= 8)

        val expectedIds = setOf(
            "groove-classic-rock",
            "groove-neo-soul-boom-bap",
            "groove-trap-hi-hat-roll",
            "groove-funk-ghost-pocket",
            "groove-pop-punk-double-time",
            "groove-heavy-metal-double-kick",
            "groove-four-on-the-floor-house",
            "groove-bossa-nova-clave"
        )
        val actualIds = grooves.map { it.id }.toSet()
        assertEquals(expectedIds, actualIds)

        for (groove in grooves) {
            assertTrue("Groove ${groove.id} must have non-empty steps", groove.steps.isNotEmpty())
            assertTrue(
                "Groove ${groove.id} BPM must be in 40..240",
                groove.bpm in GrooveTimelineCalculator.MIN_BPM..GrooveTimelineCalculator.MAX_BPM
            )
            assertTrue(
                "Groove ${groove.id} swing must be in 0..75",
                groove.swingPercent in GrooveTimelineCalculator.MIN_SWING_PERCENT..GrooveTimelineCalculator.MAX_SWING_PERCENT
            )
        }
    }

    @Test
    fun `GrooveTimelineCalculator computes exact straight and swung 16th-note timestamps and clamps bounds`() {
        // At 120 BPM, 1 beat = 500ms, 1 sixteenth step = 125ms
        val step0Straight = GrooveTimelineCalculator.computeStepTimestampMs(
            startTimeMs = 1_000L,
            globalStepIndex = 0,
            bpm = 120,
            swingPercent = 50
        )
        val step1Straight = GrooveTimelineCalculator.computeStepTimestampMs(
            startTimeMs = 1_000L,
            globalStepIndex = 1,
            bpm = 120,
            swingPercent = 0
        )
        val step1Swung50 = GrooveTimelineCalculator.computeStepTimestampMs(
            startTimeMs = 1_000L,
            globalStepIndex = 1,
            bpm = 120,
            swingPercent = 50
        )
        val step2Swung50 = GrooveTimelineCalculator.computeStepTimestampMs(
            startTimeMs = 1_000L,
            globalStepIndex = 2,
            bpm = 120,
            swingPercent = 50
        )

        assertEquals(1_000L, step0Straight)
        assertEquals(1_125L, step1Straight)
        // Odd step 1 with 50% swing adds 50% of 125ms = +63ms (1188ms)
        assertEquals(1_188L, step1Swung50)
        // Even step 2 is unaffected by swing (1000 + 2 * 125 = 1250ms)
        assertEquals(1_250L, step2Swung50)

        // Verify BPM [40..240] and Swing [0..75%] clamping
        assertEquals(40, GrooveTimelineCalculator.clampBpm(10))
        assertEquals(240, GrooveTimelineCalculator.clampBpm(999))
        assertEquals(0, GrooveTimelineCalculator.clampSwingPercent(-25))
        assertEquals(75, GrooveTimelineCalculator.clampSwingPercent(120))
    }

    @Test
    fun `RhythmPocketCoach classifies PERFECT GOOD EARLY LATE and MISS boundaries accurately`() {
        assertEquals(HitClassification.PERFECT, RhythmPocketCoach.classifyDeltaMs(0L))
        assertEquals(HitClassification.PERFECT, RhythmPocketCoach.classifyDeltaMs(-20L))
        assertEquals(HitClassification.PERFECT, RhythmPocketCoach.classifyDeltaMs(20L))

        assertEquals(HitClassification.GOOD, RhythmPocketCoach.classifyDeltaMs(-21L))
        assertEquals(HitClassification.GOOD, RhythmPocketCoach.classifyDeltaMs(45L))

        assertEquals(HitClassification.EARLY, RhythmPocketCoach.classifyDeltaMs(-46L))
        assertEquals(HitClassification.EARLY, RhythmPocketCoach.classifyDeltaMs(-90L))

        assertEquals(HitClassification.LATE, RhythmPocketCoach.classifyDeltaMs(46L))
        assertEquals(HitClassification.LATE, RhythmPocketCoach.classifyDeltaMs(90L))

        assertEquals(HitClassification.MISS, RhythmPocketCoach.classifyDeltaMs(-91L))
        assertEquals(HitClassification.MISS, RhythmPocketCoach.classifyDeltaMs(91L))
    }

    @Test
    fun `RhythmPocketCoach detects LOCKED_IN RUSHING and DRAGGING tendencies and identifies weakest pad`() {
        val expected = listOf(
            ScheduledGrooveStep(0, 0, 0, 0, DrumPadId.KICK_RIGHT, 0.9f, 1_000L),
            ScheduledGrooveStep(0, 0, 4, 4, DrumPadId.SNARE, 0.9f, 1_500L),
            ScheduledGrooveStep(0, 0, 8, 8, DrumPadId.KICK_RIGHT, 0.9f, 2_000L),
            ScheduledGrooveStep(0, 0, 12, 12, DrumPadId.SNARE, 0.9f, 2_500L)
        )

        // Case 1: Rushing session (Kick is tight -4ms, Snare rushes early by -52ms)
        val rushingHits = listOf(
            ActualPadHit(DrumPadId.KICK_RIGHT, 996L),   // -4ms (PERFECT)
            ActualPadHit(DrumPadId.SNARE, 1_448L),      // -52ms (EARLY)
            ActualPadHit(DrumPadId.KICK_RIGHT, 1_996L), // -4ms (PERFECT)
            ActualPadHit(DrumPadId.SNARE, 2_444L)       // -56ms (EARLY)
        )
        val rushingReport = RhythmPocketCoach.evaluate(expected, rushingHits)
        assertEquals(TimingTendency.RUSHING, rushingReport.tendency)
        assertTrue(rushingReport.meanDriftMs < -8)
        assertEquals(DrumPadId.SNARE, rushingReport.weakestPad)
        assertEquals(75, rushingReport.pocketScore) // (100 + 50 + 100 + 50) / 4 = 75
        assertTrue(rushingReport.coachTip.contains("SNARE"))
        assertEquals(
            "Coach tip must be 2 sentences",
            2,
            rushingReport.coachTip.split(". ").filter { it.isNotBlank() }.size
        )

        // Case 2: Dragging session (Hits arrive +30ms to +60ms late)
        val draggingHits = listOf(
            ActualPadHit(DrumPadId.KICK_RIGHT, 1_030L), // +30ms (GOOD)
            ActualPadHit(DrumPadId.SNARE, 1_560L),      // +60ms (LATE)
            ActualPadHit(DrumPadId.KICK_RIGHT, 2_025L), // +25ms (GOOD)
            ActualPadHit(DrumPadId.SNARE, 2_555L)       // +55ms (LATE)
        )
        val draggingReport = RhythmPocketCoach.evaluate(expected, draggingHits)
        assertEquals(TimingTendency.DRAGGING, draggingReport.tendency)
        assertTrue(draggingReport.meanDriftMs > 8)
        assertEquals(DrumPadId.SNARE, draggingReport.weakestPad)

        // Case 3: Locked-in session (all hits within ±6ms)
        val lockedInHits = listOf(
            ActualPadHit(DrumPadId.KICK_RIGHT, 998L),
            ActualPadHit(DrumPadId.SNARE, 1_504L),
            ActualPadHit(DrumPadId.KICK_RIGHT, 2_002L),
            ActualPadHit(DrumPadId.SNARE, 2_496L)
        )
        val lockedInReport = RhythmPocketCoach.evaluate(expected, lockedInHits)
        assertEquals(TimingTendency.LOCKED_IN, lockedInReport.tendency)
        assertEquals(100, lockedInReport.pocketScore)
        assertEquals(4, lockedInReport.perfectCount)
        assertNotNull(lockedInReport.weakestPad)
    }

    @Test
    fun `RhythmPocketCoach enforces 0 to 100 score bounds on empty hits and duplicate taps`() {
        val expected = BuiltInGrooveCatalog.CLASSIC_ROCK_BACKBEAT.let {
            GrooveTimelineCalculator.expandTimeline(it, startTimeMs = 0L, passes = 1)
        }

        // Zero hits -> 0 score, all MISS
        val emptyReport = RhythmPocketCoach.evaluate(expected, emptyList())
        assertEquals(0, emptyReport.pocketScore)
        assertEquals(expected.size, emptyReport.missCount)
        assertEquals(TimingTendency.LOCKED_IN, emptyReport.tendency)

        // Duplicate hits on the same step cannot inflate score above 100
        val duplicatePerfectHits = expected.flatMap { step ->
            listOf(
                ActualPadHit(step.padId, step.expectedTimeMs),
                ActualPadHit(step.padId, step.expectedTimeMs + 2L)
            )
        }
        val duplicateReport = RhythmPocketCoach.evaluate(expected, duplicatePerfectHits)
        assertTrue(duplicateReport.pocketScore in 0..100)
        assertEquals(100, duplicateReport.pocketScore)
    }

    @Test
    fun `judgeLiveHit and formatJudgementBadge produce deterministic HUD badge strings`() {
        val timeline = listOf(
            ScheduledGrooveStep(0, 0, 4, 4, DrumPadId.SNARE, 0.9f, 1_000L)
        )

        val perfect = RhythmPocketCoach.judgeLiveHit(DrumPadId.SNARE, 996L, timeline)
        assertEquals("PERFECT -4ms", RhythmPocketCoach.formatJudgementBadge(perfect))

        val late = RhythmPocketCoach.judgeLiveHit(DrumPadId.SNARE, 1_062L, timeline)
        assertEquals("LATE +62ms", RhythmPocketCoach.formatJudgementBadge(late))

        val missOutsideWindow = RhythmPocketCoach.judgeLiveHit(DrumPadId.SNARE, 1_300L, timeline)
        assertEquals("MISS", RhythmPocketCoach.formatJudgementBadge(missOutsideWindow))
    }
}
