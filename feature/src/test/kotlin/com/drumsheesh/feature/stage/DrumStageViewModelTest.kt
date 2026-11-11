package com.drumsheesh.feature.stage

import com.drumsheesh.core.engine.PadHitResolver
import com.drumsheesh.core.model.DrumKitId
import com.drumsheesh.core.model.DrumPadId
import com.drumsheesh.core.port.ClockPort
import com.drumsheesh.core.port.DrumAudioPort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrumStageViewModelTest {

    private class FakeDrumAudioPort : DrumAudioPort {
        val preloadedKits = mutableListOf<DrumKitId>()
        val triggeredHits = mutableListOf<Triple<DrumKitId, DrumPadId, Float>>()
        var stopAllCalledCount = 0

        override fun preloadKit(kitId: DrumKitId) {
            preloadedKits.add(kitId)
        }

        override fun triggerPad(kitId: DrumKitId, padId: DrumPadId, velocity: Float) {
            triggeredHits.add(Triple(kitId, padId, velocity))
        }

        override fun stopAllStreams() {
            stopAllCalledCount++
        }

        override fun release() = Unit
    }

    @Test
    fun `pointer touch down on Snare immediately triggers audio and records ripple state`() {
        val fakeAudio = FakeDrumAudioPort()
        var fakeTime = 1_000L
        val viewModel = DrumStageViewModel(
            drumAudioPort = fakeAudio,
            clockPort = ClockPort { fakeTime }
        )

        val snareZone = PadHitResolver.getZonesForHandedness(isLeftHanded = false)
            .first { it.padId == DrumPadId.SNARE }

        viewModel.onEvent(
            DrumStageUiEvent.PointerTouchDown(
                xNorm = snareZone.centerX,
                yNorm = snareZone.centerY
            )
        )

        assertEquals(1, fakeAudio.triggeredHits.size)
        val (kit, pad, velocity) = fakeAudio.triggeredHits.first()
        assertEquals(DrumKitId.ACOUSTIC_STUDIO, kit)
        assertEquals(DrumPadId.SNARE, pad)
        assertEquals(1.0f, velocity, 0.001f)

        val state = viewModel.uiState.value
        assertEquals(DrumPadId.SNARE, state.lastTriggeredPad)
        assertEquals(1, state.totalHitsInSession)
        assertEquals(1, state.activeRipples.size)

        // Advance animation frame past 140ms impulse decay
        fakeTime = 1_200L
        viewModel.onEvent(DrumStageUiEvent.StepAnimationFrame(fakeTime))
        assertTrue(viewModel.uiState.value.activeRipples.isEmpty())
    }

    @Test
    fun `pointer touch on empty floor space does not trigger audio or mutate hit count`() {
        val fakeAudio = FakeDrumAudioPort()
        val viewModel = DrumStageViewModel(drumAudioPort = fakeAudio)

        viewModel.onEvent(DrumStageUiEvent.PointerTouchDown(0.01f, 0.01f))

        assertTrue(fakeAudio.triggeredHits.isEmpty())
        assertNull(viewModel.uiState.value.lastTriggeredPad)
        assertEquals(0, viewModel.uiState.value.totalHitsInSession)
    }

    @Test
    fun `triggering HI_HAT_CLOSED while HI_HAT_OPEN is ringing activates choke state`() {
        val fakeAudio = FakeDrumAudioPort()
        var now = 2_000L
        val viewModel = DrumStageViewModel(
            drumAudioPort = fakeAudio,
            clockPort = ClockPort { now }
        )

        viewModel.onEvent(DrumStageUiEvent.DirectPadTrigger(DrumPadId.HI_HAT_OPEN, 0.9f))
        assertTrue(viewModel.uiState.value.openHiHatRinging)
        assertFalse(viewModel.uiState.value.isChokeFlashActive)

        now = 2_050L
        viewModel.onEvent(DrumStageUiEvent.DirectPadTrigger(DrumPadId.HI_HAT_CLOSED, 0.85f))
        val chokedState = viewModel.uiState.value
        assertFalse(chokedState.openHiHatRinging)
        assertTrue(chokedState.isChokeFlashActive)
        assertEquals(2_050L, chokedState.lastChokeAtMs)

        // Advance past choke flash duration (220ms)
        now = 2_350L
        viewModel.onEvent(DrumStageUiEvent.StepAnimationFrame(now))
        assertFalse(viewModel.uiState.value.isChokeFlashActive)
    }

    @Test
    fun `toggling Left-Handed mode mirrors pad zones and selecting kit updates BPM and preloads audio`() {
        val fakeAudio = FakeDrumAudioPort()
        val viewModel = DrumStageViewModel(drumAudioPort = fakeAudio)

        val initialCrashX = viewModel.uiState.value.padZones.first { it.padId == DrumPadId.CRASH }.centerX
        viewModel.onEvent(DrumStageUiEvent.ToggleHandedness)

        val mirroredState = viewModel.uiState.value
        assertTrue(mirroredState.isLeftHanded)
        val mirroredCrashX = mirroredState.padZones.first { it.padId == DrumPadId.CRASH }.centerX
        assertEquals(1.0f - initialCrashX, mirroredCrashX, 0.001f)

        // Touch the mirrored Crash coordinate in Left-Handed mode
        val crashZone = mirroredState.padZones.first { it.padId == DrumPadId.CRASH }
        viewModel.onEvent(DrumStageUiEvent.PointerTouchDown(crashZone.centerX, crashZone.centerY))
        assertNotNull(viewModel.uiState.value.lastTriggeredPad)
        assertEquals(DrumPadId.CRASH, viewModel.uiState.value.lastTriggeredPad)

        // Switch kit to 808 Trap
        viewModel.onEvent(DrumStageUiEvent.SelectKit(DrumKitId.TRAP_808))
        assertEquals(DrumKitId.TRAP_808, viewModel.uiState.value.selectedKit)
        assertEquals(DrumKitId.TRAP_808.defaultBpm, viewModel.uiState.value.bpm)
        assertTrue(fakeAudio.preloadedKits.contains(DrumKitId.TRAP_808))

        // Clamp BPM bounds [40..240]
        viewModel.onEvent(DrumStageUiEvent.AdjustBpm(-500))
        assertEquals(DrumStageViewModel.MIN_BPM, viewModel.uiState.value.bpm)
        viewModel.onEvent(DrumStageUiEvent.AdjustBpm(500))
        assertEquals(DrumStageViewModel.MAX_BPM, viewModel.uiState.value.bpm)
    }
}
