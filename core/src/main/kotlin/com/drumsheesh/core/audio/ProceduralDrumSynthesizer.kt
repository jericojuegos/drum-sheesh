package com.drumsheesh.core.audio

import com.drumsheesh.core.model.DrumKitId
import com.drumsheesh.core.model.DrumPadId
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure-Kotlin procedural 16-bit 44.1kHz mono RIFF WAV drum synthesizer.
 *
 * Synthesizes studio-shaped multi-layer percussion buffers for all 10 [DrumPadId] pads
 * across all 4 [DrumKitId] kits with zero external binary assets and zero Android dependencies.
 */
object ProceduralDrumSynthesizer {
    const val SAMPLE_RATE_HZ: Int = 44100
    const val WAV_HEADER_BYTES: Int = 44
    private const val TWO_PI: Double = 2.0 * PI

    /**
     * Generates a complete 44.1kHz 16-bit mono RIFF WAV byte array for [kitId] and [padId].
     */
    fun synthesizeWavBytes(kitId: DrumKitId, padId: DrumPadId): ByteArray {
        val pcmSamples = synthesizePcmSamples(kitId, padId)
        return encodeRiffWav(pcmSamples, SAMPLE_RATE_HZ)
    }

    /**
     * Generates all 10 pad WAV byte arrays for the given [kitId].
     */
    fun synthesizeKitWavMap(kitId: DrumKitId): Map<DrumPadId, ByteArray> =
        DrumPadId.entries.associateWith { padId -> synthesizeWavBytes(kitId, padId) }

    /**
     * Synthesizes 16-bit signed PCM samples for [kitId] and [padId].
     */
    fun synthesizePcmSamples(kitId: DrumKitId, padId: DrumPadId): ShortArray {
        val durationSec = durationSecondsFor(kitId, padId)
        val numSamples = (SAMPLE_RATE_HZ * durationSec).toInt().coerceAtLeast(1024)
        val samples = ShortArray(numSamples)

        // Deterministic LCG PRNG seeded per (kitId, padId) so synthesis is 100% reproducible
        var noiseState = (kitId.ordinal + 1) * 1_103_515_245L + (padId.ordinal + 1) * 12_345L
        fun nextNoise(): Double {
            noiseState = (noiseState * 6364136223846793005L + 1442695040888963407L)
            val bits = (noiseState ushr 33).toInt()
            return (bits.toDouble() / Int.MAX_VALUE.toDouble()).coerceIn(-1.0, 1.0)
        }

        var phase1 = 0.0
        var phase2 = 0.0
        var prevNoise = 0.0

        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE_HZ.toDouble()
            val noise = nextNoise()
            val highPassedNoise = noise - 0.82 * prevNoise
            prevNoise = noise

            val rawSignal = when (padId) {
                DrumPadId.KICK_LEFT, DrumPadId.KICK_RIGHT -> {
                    val isLeft = padId == DrumPadId.KICK_LEFT
                    val pitchOffset = if (isLeft) 1.04 else 1.0
                    val (startFreq, endFreq, punchDecay, subDecay, drive) = when (kitId) {
                        DrumKitId.ACOUSTIC_STUDIO -> Quintuple(145.0, 48.0, 26.0, 7.5, 1.35)
                        DrumKitId.TRAP_808 -> Quintuple(165.0, 36.0, 34.0, 2.8, 1.65)
                        DrumKitId.VINTAGE_BREAKBEAT -> Quintuple(155.0, 54.0, 28.0, 9.0, 1.50)
                        DrumKitId.SYNTHWAVE_ELECTRONIC -> Quintuple(190.0, 44.0, 22.0, 6.0, 1.45)
                    }
                    val instFreq = (endFreq + (startFreq - endFreq) * exp(-t * punchDecay)) * pitchOffset
                    phase1 += TWO_PI * instFreq / SAMPLE_RATE_HZ
                    val body = sin(phase1) * exp(-t * subDecay)
                    val click = sin(TWO_PI * 1400.0 * t) * exp(-t * 140.0) * 0.32 +
                        highPassedNoise * exp(-t * 180.0) * 0.20
                    softClip((body + click) * drive)
                }

                DrumPadId.SNARE -> {
                    val (bodyFreq, wireMix, bodyDecay, wireDecay, drive) = when (kitId) {
                        DrumKitId.ACOUSTIC_STUDIO -> Quintuple(188.0, 0.68, 18.0, 11.5, 1.4)
                        DrumKitId.TRAP_808 -> Quintuple(235.0, 0.82, 24.0, 15.0, 1.6)
                        DrumKitId.VINTAGE_BREAKBEAT -> Quintuple(202.0, 0.62, 21.0, 14.0, 1.55)
                        DrumKitId.SYNTHWAVE_ELECTRONIC -> Quintuple(172.0, 0.78, 14.0, 7.2, 1.5)
                    }
                    val instFreq = bodyFreq + 95.0 * exp(-t * 45.0)
                    phase1 += TWO_PI * instFreq / SAMPLE_RATE_HZ
                    phase2 += TWO_PI * (instFreq * 1.58) / SAMPLE_RATE_HZ
                    val shell = (sin(phase1) * 0.75 + sin(phase2) * 0.25) * exp(-t * bodyDecay)
                    val wires = highPassedNoise * exp(-t * wireDecay) * wireMix
                    val snap = sin(TWO_PI * 2200.0 * t) * exp(-t * 160.0) * 0.25
                    softClip((shell + wires + snap) * drive)
                }

                DrumPadId.TOM_HIGH, DrumPadId.TOM_MID, DrumPadId.TOM_FLOOR -> {
                    val baseFreq = when (padId) {
                        DrumPadId.TOM_HIGH -> 152.0
                        DrumPadId.TOM_MID -> 116.0
                        else -> 82.0
                    }
                    val (freqScale, pitchSweep, decayRate, noiseSnap, drive) = when (kitId) {
                        DrumKitId.ACOUSTIC_STUDIO -> Quintuple(1.0, 55.0, 7.5, 0.22, 1.25)
                        DrumKitId.TRAP_808 -> Quintuple(0.92, 80.0, 9.0, 0.18, 1.40)
                        DrumKitId.VINTAGE_BREAKBEAT -> Quintuple(1.06, 48.0, 9.8, 0.28, 1.35)
                        DrumKitId.SYNTHWAVE_ELECTRONIC -> Quintuple(0.96, 110.0, 5.8, 0.35, 1.45)
                    }
                    val instFreq = baseFreq * freqScale + pitchSweep * exp(-t * 28.0)
                    phase1 += TWO_PI * instFreq / SAMPLE_RATE_HZ
                    val fundamental = sin(phase1) * exp(-t * decayRate)
                    val overtone = sin(phase1 * 1.51) * exp(-t * (decayRate * 1.7)) * 0.22
                    val attack = highPassedNoise * exp(-t * 95.0) * noiseSnap
                    softClip((fundamental + overtone + attack) * drive)
                }

                DrumPadId.HI_HAT_CLOSED -> {
                    val decayRate = when (kitId) {
                        DrumKitId.ACOUSTIC_STUDIO -> 42.0
                        DrumKitId.TRAP_808 -> 58.0
                        DrumKitId.VINTAGE_BREAKBEAT -> 38.0
                        DrumKitId.SYNTHWAVE_ELECTRONIC -> 34.0
                    }
                    val metallic = metallicCymbalMatrix(t, kitId)
                    val crisp = (metallic * 0.65 + highPassedNoise * 0.55) * exp(-t * decayRate)
                    softClip(crisp * 1.35)
                }

                DrumPadId.HI_HAT_OPEN -> {
                    val decayRate = when (kitId) {
                        DrumKitId.ACOUSTIC_STUDIO -> 5.6
                        DrumKitId.TRAP_808 -> 6.8
                        DrumKitId.VINTAGE_BREAKBEAT -> 6.2
                        DrumKitId.SYNTHWAVE_ELECTRONIC -> 4.9
                    }
                    val metallic = metallicCymbalMatrix(t, kitId)
                    val sizzle = (metallic * 0.70 + highPassedNoise * 0.45) * exp(-t * decayRate)
                    softClip(sizzle * 1.25)
                }

                DrumPadId.CRASH -> {
                    val decayRate = when (kitId) {
                        DrumKitId.ACOUSTIC_STUDIO -> 3.4
                        DrumKitId.TRAP_808 -> 4.2
                        DrumKitId.VINTAGE_BREAKBEAT -> 3.8
                        DrumKitId.SYNTHWAVE_ELECTRONIC -> 3.1
                    }
                    val metallic = metallicCymbalMatrix(t * 0.92, kitId)
                    val shimmer = (metallic * 0.62 + highPassedNoise * 0.58) * exp(-t * decayRate)
                    val bellAttack = sin(TWO_PI * 980.0 * t) * exp(-t * 35.0) * 0.25
                    softClip((shimmer + bellAttack) * 1.30)
                }

                DrumPadId.RIDE -> {
                    val decayRate = when (kitId) {
                        DrumKitId.ACOUSTIC_STUDIO -> 3.8
                        DrumKitId.TRAP_808 -> 4.8
                        DrumKitId.VINTAGE_BREAKBEAT -> 4.2
                        DrumKitId.SYNTHWAVE_ELECTRONIC -> 3.5
                    }
                    val metallic = metallicCymbalMatrix(t * 1.08, kitId)
                    val ping = sin(TWO_PI * 1340.0 * t + sin(TWO_PI * 2680.0 * t) * 0.6) *
                        exp(-t * 11.0) * 0.45
                    val wash = (metallic * 0.55 + highPassedNoise * 0.30) * exp(-t * decayRate)
                    softClip((ping + wash) * 1.25)
                }
            }

            // Smooth 1.5ms attack & tail fade-out to avoid DC clicks
            val attackEnv = min(1.0, i.toDouble() / 64.0)
            val tailRemaining = (numSamples - 1 - i).toDouble()
            val releaseEnv = min(1.0, tailRemaining / 256.0)
            val scaled = (rawSignal * attackEnv * releaseEnv * 30000.0)
                .toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            samples[i] = scaled.toShort()
        }

        return samples
    }

    /**
     * Computes RMS amplitude in `[0.0..1.0]` for a 44-byte-header RIFF WAV buffer (used in unit tests).
     */
    fun computeWavRms(wavBytes: ByteArray): Double {
        if (wavBytes.size <= WAV_HEADER_BYTES) return 0.0
        val sampleCount = (wavBytes.size - WAV_HEADER_BYTES) / 2
        if (sampleCount <= 0) return 0.0

        val buffer = ByteBuffer.wrap(wavBytes, WAV_HEADER_BYTES, sampleCount * 2)
            .order(ByteOrder.LITTLE_ENDIAN)
        var sumSquares = 0.0
        for (i in 0 until sampleCount) {
            val normalized = buffer.short.toDouble() / Short.MAX_VALUE.toDouble()
            sumSquares += normalized * normalized
        }
        return sqrt(sumSquares / sampleCount.toDouble())
    }

    private fun durationSecondsFor(kitId: DrumKitId, padId: DrumPadId): Double = when (padId) {
        DrumPadId.HI_HAT_CLOSED -> 0.14
        DrumPadId.KICK_LEFT, DrumPadId.KICK_RIGHT -> if (kitId == DrumKitId.TRAP_808) 0.48 else 0.30
        DrumPadId.SNARE -> 0.28
        DrumPadId.TOM_HIGH -> 0.30
        DrumPadId.TOM_MID -> 0.34
        DrumPadId.TOM_FLOOR -> 0.38
        DrumPadId.HI_HAT_OPEN -> 0.52
        DrumPadId.RIDE -> 0.60
        DrumPadId.CRASH -> 0.65
    }

    private fun metallicCymbalMatrix(t: Double, kitId: DrumKitId): Double {
        // 6 inharmonic metallic partials (inspired by TR-808 / bronze modal ratios)
        val shift = when (kitId) {
            DrumKitId.ACOUSTIC_STUDIO -> 1.00
            DrumKitId.TRAP_808 -> 1.14
            DrumKitId.VINTAGE_BREAKBEAT -> 0.95
            DrumKitId.SYNTHWAVE_ELECTRONIC -> 1.07
        }
        val f1 = 318.0 * shift
        val f2 = 469.0 * shift
        val f3 = 631.0 * shift
        val f4 = 847.0 * shift
        val f5 = 1193.0 * shift
        val f6 = 1682.0 * shift
        return (
            sin(TWO_PI * f1 * t) +
                sin(TWO_PI * f2 * t + 0.4) +
                sin(TWO_PI * f3 * t + 0.9) +
                sin(TWO_PI * f4 * t + 1.7) +
                sin(TWO_PI * f5 * t + 2.3) +
                sin(TWO_PI * f6 * t + 3.1)
            ) / 6.0
    }

    private fun softClip(x: Double): Double {
        val clamped = max(-2.5, min(2.5, x))
        return clamped / (1.0 + 0.28 * clamped.pow(2.0))
    }

    private fun encodeRiffWav(pcmSamples: ShortArray, sampleRate: Int): ByteArray {
        val dataSize = pcmSamples.size * 2
        val totalFileSizeMinus8 = 36 + dataSize
        val byteRate = sampleRate * 2 // mono 16-bit

        val buffer = ByteBuffer.allocate(WAV_HEADER_BYTES + dataSize)
            .order(ByteOrder.LITTLE_ENDIAN)

        // RIFF chunk descriptor
        buffer.put('R'.code.toByte())
        buffer.put('I'.code.toByte())
        buffer.put('F'.code.toByte())
        buffer.put('F'.code.toByte())
        buffer.putInt(totalFileSizeMinus8)
        buffer.put('W'.code.toByte())
        buffer.put('A'.code.toByte())
        buffer.put('V'.code.toByte())
        buffer.put('E'.code.toByte())

        // "fmt " sub-chunk
        buffer.put('f'.code.toByte())
        buffer.put('m'.code.toByte())
        buffer.put('t'.code.toByte())
        buffer.put(' '.code.toByte())
        buffer.putInt(16) // PCM subchunk1Size
        buffer.putShort(1) // AudioFormat = 1 (PCM)
        buffer.putShort(1) // NumChannels = 1 (Mono)
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate)
        buffer.putShort(2) // BlockAlign = 2 bytes per sample
        buffer.putShort(16) // BitsPerSample = 16

        // "data" sub-chunk
        buffer.put('d'.code.toByte())
        buffer.put('a'.code.toByte())
        buffer.put('t'.code.toByte())
        buffer.put('a'.code.toByte())
        buffer.putInt(dataSize)

        for (sample in pcmSamples) {
            buffer.putShort(sample)
        }

        return buffer.array()
    }

    private data class Quintuple(
        val a: Double,
        val b: Double,
        val c: Double,
        val d: Double,
        val e: Double
    )
}
