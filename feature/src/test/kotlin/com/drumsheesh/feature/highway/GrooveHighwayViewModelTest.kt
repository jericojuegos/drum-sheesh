package com.drumsheesh.feature.highway

import com.drumsheesh.core.coach.BuiltInGrooveCatalog
import com.drumsheesh.core.coach.HitClassification
import com.drumsheesh.core.coach.TimingTendency
import com.drumsheesh.core.model.DrumPadId
import com.drumsheesh.core.port.ClockPort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GrooveHighwayViewModelTest {

    @Test
    fun `selecting groove updates BPM swing and timeline and clamps adjustments`() {
        var fakeNow = 5_000L
        val viewModel = GrooveHighwayViewModel(clockPort = ClockPort { fakeNow })

        assertEquals(8, viewModel.uiState.value.availableGrooves.size)
        assertEquals(BuiltInGrooveCatalog.CLASSIC_ROCK_BACKBEAT.id, viewModel.uiState.value.selectedGroove.id)

        // Select 92 BPM Neo-Soul Boom Bap (18% swing)
        viewModel.onEvent(GrooveHighwayUiEvent.SelectGroove(BuiltInGrooveCatalog.NEO_SOUL_BOOM_BAP.id))
        val neoSoulState = viewModel.uiState.value
        assertEquals(92, neoSoulState.bpm)
        assertEquals(18, neoSoulState.swingPercent)
        assertTrue(neoSoulState.scheduledTimeline.isNotEmpty())

        // Clamp BPM [40..240] and Swing [0..75%]
        viewModel.onEvent(GrooveHighwayUiEvent.AdjustBpm(-500))
        assertEquals(40, viewModel.uiState.value.bpm)
        viewModel.onEvent(GrooveHighwayUiEvent.AdjustBpm(500))
        assertEquals(240, viewModel.uiState.value.bpm)

        viewModel.onEvent(GrooveHighwayUiEvent.AdjustSwing(-200))
        assertEquals(0, viewModel.uiState.value.swingPercent)
        viewModel.onEvent(GrooveHighwayUiEvent.AdjustSwing(200))
        assertEquals(75, viewModel.uiState.value.swingPercent)
    }

    @Test
    fun `playing groove recording hits and finishing generates PocketCoachReport and supports SlowTempoAndRetry`() {
        var fakeNow = 10_000L
        val viewModel = GrooveHighwayViewModel(clockPort = ClockPort { fakeNow })

        // Start playback at t = 10_000L
        viewModel.onEvent(GrooveHighwayUiEvent.TogglePlayback)
        assertTrue(viewModel.uiState.value.isPlaying)

        val timeline = viewModel.uiState.value.scheduledTimeline
        assertTrue(timeline.isNotEmpty())

        // Hit every expected step slightly early (-12ms) except Snare which is rushed by -48ms
        for (step in timeline) {
            val offset = if (step.padId == DrumPadId.SNARE) -48L else -12L
            fakeNow = step.expectedTimeMs + offset
            viewModel.onEvent(
                GrooveHighwayUiEvent.RecordUserPadHit(
                    padId = step.padId,
                    velocity = 0.9f,
                    hitTimeMs = fakeNow
                )
            )
        }

        val lastJudgement = viewModel.uiState.value.lastJudgement
        assertNotNull(lastJudgement)
        assertTrue(viewModel.uiState.value.isNowLineFlashing)

        // Finish session & evaluate Coach Report
        viewModel.onEvent(GrooveHighwayUiEvent.FinishAndCoach)
        val coachedState = viewModel.uiState.value
        assertFalse(coachedState.isPlaying)
        assertTrue(coachedState.isCoachModalVisible)

        val report = coachedState.coachReport
        assertNotNull(report)
        assertEquals(TimingTendency.RUSHING, report!!.tendency)
        assertEquals(DrumPadId.SNARE, report.weakestPad)
        assertTrue(report.pocketScore in 1..100)
        assertTrue(report.coachTip.contains("SNARE"))

        // Trigger Slow Tempo -10 BPM retry from modal
        val initialBpm = coachedState.bpm
        fakeNow = 25_000L
        viewModel.onEvent(GrooveHighwayUiEvent.SlowTempoAndRetry)
        val retryState = viewModel.uiState.value
        assertEquals(initialBpm - 10, retryState.bpm)
        assertTrue(retryState.isPlaying)
        assertFalse(retryState.isCoachModalVisible)
        assertTrue(retryState.recordedHits.isEmpty())
        assertEquals(HitClassification.PERFECT, com.drumsheesh.core.coach.RhythmPocketCoach.classifyDeltaMs(-12L))
    }
}
