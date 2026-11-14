package com.drumsheesh.core.engine

import com.drumsheesh.core.audio.ProceduralDrumSynthesizer
import com.drumsheesh.core.model.DrumKitId
import com.drumsheesh.core.model.DrumPadId
import com.drumsheesh.core.model.DrumSoundProfile
import com.drumsheesh.core.model.SnareArticulation
import com.drumsheesh.core.model.SnareVelocityTier
import com.drumsheesh.core.model.SnareZone
import com.drumsheesh.core.model.SoundCharacterPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SnareInteractionEngineTest {

    @Test
    fun `resolveStrike maps center, edge, upper rimshot, and lower cross-stick zones`() {
        val centerHard = SnareInteractionEngine.resolveStrike(radialDistance = 0.05f, dyNormalized = 0f, roundRobinStepOverride = 0)
        assertEquals(SnareZone.CENTER_HEAD, centerHard.zone)
        assertEquals(SnareArticulation.CENTER_HARD, centerHard.articulation)
        assertEquals(SnareVelocityTier.FORTISSIMO_IMPACT, centerHard.velocityTier)

        val centerSoft = SnareInteractionEngine.resolveStrike(radialDistance = 0.42f, dyNormalized = 0f, roundRobinStepOverride = 0)
        assertEquals(SnareZone.CENTER_HEAD, centerSoft.zone)
        assertEquals(SnareArticulation.CENTER_SOFT, centerSoft.articulation)

        val edgeHit = SnareInteractionEngine.resolveStrike(radialDistance = 0.62f, dyNormalized = 0f, roundRobinStepOverride = 0)
        assertEquals(SnareZone.EDGE_HEAD, edgeHit.zone)
        assertEquals(SnareArticulation.EDGE_CRISP, edgeHit.articulation)

        val outerGhost = SnareInteractionEngine.resolveStrike(radialDistance = 0.76f, dyNormalized = 0f, roundRobinStepOverride = 0)
        assertEquals(SnareZone.EDGE_HEAD, outerGhost.zone)
        assertEquals(SnareArticulation.GHOST_NOTE, outerGhost.articulation)
        assertEquals(SnareVelocityTier.PIANISSIMO_GHOST, outerGhost.velocityTier)

        val rimshot = SnareInteractionEngine.resolveStrike(radialDistance = 0.90f, dyNormalized = -0.85f, roundRobinStepOverride = 0)
        assertEquals(SnareZone.RIM_SIDE, rimshot.zone)
        assertEquals(SnareArticulation.RIMSHOT, rimshot.articulation)

        val crossStick = SnareInteractionEngine.resolveStrike(radialDistance = 0.90f, dyNormalized = 0.85f, roundRobinStepOverride = 0)
        assertEquals(SnareZone.RIM_SIDE, crossStick.zone)
        assertEquals(SnareArticulation.CROSS_STICK, crossStick.articulation)
    }

    @Test
    fun `touch pressure overrides center radial velocity for soft ghost notes and round-robin alternates`() {
        val gentleCenterTap = SnareInteractionEngine.resolveStrike(
            radialDistance = 0.10f,
            dyNormalized = 0f,
            touchPressure = 0.08f,
            roundRobinStepOverride = 0
        )
        val nextGentleTap = SnareInteractionEngine.resolveStrike(
            radialDistance = 0.10f,
            dyNormalized = 0f,
            touchPressure = 0.08f,
            roundRobinStepOverride = 1
        )

        assertTrue(gentleCenterTap.velocity < 0.50f)
        assertEquals(0, gentleCenterTap.roundRobinIndex)
        assertEquals(1, nextGentleTap.roundRobinIndex)
        assertTrue(gentleCenterTap.pitchJitterRate != nextGentleTap.pitchJitterRate)
        assertTrue(gentleCenterTap.pitchJitterRate in 0.98f..1.02f)
    }

    @Test
    fun `PadHitResolver populates snareHit metadata for snare stage coordinates`() {
        val snareCenter = PadHitResolver.resolveHit(xNorm = 0.48f, yNorm = 0.52f)
        assertNotNull(snareCenter)
        assertEquals(DrumPadId.SNARE, snareCenter!!.padId)
        assertEquals(SnareArticulation.CENTER_HARD, snareCenter.snareHit?.articulation)

        val snareTopRim = PadHitResolver.resolveHit(xNorm = 0.48f, yNorm = 0.355f)
        assertNotNull(snareTopRim)
        assertEquals(DrumPadId.SNARE, snareTopRim!!.padId)
        assertEquals(SnareArticulation.RIMSHOT, snareTopRim.snareHit?.articulation)

        val snareBottomRim = PadHitResolver.resolveHit(xNorm = 0.48f, yNorm = 0.685f)
        assertNotNull(snareBottomRim)
        assertEquals(DrumPadId.SNARE, snareBottomRim!!.padId)
        assertEquals(SnareArticulation.CROSS_STICK, snareBottomRim.snareHit?.articulation)
    }

    @Test
    fun `all 6 snare articulations synthesize valid 44_1kHz WAVs across all kits and process real WAVs`() {
        for (kitId in DrumKitId.entries) {
            for (articulation in SnareArticulation.entries) {
                val wav0 = ProceduralDrumSynthesizer.synthesizeSnareArticulationWavBytes(
                    kitId = kitId,
                    articulation = articulation,
                    variantIndex = 0
                )
                val wav1 = ProceduralDrumSynthesizer.synthesizeSnareArticulationWavBytes(
                    kitId = kitId,
                    articulation = articulation,
                    variantIndex = 1
                )

                assertEquals("RIFF", String(wav0, 0, 4, Charsets.US_ASCII))
                assertEquals("WAVE", String(wav0, 8, 4, Charsets.US_ASCII))
                val sr = ByteBuffer.wrap(wav0, 24, 4).order(ByteOrder.LITTLE_ENDIAN).int
                assertEquals(ProceduralDrumSynthesizer.SAMPLE_RATE_HZ, sr)
                assertTrue(ProceduralDrumSynthesizer.computeWavRms(wav0) > 0.01)
                assertFalse(wav0.contentEquals(wav1))
            }
        }

        val sampleBase = DrumSoundProfile.defaultForKit(DrumKitId.ACOUSTIC_STUDIO)
        val drySnare = ProceduralDrumSynthesizer.synthesizeWavBytes(
            kitId = DrumKitId.ACOUSTIC_STUDIO,
            padId = DrumPadId.SNARE,
            profile = SoundCharacterPreset.TIGHT_DRY_POP.toProfile()
        )
        val shapedRimshot = ProceduralDrumSynthesizer.processSnareWavWithProfile(
            wavBytes = drySnare,
            articulation = SnareArticulation.RIMSHOT,
            profile = sampleBase
        )
        assertEquals("RIFF", String(shapedRimshot, 0, 4, Charsets.US_ASCII))
        assertTrue(ProceduralDrumSynthesizer.computeWavRms(shapedRimshot) > 0.01)
    }
}
