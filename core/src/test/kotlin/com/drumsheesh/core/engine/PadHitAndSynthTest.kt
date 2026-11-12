package com.drumsheesh.core.engine

import com.drumsheesh.core.audio.ProceduralDrumSynthesizer
import com.drumsheesh.core.model.DrumKitId
import com.drumsheesh.core.model.DrumPadId
import com.drumsheesh.core.model.DrumSoundProfile
import com.drumsheesh.core.model.PadMixSetting
import com.drumsheesh.core.model.RoomAmbiencePreset
import com.drumsheesh.core.model.SoundCharacterPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PadHitAndSynthTest {

    @Test
    fun `PadHitResolver resolves dead-center hit with max velocity 1_0 and outer edge near 0_45`() {
        val zones = PadHitResolver.getZonesForHandedness(isLeftHanded = false)
        assertEquals(10, zones.size)

        val snareZone = zones.first { it.padId == DrumPadId.SNARE }

        // Dead-center hit on Snare
        val centerHit = PadHitResolver.resolveHit(
            xNorm = snareZone.centerX,
            yNorm = snareZone.centerY,
            isLeftHanded = false
        )
        assertNotNull(centerHit)
        assertEquals(DrumPadId.SNARE, centerHit!!.padId)
        assertEquals(1.0f, centerHit.velocity, 0.001f)

        // Outer rim hit on Snare (95% toward right edge)
        val rimHit = PadHitResolver.resolveHit(
            xNorm = snareZone.centerX + snareZone.radiusX * 0.95f,
            yNorm = snareZone.centerY,
            isLeftHanded = false
        )
        assertNotNull(rimHit)
        assertEquals(DrumPadId.SNARE, rimHit!!.padId)
        assertTrue(
            "Expected outer rim velocity in [0.45..0.55], got ${rimHit.velocity}",
            rimHit.velocity in 0.45f..0.55f
        )
    }

    @Test
    fun `PadHitResolver returns null for empty stage floor or out-of-bounds coordinates`() {
        // Corner outside all pads
        assertNull(PadHitResolver.resolveHit(0.01f, 0.01f, isLeftHanded = false))
        assertNull(PadHitResolver.resolveHit(-0.1f, 0.5f, isLeftHanded = false))
        assertNull(PadHitResolver.resolveHit(1.2f, 0.5f, isLeftHanded = false))
    }

    @Test
    fun `PadHitResolver mirrors horizontal coordinates in Left-Handed mode`() {
        val rightHandZones = PadHitResolver.getZonesForHandedness(isLeftHanded = false)
        for (zone in rightHandZones) {
            val rightHandHit = PadHitResolver.resolveHit(
                xNorm = zone.centerX,
                yNorm = zone.centerY,
                isLeftHanded = false
            )
            val leftHandMirroredHit = PadHitResolver.resolveHit(
                xNorm = 1.0f - zone.centerX,
                yNorm = zone.centerY,
                isLeftHanded = true
            )
            assertNotNull("Expected right-hand hit for ${zone.padId}", rightHandHit)
            assertNotNull("Expected mirrored left-hand hit for ${zone.padId}", leftHandMirroredHit)
            assertEquals(rightHandHit!!.padId, leftHandMirroredHit!!.padId)
            assertEquals(rightHandHit.velocity, leftHandMirroredHit.velocity, 0.001f)
        }
    }

    @Test
    fun `HiHatChokeTracker returns active open hi-hat streams when closed hi-hat is triggered`() {
        val tracker = HiHatChokeTracker()
        tracker.recordOpenHiHatStream(101)
        tracker.recordOpenHiHatStream(102)
        assertEquals(2, tracker.activeOpenStreamCount())

        // Triggering Snare does NOT choke open hi-hat
        val snareChoked = tracker.onPadTriggered(DrumPadId.SNARE)
        assertTrue(snareChoked.isEmpty())
        assertEquals(2, tracker.activeOpenStreamCount())

        // Triggering HI_HAT_CLOSED immediately chokes all active open hi-hat streams
        val closedChoked = tracker.onPadTriggered(DrumPadId.HI_HAT_CLOSED)
        assertEquals(listOf(101, 102), closedChoked)
        assertEquals(0, tracker.activeOpenStreamCount())
    }

    @Test
    fun `ProceduralDrumSynthesizer generates valid 44_1kHz 16-bit RIFF WAV buffers with non-zero RMS for all 40 kit-pad combinations`() {
        var totalVerified = 0
        for (kitId in DrumKitId.entries) {
            val kitMap = ProceduralDrumSynthesizer.synthesizeKitWavMap(kitId)
            assertEquals(10, kitMap.size)

            for (padId in DrumPadId.entries) {
                val wav = kitMap[padId]
                assertNotNull("Missing WAV for $kitId / $padId", wav)
                val bytes = wav!!
                assertTrue("WAV too short for $kitId / $padId", bytes.size > 1000)

                // Verify RIFF and WAVE magic headers
                val riffHeader = String(bytes, 0, 4, Charsets.US_ASCII)
                val waveHeader = String(bytes, 8, 4, Charsets.US_ASCII)
                val fmtHeader = String(bytes, 12, 4, Charsets.US_ASCII)
                val dataHeader = String(bytes, 36, 4, Charsets.US_ASCII)

                assertEquals("RIFF", riffHeader)
                assertEquals("WAVE", waveHeader)
                assertEquals("fmt ", fmtHeader)
                assertEquals("data", dataHeader)

                val sampleRate = ByteBuffer.wrap(bytes, 24, 4).order(ByteOrder.LITTLE_ENDIAN).int
                assertEquals(ProceduralDrumSynthesizer.SAMPLE_RATE_HZ, sampleRate)

                val rms = ProceduralDrumSynthesizer.computeWavRms(bytes)
                assertTrue(
                    "Expected non-zero acoustic RMS energy for $kitId / $padId, got $rms",
                    rms > 0.01
                )
                totalVerified++
            }
        }
        assertEquals(40, totalVerified)
    }

    @Test
    fun `DrumSoundProfile room ambience and character presets distinctly shape synthesized WAV buffers`() {
        val dryWav = ProceduralDrumSynthesizer.synthesizeWavBytes(
            kitId = DrumKitId.ACOUSTIC_STUDIO,
            padId = DrumPadId.SNARE,
            profile = SoundCharacterPreset.TIGHT_DRY_POP.toProfile()
        )
        val arenaWav = ProceduralDrumSynthesizer.synthesizeWavBytes(
            kitId = DrumKitId.ACOUSTIC_STUDIO,
            padId = DrumPadId.SNARE,
            profile = SoundCharacterPreset.ARENA_ROCK.toProfile()
        )

        assertTrue(arenaWav.size > dryWav.size)
        assertFalse(dryWav.contentEquals(arenaWav))
        assertTrue(ProceduralDrumSynthesizer.computeWavRms(dryWav) > 0.01)
        assertTrue(ProceduralDrumSynthesizer.computeWavRms(arenaWav) > 0.01)

        // PadMixSetting clamps volume and semitones cleanly to SoundPool playback bounds
        val extreme = PadMixSetting(volume = 5.0f, pitchSemitones = 24.0f)
        assertEquals(PadMixSetting.MAX_VOLUME, extreme.clampedVolume, 0.001f)
        assertEquals(PadMixSetting.MAX_SEMITONES, extreme.clampedPitchSemitones, 0.001f)
        assertTrue(extreme.playbackRate in 0.5f..2.0f)
        assertEquals(RoomAmbiencePreset.ARENA_VERB, SoundCharacterPreset.ARENA_ROCK.toProfile().roomPreset)
    }
}
