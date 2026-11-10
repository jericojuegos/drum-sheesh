package com.drumsheesh.core.ai

import com.drumsheesh.core.model.DrumKitId
import com.drumsheesh.core.model.DrumPadId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiGrooveAndQuantizerTest {

    @Test
    fun `GrooveJsonCodec round-trips GroovePattern and strips markdown code fences`() {
        val original = ProceduralGrooveGenerator.generate(
            AiGrooveRequest(
                prompt = "94 BPM J Dilla neo-soul boom bap with swung hi-hats and ghost snares",
                targetKit = DrumKitId.VINTAGE_BREAKBEAT
            )
        )

        val encoded = GrooveJsonCodec.encodeGroovePattern(original)
        val wrappedInFence = "```json\n$encoded\n```"

        val decoded = GrooveJsonCodec.decodeGroovePattern(wrappedInFence)

        assertEquals(original.id, decoded.id)
        assertEquals(original.title, decoded.title)
        assertEquals(94, decoded.bpm)
        assertEquals(original.swingPercent, decoded.swingPercent)
        assertEquals(original.stepsPerBar, decoded.stepsPerBar)
        assertEquals(original.bars, decoded.bars)
        assertEquals(DrumKitId.VINTAGE_BREAKBEAT, decoded.kitId)
        assertEquals(original.steps.size, decoded.steps.size)
        assertEquals(original.steps.first().padId, decoded.steps.first().padId)
    }

    @Test
    fun `GrooveJsonCodec clamps out-of-range BPM, swingPercent, velocity, and stepIndex`() {
        val rawOutOfBoundsJson = """
            {
              "id": "extreme-groove",
              "title": "Extreme Bounds Test",
              "bpm": 999,
              "swingPercent": 120,
              "stepsPerBar": 16,
              "bars": 2,
              "kitId": "808-trap",
              "steps": [
                { "stepIndex": -5, "padId": "KICK_RIGHT", "velocity": 2.5 },
                { "stepIndex": 88, "padId": "SNARE", "velocity": 0.01 },
                { "stepIndex": 4, "padId": "CLOSED_HI_HAT", "velocity": 0.80 }
              ]
            }
        """.trimIndent()

        val decoded = GrooveJsonCodec.decodeGroovePattern(rawOutOfBoundsJson, GrooveSource.CLOUD_AI)

        assertEquals(GroovePattern.MAX_BPM, decoded.bpm)
        assertEquals(GroovePattern.MAX_SWING_PERCENT, decoded.swingPercent)
        assertEquals(DrumKitId.TRAP_808, decoded.kitId)
        assertEquals(GrooveSource.CLOUD_AI, decoded.source)
        assertTrue(decoded.steps.all { it.stepIndex in 0 until decoded.totalSteps })
        assertTrue(decoded.steps.all { it.velocity in GroovePattern.MIN_VELOCITY..GroovePattern.MAX_VELOCITY })
        assertTrue(decoded.steps.any { it.padId == DrumPadId.HI_HAT_CLOSED && it.stepIndex == 4 })
    }

    @Test
    fun `ProceduralGrooveGenerator extracts BPM, swing, and genre archetypes from prompts`() {
        val boomBap = ProceduralGrooveGenerator.generate(
            AiGrooveRequest("94 BPM J Dilla neo-soul boom bap with swung hi-hats and ghost snares")
        )
        assertEquals(94, boomBap.bpm)
        assertTrue(boomBap.swingPercent >= 20)
        assertEquals(DrumKitId.VINTAGE_BREAKBEAT, boomBap.kitId)
        assertTrue(boomBap.steps.any { it.padId == DrumPadId.SNARE && it.velocity < 0.60f }) // Ghost snare
        assertTrue(boomBap.steps.all { it.stepIndex in 0 until boomBap.totalSteps })

        val amenBreak = ProceduralGrooveGenerator.generate(
            AiGrooveRequest("165 BPM drum and bass amen break")
        )
        assertEquals(165, amenBreak.bpm)
        assertEquals(0, amenBreak.swingPercent)
        assertTrue(amenBreak.steps.any { it.padId == DrumPadId.RIDE })

        val trap = ProceduralGrooveGenerator.generate(
            AiGrooveRequest("140 BPM 808 trap roll")
        )
        assertEquals(140, trap.bpm)
        assertEquals(DrumKitId.TRAP_808, trap.kitId)

        val metal = ProceduralGrooveGenerator.generate(
            AiGrooveRequest("300 BPM double-bass thrash metal")
        )
        // Clamped to 240 max BPM
        assertEquals(GroovePattern.MAX_BPM, metal.bpm)
        assertTrue(metal.steps.any { it.padId == DrumPadId.KICK_LEFT })
        assertTrue(metal.steps.any { it.padId == DrumPadId.KICK_RIGHT })
    }

    @Test
    fun `AiGrooveRequest validatePrompt rejects blank or short prompts under 3 chars`() {
        assertNotNull(AiGrooveRequest.validatePrompt(""))
        assertNotNull(AiGrooveRequest.validatePrompt("   "))
        assertNotNull(AiGrooveRequest.validatePrompt("ab"))
        assertNotNull(AiGrooveRequest.validatePrompt("a".repeat(201)))
        assertNull(AiGrooveRequest.validatePrompt("90s Boom Bap"))
    }

    @Test
    fun `SessionRecorderEngine captures relative offsetMs and LoopQuantizer performs non-destructive 1-16 quantization`() {
        var nowMs = 1_000L
        val engine = SessionRecorderEngine { nowMs }

        // At 120 BPM: quarter note = 500ms, 1/16th note = 125ms
        engine.startRecording(bpm = 120, swingPercent = 0, bars = 2, kitId = DrumKitId.ACOUSTIC_STUDIO)
        assertTrue(engine.isRecording)

        nowMs = 1_018L // +18ms (near 0ms downbeat)
        engine.recordHit(DrumPadId.KICK_RIGHT, velocity = 0.95f)

        nowMs = 1_139L // +139ms (near 125ms 1st 16th note)
        engine.recordHit(DrumPadId.HI_HAT_CLOSED, velocity = 0.75f)

        nowMs = 1_484L // +484ms (near 500ms beat 2 snare)
        engine.recordHit(DrumPadId.SNARE, velocity = 0.90f)

        nowMs = 2_000L
        val rawLoop = engine.stopRecording(customTitle = "Pocket Jam")
        assertFalse(engine.isRecording)
        assertFalse(rawLoop.isQuantized)
        assertEquals(listOf(18L, 139L, 484L), rawLoop.originalHits.map { it.offsetMs })
        assertEquals(listOf(18L, 139L, 484L), rawLoop.activeHits.map { it.offsetMs })

        // Quantize to 1/16 grid (125ms intervals at 120 BPM)
        val quantizedLoop = LoopQuantizer.quantizeToSixteenthGrid(rawLoop)
        assertTrue(quantizedLoop.isQuantized)
        assertEquals(listOf(0L, 125L, 500L), quantizedLoop.activeHits.map { it.offsetMs })
        // Non-destructive invariant: originalHits MUST remain untouched
        assertEquals(listOf(18L, 139L, 484L), quantizedLoop.originalHits.map { it.offsetMs })

        // Restore unquantized take
        val restoredLoop = LoopQuantizer.restoreUnquantized(quantizedLoop)
        assertFalse(restoredLoop.isQuantized)
        assertEquals(rawLoop.originalHits, restoredLoop.activeHits)

        // Verify JSON round-trip of RecordedLoop
        val loopJson = GrooveJsonCodec.encodeRecordedLoop(quantizedLoop)
        val decodedLoop = GrooveJsonCodec.decodeRecordedLoop(loopJson)
        assertEquals(quantizedLoop.id, decodedLoop.id)
        assertEquals(quantizedLoop.isQuantized, decodedLoop.isQuantized)
        assertEquals(quantizedLoop.originalHits, decodedLoop.originalHits)
        assertEquals(quantizedLoop.activeHits, decodedLoop.activeHits)
    }

    @Test
    fun `LoopQuantizer applies swing delay to odd 16th-note grid positions`() {
        // 120 BPM -> straight 16th = 125ms. With 30% swing -> odd step delayed by 125 * 0.30 * (2/3) = +25ms -> 150ms
        val grid = LoopQuantizer.buildSixteenthGridTimestampsMs(bpm = 120, swingPercent = 30, bars = 1)
        assertEquals(0L, grid[0])
        assertEquals(150L, grid[1])
        assertEquals(250L, grid[2])
        assertEquals(400L, grid[3])
    }
}
