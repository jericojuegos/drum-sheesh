package com.drumsheesh.core.audio

import com.drumsheesh.core.model.DrumKitId
import com.drumsheesh.core.model.DrumPadId
import com.drumsheesh.core.model.DrumSoundProfile
import com.drumsheesh.core.model.RoomAmbiencePreset
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure-Kotlin procedural 16-bit 44.1kHz mono RIFF WAV drum synthesizer.
 *
 * Synthesizes multi-mode acoustic & electronic percussion buffers with room reflections,
 * transient punch shaping, and tonal warmth for all 10 [DrumPadId] pads across all 4 [DrumKitId] kits
 * with zero external binary assets and zero Android dependencies.
 */
object ProceduralDrumSynthesizer {
    const val SAMPLE_RATE_HZ: Int = 44100
    const val WAV_HEADER_BYTES: Int = 44
    private const val TWO_PI: Double = 2.0 * PI

    /**
     * Generates a complete 44.1kHz 16-bit mono RIFF WAV byte array for [kitId], [padId], and [profile].
     */
    fun synthesizeWavBytes(
        kitId: DrumKitId,
        padId: DrumPadId,
        profile: DrumSoundProfile = DrumSoundProfile.defaultForKit(kitId)
    ): ByteArray {
        val pcmSamples = synthesizePcmSamples(kitId, padId, profile)
        return encodeRiffWav(pcmSamples, SAMPLE_RATE_HZ)
    }

    /**
     * Generates all 10 pad WAV byte arrays for the given [kitId] and [profile].
     */
    fun synthesizeKitWavMap(
        kitId: DrumKitId,
        profile: DrumSoundProfile = DrumSoundProfile.defaultForKit(kitId)
    ): Map<DrumPadId, ByteArray> =
        DrumPadId.entries.associateWith { padId -> synthesizeWavBytes(kitId, padId, profile) }

    /**
     * Synthesizes 16-bit signed PCM samples for [kitId], [padId], and [profile].
     */
    fun synthesizePcmSamples(
        kitId: DrumKitId,
        padId: DrumPadId,
        profile: DrumSoundProfile = DrumSoundProfile.defaultForKit(kitId)
    ): ShortArray {
        val dryDurationSec = dryDurationSecondsFor(kitId, padId)
        val roomTailSec = when {
            padId == DrumPadId.HI_HAT_CLOSED -> profile.roomPreset.decaySeconds * 0.25
            profile.roomPreset == RoomAmbiencePreset.DRY_BOOTH -> 0.04
            else -> profile.roomPreset.decaySeconds * 0.60
        }
        val totalDurationSec = (dryDurationSec + roomTailSec).coerceAtMost(1.25)
        val numSamples = (SAMPLE_RATE_HZ * totalDurationSec).toInt().coerceAtLeast(1024)
        val drySignal = DoubleArray(numSamples)

        val punch = profile.clampedPunch.toDouble()
        val warmth = profile.clampedWarmth.toDouble()

        // Deterministic LCG PRNG seeded per (kitId, padId) so synthesis is 100% reproducible
        var noiseState = (kitId.ordinal + 1) * 1_103_515_245L + (padId.ordinal + 1) * 12_345L
        fun nextNoise(): Double {
            noiseState = (noiseState * 6364136223846793005L + 1442695040888963407L)
            val bits = (noiseState ushr 33).toInt()
            return (bits.toDouble() / Int.MAX_VALUE.toDouble()).coerceIn(-1.0, 1.0)
        }

        // State-variable & 1-pole filter states for realistic snare-wire / beater / bronze shaping
        var phase1 = 0.0
        var phase2 = 0.0
        var phase3 = 0.0
        var phase4 = 0.0
        var prevNoise = 0.0
        var bpLow = 0.0
        var bpBand = 0.0

        // Center frequency for SVF bandpass (used for snare wire sizzle & beater wood transient)
        val svfFreq = when (padId) {
            DrumPadId.SNARE -> if (kitId == DrumKitId.VINTAGE_BREAKBEAT) 2550.0 else 3350.0
            DrumPadId.KICK_LEFT, DrumPadId.KICK_RIGHT -> 1950.0
            DrumPadId.HI_HAT_CLOSED, DrumPadId.HI_HAT_OPEN -> 7600.0
            DrumPadId.CRASH, DrumPadId.RIDE -> 5900.0
            else -> 2100.0
        }
        val svfF = 2.0 * sin(PI * svfFreq / SAMPLE_RATE_HZ)
        val svfQ = when (padId) {
            DrumPadId.SNARE -> 0.85
            DrumPadId.KICK_LEFT, DrumPadId.KICK_RIGHT -> 0.70
            else -> 0.95
        }

        val punchDrive = 0.85 + punch * 0.55

        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE_HZ.toDouble()
            val noise = nextNoise()
            val highPassedNoise = noise - 0.86 * prevNoise
            prevNoise = noise

            // Run 2-pole SVF bandpass for crisp acoustic noise shaping
            val svfHigh = noise - bpLow - svfQ * bpBand
            bpBand += svfF * svfHigh
            bpLow += svfF * bpBand

            val sample = when (padId) {
                DrumPadId.KICK_LEFT, DrumPadId.KICK_RIGHT -> {
                    val isLeft = padId == DrumPadId.KICK_LEFT
                    val pitchOffset = if (isLeft) 1.045 else 1.0
                    val (startFreq, endFreq, punchDecay, subDecay, baseDrive) = when (kitId) {
                        DrumKitId.ACOUSTIC_STUDIO -> Quintuple(162.0, 52.0, 34.0, 8.2, 1.32)
                        DrumKitId.TRAP_808 -> Quintuple(172.0, 38.0, 36.0, 2.7, 1.55)
                        DrumKitId.VINTAGE_BREAKBEAT -> Quintuple(156.0, 56.0, 31.0, 9.6, 1.45)
                        DrumKitId.SYNTHWAVE_ELECTRONIC -> Quintuple(196.0, 46.0, 25.0, 6.2, 1.42)
                    }
                    val instFreq = (endFreq + (startFreq - endFreq) * exp(-t * punchDecay)) * pitchOffset
                    phase1 += TWO_PI * instFreq / SAMPLE_RATE_HZ
                    phase2 += TWO_PI * (instFreq * 2.0) / SAMPLE_RATE_HZ

                    val subBody = sin(phase1) * exp(-t * subDecay)
                    // Chest harmonic gives clear punch on mobile speakers as well as headphones
                    val chestHarmony = sin(phase2) * exp(-t * (subDecay * 1.9)) * (0.32 + punch * 0.14)
                    // Batter head thump + felt/wood beater snap
                    val headThump = sin(TWO_PI * (185.0 * pitchOffset) * t) * exp(-t * 48.0) * (0.28 + punch * 0.18)
                    val beaterSnap = (
                        sin(TWO_PI * 1950.0 * t) * 0.45 +
                            bpBand * 0.75
                        ) * exp(-t * 145.0) * (0.30 + punch * 0.32)

                    softClip((subBody + chestHarmony + headThump + beaterSnap) * baseDrive * punchDrive)
                }

                DrumPadId.SNARE -> {
                    val (bodyFreq, wireMix, bodyDecay, wireDecay, baseDrive) = when (kitId) {
                        DrumKitId.ACOUSTIC_STUDIO -> Quintuple(194.0, 0.78, 17.0, 10.5, 1.35)
                        DrumKitId.TRAP_808 -> Quintuple(238.0, 0.88, 25.0, 14.0, 1.55)
                        DrumKitId.VINTAGE_BREAKBEAT -> Quintuple(182.0, 0.72, 20.0, 13.0, 1.48)
                        DrumKitId.SYNTHWAVE_ELECTRONIC -> Quintuple(174.0, 0.84, 13.5, 6.8, 1.45)
                    }
                    val instFreq = bodyFreq + 112.0 * exp(-t * 46.0)
                    phase1 += TWO_PI * instFreq / SAMPLE_RATE_HZ
                    // Circular drumhead Bessel overtones (1.00, 1.593, 2.136)
                    phase2 += TWO_PI * (instFreq * 1.593) / SAMPLE_RATE_HZ
                    phase3 += TWO_PI * (instFreq * 2.136) / SAMPLE_RATE_HZ

                    val membrane = (
                        sin(phase1) * 0.62 +
                            sin(phase2) * 0.25 +
                            sin(phase3) * 0.13
                        ) * exp(-t * bodyDecay)

                    // Snare wire sizzle: resonant bandpass wires + subtle 178Hz buzz + airy crispness
                    val wireFlutter = 0.82 + 0.18 * sin(TWO_PI * 178.0 * t)
                    val shapedWires = (bpBand * 0.78 + highPassedNoise * 0.36) *
                        wireFlutter * exp(-t * wireDecay) * wireMix
                    val stickCrack = (sin(TWO_PI * 2850.0 * t) * 0.42 + bpBand * 0.48) *
                        exp(-t * 185.0) * (0.28 + punch * 0.32)

                    softClip((membrane + shapedWires + stickCrack) * baseDrive * punchDrive)
                }

                DrumPadId.TOM_HIGH, DrumPadId.TOM_MID, DrumPadId.TOM_FLOOR -> {
                    val baseFreq = when (padId) {
                        DrumPadId.TOM_HIGH -> 162.0
                        DrumPadId.TOM_MID -> 118.0
                        else -> 82.0
                    }
                    val (freqScale, pitchSweep, decayRate, noiseSnap, baseDrive) = when (kitId) {
                        DrumKitId.ACOUSTIC_STUDIO -> Quintuple(1.0, 68.0, 6.8, 0.26, 1.28)
                        DrumKitId.TRAP_808 -> Quintuple(0.92, 85.0, 8.6, 0.20, 1.42)
                        DrumKitId.VINTAGE_BREAKBEAT -> Quintuple(1.04, 54.0, 8.8, 0.30, 1.38)
                        DrumKitId.SYNTHWAVE_ELECTRONIC -> Quintuple(0.96, 118.0, 5.4, 0.36, 1.45)
                    }
                    val instFreq = baseFreq * freqScale + pitchSweep * exp(-t * 30.0)
                    phase1 += TWO_PI * instFreq / SAMPLE_RATE_HZ
                    // Circular Bessel drumhead overtones for real resonant shell tone
                    phase2 += TWO_PI * (instFreq * 1.593) / SAMPLE_RATE_HZ
                    phase3 += TWO_PI * (instFreq * 2.136) / SAMPLE_RATE_HZ
                    phase4 += TWO_PI * (instFreq * 2.295) / SAMPLE_RATE_HZ

                    val fundamental = sin(phase1) * exp(-t * decayRate)
                    val shellOvertone = (
                        sin(phase2) * 0.55 +
                            sin(phase3) * 0.28 +
                            sin(phase4) * 0.17
                        ) * exp(-t * (decayRate * 1.55)) * 0.34
                    val stickAttack = (bpBand * 0.65 + highPassedNoise * 0.40) *
                        exp(-t * 105.0) * (noiseSnap + punch * 0.18)

                    softClip((fundamental + shellOvertone + stickAttack) * baseDrive * punchDrive)
                }

                DrumPadId.HI_HAT_CLOSED -> {
                    val decayRate = when (kitId) {
                        DrumKitId.ACOUSTIC_STUDIO -> 40.0
                        DrumKitId.TRAP_808 -> 55.0
                        DrumKitId.VINTAGE_BREAKBEAT -> 36.0
                        DrumKitId.SYNTHWAVE_ELECTRONIC -> 32.0
                    }
                    val bronze = bronzeCymbalMatrix(t * 1.12, kitId)
                    val air = (bpBand * 0.65 + highPassedNoise * 0.45)
                    val chick = (bronze * 0.44 + air * 0.72) * exp(-t * decayRate)
                    val transient = air * exp(-t * 165.0) * (0.20 + punch * 0.22)
                    softClip((chick + transient) * 1.28)
                }

                DrumPadId.HI_HAT_OPEN -> {
                    val decayRate = when (kitId) {
                        DrumKitId.ACOUSTIC_STUDIO -> 5.2
                        DrumKitId.TRAP_808 -> 6.4
                        DrumKitId.VINTAGE_BREAKBEAT -> 5.8
                        DrumKitId.SYNTHWAVE_ELECTRONIC -> 4.6
                    }
                    val pairBeat = 0.88 + 0.12 * sin(TWO_PI * 13.5 * t)
                    val bronze = bronzeCymbalMatrix(t, kitId)
                    val air = (bpBand * 0.58 + highPassedNoise * 0.48)
                    val sizzle = (bronze * 0.48 + air * 0.62) * pairBeat * exp(-t * decayRate)
                    val attack = air * exp(-t * 90.0) * (0.18 + punch * 0.20)
                    softClip((sizzle + attack) * 1.18)
                }

                DrumPadId.CRASH -> {
                    val decayRate = when (kitId) {
                        DrumKitId.ACOUSTIC_STUDIO -> 3.0
                        DrumKitId.TRAP_808 -> 3.9
                        DrumKitId.VINTAGE_BREAKBEAT -> 3.5
                        DrumKitId.SYNTHWAVE_ELECTRONIC -> 2.8
                    }
                    val bloomEnv = min(1.0, t / 0.014) * exp(-t * decayRate)
                    val bronze = bronzeCymbalMatrix(t * 0.88, kitId)
                    val airWash = (bpBand * 0.55 + highPassedNoise * 0.55)
                    val stickPing = sin(TWO_PI * 2460.0 * t) * exp(-t * 42.0) * (0.20 + punch * 0.15)
                    val shimmer = (bronze * 0.45 + airWash * 0.65) * bloomEnv
                    softClip((shimmer + stickPing) * 1.22)
                }

                DrumPadId.RIDE -> {
                    val decayRate = when (kitId) {
                        DrumKitId.ACOUSTIC_STUDIO -> 3.3
                        DrumKitId.TRAP_808 -> 4.4
                        DrumKitId.VINTAGE_BREAKBEAT -> 3.8
                        DrumKitId.SYNTHWAVE_ELECTRONIC -> 3.1
                    }
                    val bronze = bronzeCymbalMatrix(t * 1.05, kitId)
                    val bellPing = (
                        sin(TWO_PI * 2680.0 * t + sin(TWO_PI * 4120.0 * t) * 0.35) * 0.65 +
                            sin(TWO_PI * 3940.0 * t) * 0.35
                        ) * exp(-t * 15.0) * (0.48 + punch * 0.16)
                    val wash = (bronze * 0.46 + bpBand * 0.38 + highPassedNoise * 0.24) * exp(-t * decayRate)
                    softClip((bellPing + wash) * 1.22)
                }
            }

            drySignal[i] = sample
        }

        val processed = applyToneAndRoom(
            drySignal = drySignal,
            padId = padId,
            profile = profile,
            warmth = warmth
        )

        val samples = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val attackEnv = min(1.0, i.toDouble() / 56.0)
            val tailRemaining = (numSamples - 1 - i).toDouble()
            val releaseEnv = min(1.0, tailRemaining / 440.0)
            val shaped = softClip(processed[i]) * attackEnv * releaseEnv * 30000.0
            samples[i] = shaped.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        return samples
    }

    /**
     * Applies variable tonal warmth/brightness tilt plus a 4-comb + allpass studio room stage.
     */
    private fun applyToneAndRoom(
        drySignal: DoubleArray,
        padId: DrumPadId,
        profile: DrumSoundProfile,
        warmth: Double
    ): DoubleArray {
        val n = drySignal.size
        val out = DoubleArray(n)

        // 1. Variable Warmth / Brightness Tilt Filter
        val lpAlpha = (0.62 - warmth * 0.44).coerceIn(0.14, 0.70)
        var lpState = 0.0
        val airBoost = (0.5 - warmth).coerceAtLeast(0.0) * 0.45

        // 2. Prime-spaced studio room comb delays (~23ms, ~31ms, ~39ms, ~49ms at 44.1kHz)
        val combDelays = intArrayOf(1013, 1367, 1721, 2161)
        val combBuffers = Array(combDelays.size) { DoubleArray(combDelays[it]) }
        val combIndices = IntArray(combDelays.size)
        val combLpStates = DoubleArray(combDelays.size)

        val wetTarget = profile.roomPreset.wetMix.toDouble()
        val effectiveWet = when (padId) {
            DrumPadId.KICK_LEFT, DrumPadId.KICK_RIGHT -> wetTarget * 0.55
            DrumPadId.HI_HAT_CLOSED -> wetTarget * 0.50
            DrumPadId.CRASH, DrumPadId.RIDE, DrumPadId.HI_HAT_OPEN -> wetTarget * 0.72
            else -> wetTarget
        }
        val decayNorm = profile.roomPreset.decaySeconds.toDouble().coerceIn(0.12, 0.90)
        val baseFeedback = (0.34 + decayNorm * 0.42).coerceAtMost(0.74)
        val damping = profile.roomPreset.damping.toDouble().coerceIn(0.15, 0.75)

        // Allpass diffuser (223 samples ~5ms)
        val apDelay = 223
        val apBuffer = DoubleArray(apDelay)
        var apIdx = 0

        // Tight low-cut on reverb send so sub-bass kicks stay punchy without muddy boom
        var sendHpPrevIn = 0.0
        var sendHpPrevOut = 0.0

        for (i in 0 until n) {
            val raw = drySignal[i]
            lpState += lpAlpha * (raw - lpState)
            val hp = raw - lpState
            val toned = when {
                warmth > 0.55 -> raw * (1.0 - (warmth - 0.5) * 0.75) + lpState * ((warmth - 0.5) * 0.75)
                else -> raw + hp * airBoost
            }

            if (effectiveWet <= 0.01) {
                out[i] = toned
                continue
            }

            val sendHp = toned - sendHpPrevIn + 0.96 * sendHpPrevOut
            sendHpPrevIn = toned
            sendHpPrevOut = sendHp
            val reverbSend = if (padId == DrumPadId.KICK_LEFT || padId == DrumPadId.KICK_RIGHT) {
                sendHp
            } else {
                toned
            }

            var combSum = 0.0
            for (c in combDelays.indices) {
                val buf = combBuffers[c]
                val idx = combIndices[c]
                val delayed = buf[idx]
                combLpStates[c] = delayed * (1.0 - damping) + combLpStates[c] * damping
                buf[idx] = reverbSend + combLpStates[c] * baseFeedback
                combIndices[c] = (idx + 1) % combDelays[c]
                combSum += delayed
            }
            val avgComb = combSum / combDelays.size.toDouble()

            val apOut = -avgComb + apBuffer[apIdx]
            apBuffer[apIdx] = avgComb + apOut * 0.5
            apIdx = (apIdx + 1) % apDelay

            out[i] = toned * (1.0 - effectiveWet * 0.25) + apOut * effectiveWet
        }

        // Prevent peak collapse when room + drive stack
        var maxAbs = 0.0
        for (v in out) {
            val a = abs(v)
            if (a > maxAbs) maxAbs = a
        }
        if (maxAbs > 1.35) {
            val norm = 1.35 / maxAbs
            for (i in 0 until n) {
                out[i] *= norm
            }
        }

        return out
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

    private fun dryDurationSecondsFor(kitId: DrumKitId, padId: DrumPadId): Double = when (padId) {
        DrumPadId.HI_HAT_CLOSED -> 0.14
        DrumPadId.KICK_LEFT, DrumPadId.KICK_RIGHT -> if (kitId == DrumKitId.TRAP_808) 0.46 else 0.28
        DrumPadId.SNARE -> 0.27
        DrumPadId.TOM_HIGH -> 0.30
        DrumPadId.TOM_MID -> 0.34
        DrumPadId.TOM_FLOOR -> 0.38
        DrumPadId.HI_HAT_OPEN -> 0.48
        DrumPadId.RIDE -> 0.56
        DrumPadId.CRASH -> 0.62
    }

    private fun bronzeCymbalMatrix(t: Double, kitId: DrumKitId): Double {
        // 8 high-frequency inharmonic bronze partials (2.2kHz..9.4kHz) with subtle FM shimmer
        val shift = when (kitId) {
            DrumKitId.ACOUSTIC_STUDIO -> 1.00
            DrumKitId.TRAP_808 -> 1.12
            DrumKitId.VINTAGE_BREAKBEAT -> 0.94
            DrumKitId.SYNTHWAVE_ELECTRONIC -> 1.08
        }
        val fm = sin(TWO_PI * 1490.0 * shift * t) * 0.32
        val f1 = 2240.0 * shift
        val f2 = 3070.0 * shift
        val f3 = 3890.0 * shift
        val f4 = 4730.0 * shift
        val f5 = 5820.0 * shift
        val f6 = 6940.0 * shift
        val f7 = 8190.0 * shift
        val f8 = 9420.0 * shift
        return (
            sin(TWO_PI * f1 * t + fm) +
                sin(TWO_PI * f2 * t + 0.5 + fm * 0.8) +
                sin(TWO_PI * f3 * t + 1.1) +
                sin(TWO_PI * f4 * t + 1.8 + fm * 0.6) +
                sin(TWO_PI * f5 * t + 2.4) +
                sin(TWO_PI * f6 * t + 3.0) +
                sin(TWO_PI * f7 * t + 0.8) * 0.75 +
                sin(TWO_PI * f8 * t + 1.9) * 0.65
            ) / 7.4
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
