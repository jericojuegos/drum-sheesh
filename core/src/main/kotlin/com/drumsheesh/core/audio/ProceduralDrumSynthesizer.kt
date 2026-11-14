package com.drumsheesh.core.audio

import com.drumsheesh.core.model.DrumKitId
import com.drumsheesh.core.model.DrumPadId
import com.drumsheesh.core.model.DrumSoundProfile
import com.drumsheesh.core.model.RoomAmbiencePreset
import com.drumsheesh.core.model.SnareArticulation
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
 * Models close-mic'd shells (Bessel drumhead modes, pink-noise snare wire rattle, warm beater thump),
 * full-bodied bronze cymbals (pink-noise roar + low-mid gong body without shrill whistling peaks),
 * and an overhead studio/arena room-mic reflection & compression stage with zero Android dependencies.
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
     * Shapes a real 16-bit PCM RIFF WAV recording using [profile] room ambience, punch, and warmth.
     */
    fun processWavWithProfile(
        wavBytes: ByteArray,
        padId: DrumPadId,
        profile: DrumSoundProfile
    ): ByteArray {
        val dryMono = decodeRiffWavMono(wavBytes) ?: return wavBytes
        val roomTailSec = when {
            padId == DrumPadId.HI_HAT_CLOSED -> profile.roomPreset.decaySeconds * 0.22
            profile.roomPreset == RoomAmbiencePreset.DRY_BOOTH -> 0.05
            else -> profile.roomPreset.decaySeconds * 0.60
        }
        val extraTailSamples = (SAMPLE_RATE_HZ * roomTailSec).toInt()
        val maxSamples = (SAMPLE_RATE_HZ * 1.85).toInt()
        val totalSamples = (dryMono.size + extraTailSamples).coerceAtMost(maxSamples).coerceAtLeast(1024)
        val drySignal = DoubleArray(totalSamples)

        val punch = profile.clampedPunch.toDouble()
        val copyCount = min(dryMono.size, totalSamples)
        for (i in 0 until copyCount) {
            val t = i.toDouble() / SAMPLE_RATE_HZ.toDouble()
            // Transient-weighted punch boost preserves studio stick crack and kick thump
            val transientBoost = 1.0 + (punch - 0.5) * 0.42 * exp(-t * 28.0)
            drySignal[i] = softClip(dryMono[i] * transientBoost)
        }

        val samples = finalizeSamples(
            drySignal = drySignal,
            padId = padId,
            profile = profile,
            attackFadeSamples = 16
        )
        return encodeRiffWav(samples, SAMPLE_RATE_HZ)
    }

    /**
     * Shapes a real 16-bit snare articulation WAV with articulation-aware warmth and punch,
     * preserving intimate ghost-note buzz, crisp edge overtones, woody cross-stick, and explosive rimshot snap.
     */
    fun processSnareWavWithProfile(
        wavBytes: ByteArray,
        articulation: SnareArticulation,
        profile: DrumSoundProfile
    ): ByteArray {
        val basePunch = profile.clampedPunch
        val baseWarmth = profile.clampedWarmth
        val tunedProfile = when (articulation) {
            SnareArticulation.GHOST_NOTE -> profile.copy(
                punchAmount = (basePunch * 0.65f).coerceIn(0f, 1f),
                warmthTone = (baseWarmth + 0.14f).coerceIn(0f, 1f)
            )
            SnareArticulation.CENTER_SOFT -> profile.copy(
                punchAmount = (basePunch * 0.85f).coerceIn(0f, 1f),
                warmthTone = (baseWarmth + 0.05f).coerceIn(0f, 1f)
            )
            SnareArticulation.CENTER_HARD -> profile
            SnareArticulation.EDGE_CRISP -> profile.copy(
                punchAmount = (basePunch + 0.12f).coerceIn(0f, 1f),
                warmthTone = (baseWarmth - 0.16f).coerceIn(0f, 1f)
            )
            SnareArticulation.CROSS_STICK -> profile.copy(
                punchAmount = (basePunch + 0.15f).coerceIn(0f, 1f),
                warmthTone = (baseWarmth - 0.08f).coerceIn(0f, 1f)
            )
            SnareArticulation.RIMSHOT -> profile.copy(
                punchAmount = (basePunch + 0.25f).coerceIn(0f, 1f),
                warmthTone = (baseWarmth - 0.12f).coerceIn(0f, 1f)
            )
        }
        return processWavWithProfile(wavBytes, DrumPadId.SNARE, tunedProfile)
    }

    /**
     * Synthesizes a dedicated snare articulation WAV (Ghost, Soft, Hard, Edge, Cross-Stick, Rimshot)
     * with deterministic round-robin [variantIndex] texture for synthesized kits and fallback.
     */
    fun synthesizeSnareArticulationWavBytes(
        kitId: DrumKitId,
        articulation: SnareArticulation,
        variantIndex: Int = 0,
        profile: DrumSoundProfile = DrumSoundProfile.defaultForKit(kitId)
    ): ByteArray {
        val roomTailSec = if (profile.roomPreset == RoomAmbiencePreset.DRY_BOOTH) {
            0.05
        } else {
            profile.roomPreset.decaySeconds * (if (articulation == SnareArticulation.CROSS_STICK) 0.42 else 0.65)
        }
        val dryDurationSec = when (articulation) {
            SnareArticulation.CROSS_STICK -> 0.15
            SnareArticulation.GHOST_NOTE -> 0.18
            SnareArticulation.CENTER_SOFT -> 0.24
            SnareArticulation.EDGE_CRISP -> 0.26
            SnareArticulation.CENTER_HARD -> 0.30
            SnareArticulation.RIMSHOT -> 0.34
        }
        val totalSamples = (SAMPLE_RATE_HZ * (dryDurationSec + roomTailSec).coerceAtMost(1.38)).toInt().coerceAtLeast(1024)
        val drySignal = DoubleArray(totalSamples)
        val punch = profile.clampedPunch.toDouble()

        var noiseState = (kitId.ordinal + 1) * 1_103_515_245L +
            (articulation.ordinal + 17) * 54_321L +
            (variantIndex and 3) * 98_765L
        var pinkB0 = 0.0
        var pinkB1 = 0.0
        var pinkB2 = 0.0
        var prevPink = 0.0

        val (bodyFreq, wireMix, bodyDecay, wireDecay, baseDrive) = when (kitId) {
            DrumKitId.ACOUSTIC_STUDIO -> Quintuple(188.0, 0.85, 15.5, 9.8, 1.40)
            DrumKitId.TRAP_808 -> Quintuple(232.0, 0.90, 24.0, 13.5, 1.55)
            DrumKitId.VINTAGE_BREAKBEAT -> Quintuple(178.0, 0.78, 18.5, 12.0, 1.50)
            DrumKitId.SYNTHWAVE_ELECTRONIC -> Quintuple(172.0, 0.88, 13.0, 6.6, 1.46)
        }

        var phase1 = 0.0
        var phase2 = 0.0
        var phase3 = 0.0

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / SAMPLE_RATE_HZ.toDouble()
            noiseState = (noiseState * 6364136223846793005L + 1442695040888963407L)
            val white = (((noiseState ushr 33).toInt()).toDouble() / Int.MAX_VALUE.toDouble()).coerceIn(-1.0, 1.0)
            pinkB0 = 0.99765 * pinkB0 + white * 0.0990460
            pinkB1 = 0.96300 * pinkB1 + white * 0.2965164
            pinkB2 = 0.57000 * pinkB2 + white * 1.0526913
            val pink = ((pinkB0 + pinkB1 + pinkB2 + white * 0.1848) * 0.24).coerceIn(-1.0, 1.0)
            val crispNoise = pink - prevPink * 0.82
            prevPink = pink

            drySignal[i] = when (articulation) {
                SnareArticulation.CROSS_STICK -> {
                    // Clean woody + metallic rim click with minimal snare wire bleed
                    val woodClick = (
                        sin(TWO_PI * 465.0 * t) * 0.52 +
                            sin(TWO_PI * 1190.0 * t) * 0.44 +
                            sin(TWO_PI * 2420.0 * t) * 0.28
                        ) * exp(-t * 48.0)
                    val stickTap = crispNoise * exp(-t * 160.0) * 0.35
                    val ghostWire = pink * exp(-t * 32.0) * 0.08
                    softClip((woodClick + stickTap + ghostWire) * (1.35 + punch * 0.25))
                }
                SnareArticulation.RIMSHOT -> {
                    // Explosive head + steel hoop overtones + full aggressive wire burst
                    val instFreq = (bodyFreq * 1.06) + 145.0 * exp(-t * 55.0)
                    phase1 += TWO_PI * instFreq / SAMPLE_RATE_HZ
                    phase2 += TWO_PI * (instFreq * 2.18) / SAMPLE_RATE_HZ
                    phase3 += TWO_PI * 910.0 / SAMPLE_RATE_HZ
                    val head = (sin(phase1) * 0.65 + sin(phase2) * 0.35) * exp(-t * (bodyDecay * 0.85))
                    val rimRing = (sin(phase3) * 0.55 + sin(TWO_PI * 1880.0 * t) * 0.35) * exp(-t * 38.0)
                    val wires = crispNoise * exp(-t * (wireDecay * 0.88)) * wireMix * 1.15
                    val crack = (sin(TWO_PI * 2650.0 * t) * 0.42 + crispNoise * 0.55) * exp(-t * 150.0)
                    softClip((head + rimRing + wires + crack) * baseDrive * (1.18 + punch * 0.25))
                }
                SnareArticulation.GHOST_NOTE -> {
                    // Intimate soft membrane + delicate warm wire buzz
                    val instFreq = bodyFreq * 0.96 + 45.0 * exp(-t * 40.0)
                    phase1 += TWO_PI * instFreq / SAMPLE_RATE_HZ
                    val head = sin(phase1) * 0.48 * exp(-t * (bodyDecay * 1.25))
                    val softWires = (pink * 0.45 + crispNoise * 0.35) * exp(-t * (wireDecay * 1.15)) * wireMix * 0.72
                    softClip((head + softWires) * baseDrive * 0.82)
                }
                SnareArticulation.EDGE_CRISP -> {
                    // Brighter stick attack + crisp high-mid overtones
                    val instFreq = bodyFreq * 1.08 + 95.0 * exp(-t * 50.0)
                    phase1 += TWO_PI * instFreq / SAMPLE_RATE_HZ
                    phase2 += TWO_PI * (instFreq * 1.78) / SAMPLE_RATE_HZ
                    val head = (sin(phase1) * 0.45 + sin(phase2) * 0.38) * exp(-t * (bodyDecay * 1.05))
                    val crispWires = crispNoise * exp(-t * wireDecay) * wireMix * 1.05
                    val attack = sin(TWO_PI * 2450.0 * t) * exp(-t * 185.0) * (0.32 + punch * 0.28)
                    softClip((head + crispWires + attack) * baseDrive)
                }
                SnareArticulation.CENTER_SOFT,
                SnareArticulation.CENTER_HARD -> {
                    val isHard = articulation == SnareArticulation.CENTER_HARD
                    val instFreq = bodyFreq + (if (isHard) 115.0 else 75.0) * exp(-t * 45.0)
                    phase1 += TWO_PI * instFreq / SAMPLE_RATE_HZ
                    phase2 += TWO_PI * (instFreq * 1.593) / SAMPLE_RATE_HZ
                    val head = (sin(phase1) * 0.68 + sin(phase2) * 0.26) * exp(-t * bodyDecay)
                    val wires = (crispNoise * 0.68 + pink * 0.28) * exp(-t * wireDecay) *
                        wireMix * (if (isHard) 1.05 else 0.82)
                    val attack = (sin(TWO_PI * 2150.0 * t) * 0.34 + crispNoise * 0.48) *
                        exp(-t * 170.0) * (if (isHard) 0.34 + punch * 0.30 else 0.16 + punch * 0.18)
                    softClip((head + wires + attack) * baseDrive * (if (isHard) 1.05 else 0.90))
                }
            }
        }

        val samples = finalizeSamples(
            drySignal = drySignal,
            padId = DrumPadId.SNARE,
            profile = profile,
            attackFadeSamples = 24
        )
        return encodeRiffWav(samples, SAMPLE_RATE_HZ)
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
            padId == DrumPadId.HI_HAT_CLOSED -> profile.roomPreset.decaySeconds * 0.22
            profile.roomPreset == RoomAmbiencePreset.DRY_BOOTH -> 0.05
            else -> profile.roomPreset.decaySeconds * 0.68
        }
        val totalDurationSec = (dryDurationSec + roomTailSec).coerceAtMost(1.38)
        val numSamples = (SAMPLE_RATE_HZ * totalDurationSec).toInt().coerceAtLeast(1024)
        val drySignal = DoubleArray(numSamples)

        val punch = profile.clampedPunch.toDouble()

        // Deterministic LCG PRNG seeded per (kitId, padId) + second decorrelated stream for cymbal turbulence
        var noiseState = (kitId.ordinal + 1) * 1_103_515_245L + (padId.ordinal + 1) * 12_345L
        var noiseState2 = (kitId.ordinal + 7) * 2_140_132_611L + (padId.ordinal + 13) * 98_765L
        fun nextWhiteNoise(): Double {
            noiseState = (noiseState * 6364136223846793005L + 1442695040888963407L)
            val bits = (noiseState ushr 33).toInt()
            return (bits.toDouble() / Int.MAX_VALUE.toDouble()).coerceIn(-1.0, 1.0)
        }
        fun nextWhiteNoise2(): Double {
            noiseState2 = (noiseState2 * 6364136223846793005L + 1442695040888963407L)
            val bits = (noiseState2 ushr 33).toInt()
            return (bits.toDouble() / Int.MAX_VALUE.toDouble()).coerceIn(-1.0, 1.0)
        }

        // Paul Kellet 3-stage pink noise state (-3dB/oct natural acoustic spectrum)
        var pinkB0 = 0.0
        var pinkB1 = 0.0
        var pinkB2 = 0.0
        fun nextPinkNoise(white: Double): Double {
            pinkB0 = 0.99765 * pinkB0 + white * 0.0990460
            pinkB1 = 0.96300 * pinkB1 + white * 0.2965164
            pinkB2 = 0.57000 * pinkB2 + white * 1.0526913
            return ((pinkB0 + pinkB1 + pinkB2 + white * 0.1848) * 0.24).coerceIn(-1.0, 1.0)
        }

        var phase1 = 0.0
        var phase2 = 0.0
        var phase3 = 0.0
        var phase4 = 0.0
        var prevPink = 0.0
        var prevWhite2 = 0.0

        // Triple SVF bandpass filters: Low-Mid Body, Mid Splash, Presence/Air
        var lowMidLow = 0.0
        var lowMidBand = 0.0
        var midLow = 0.0
        var midBand = 0.0
        var highLow = 0.0
        var highBand = 0.0

        val lowMidCenterHz = when (padId) {
            DrumPadId.CRASH -> 520.0
            DrumPadId.RIDE -> 680.0
            DrumPadId.HI_HAT_OPEN -> 950.0
            DrumPadId.HI_HAT_CLOSED -> 1300.0
            else -> 480.0
        }
        val midCenterHz = when (padId) {
            DrumPadId.CRASH -> 1480.0
            DrumPadId.RIDE -> 1720.0
            DrumPadId.HI_HAT_OPEN -> 2650.0
            DrumPadId.HI_HAT_CLOSED -> 3350.0
            DrumPadId.SNARE -> 1150.0
            else -> 720.0
        }
        val highCenterHz = when (padId) {
            DrumPadId.CRASH -> 3450.0
            DrumPadId.RIDE -> 3900.0
            DrumPadId.HI_HAT_OPEN -> 5200.0
            DrumPadId.HI_HAT_CLOSED -> 5900.0
            DrumPadId.SNARE -> if (kitId == DrumKitId.VINTAGE_BREAKBEAT) 2150.0 else 2750.0
            DrumPadId.KICK_LEFT, DrumPadId.KICK_RIGHT -> 1550.0
            else -> 1650.0
        }
        val lowMidF = 2.0 * sin(PI * lowMidCenterHz / SAMPLE_RATE_HZ)
        val midF = 2.0 * sin(PI * midCenterHz / SAMPLE_RATE_HZ)
        val highF = 2.0 * sin(PI * highCenterHz / SAMPLE_RATE_HZ)
        val lowMidQ = 0.56
        val midQ = if (padId == DrumPadId.CRASH) 0.58 else 0.72
        val highQ = if (padId == DrumPadId.CRASH) 0.64 else 0.82

        val punchDrive = 0.88 + punch * 0.55

        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE_HZ.toDouble()
            val white = nextWhiteNoise()
            val white2 = nextWhiteNoise2()
            val pink = nextPinkNoise(white)
            val crispPink = pink - 0.55 * prevPink
            prevPink = pink
            val hpWhite2 = white2 - 0.65 * prevWhite2
            prevWhite2 = white2

            val lmHigh = pink - lowMidLow - lowMidQ * lowMidBand
            lowMidBand += lowMidF * lmHigh
            lowMidLow += lowMidF * lowMidBand

            val midHigh = pink - midLow - midQ * midBand
            midBand += midF * midHigh
            midLow += midF * midBand

            val hiHigh = pink - highLow - highQ * highBand
            highBand += highF * hiHigh
            highLow += highF * highBand

            val sample = when (padId) {
                DrumPadId.KICK_LEFT, DrumPadId.KICK_RIGHT -> {
                    val isLeft = padId == DrumPadId.KICK_LEFT
                    val pitchOffset = if (isLeft) 1.045 else 1.0
                    val (startFreq, endFreq, punchDecay, subDecay, baseDrive) = when (kitId) {
                        DrumKitId.ACOUSTIC_STUDIO -> Quintuple(168.0, 52.0, 34.0, 7.6, 1.38)
                        DrumKitId.TRAP_808 -> Quintuple(175.0, 38.0, 36.0, 2.6, 1.55)
                        DrumKitId.VINTAGE_BREAKBEAT -> Quintuple(158.0, 56.0, 30.0, 9.0, 1.48)
                        DrumKitId.SYNTHWAVE_ELECTRONIC -> Quintuple(198.0, 46.0, 25.0, 6.0, 1.44)
                    }
                    val instFreq = (endFreq + (startFreq - endFreq) * exp(-t * punchDecay)) * pitchOffset
                    phase1 += TWO_PI * instFreq / SAMPLE_RATE_HZ
                    phase2 += TWO_PI * (instFreq * 2.0) / SAMPLE_RATE_HZ

                    val subBody = sin(phase1) * exp(-t * subDecay)
                    // Close-mic chest harmonic (~95-115Hz) + batter-head thump for full phone-speaker weight
                    val chestHarmony = sin(phase2) * exp(-t * (subDecay * 1.75)) * (0.38 + punch * 0.14)
                    val headThump = sin(TWO_PI * (155.0 * pitchOffset) * t) * exp(-t * 40.0) * (0.32 + punch * 0.16)
                    val beaterSnap = (
                        sin(TWO_PI * 1350.0 * t) * 0.36 +
                            highBand * 0.72
                        ) * exp(-t * 135.0) * (0.28 + punch * 0.30)

                    softClip((subBody + chestHarmony + headThump + beaterSnap) * baseDrive * punchDrive)
                }

                DrumPadId.SNARE -> {
                    val (bodyFreq, wireMix, bodyDecay, wireDecay, baseDrive) = when (kitId) {
                        DrumKitId.ACOUSTIC_STUDIO -> Quintuple(188.0, 0.85, 15.5, 9.8, 1.40)
                        DrumKitId.TRAP_808 -> Quintuple(232.0, 0.90, 24.0, 13.5, 1.55)
                        DrumKitId.VINTAGE_BREAKBEAT -> Quintuple(178.0, 0.78, 18.5, 12.0, 1.50)
                        DrumKitId.SYNTHWAVE_ELECTRONIC -> Quintuple(172.0, 0.88, 13.0, 6.6, 1.46)
                    }
                    val instFreq = bodyFreq + 110.0 * exp(-t * 45.0)
                    phase1 += TWO_PI * instFreq / SAMPLE_RATE_HZ
                    phase2 += TWO_PI * (instFreq * 1.593) / SAMPLE_RATE_HZ
                    phase3 += TWO_PI * (instFreq * 2.136) / SAMPLE_RATE_HZ

                    val membrane = (
                        sin(phase1) * 0.65 +
                            sin(phase2) * 0.24 +
                            sin(phase3) * 0.11
                        ) * exp(-t * bodyDecay)

                    // Thick close-mic'd bottom snare wires built from warm pink noise + subtle 172Hz buzz
                    val wireBuzz = 0.84 + 0.16 * sin(TWO_PI * 172.0 * t)
                    val snareWires = (midBand * 0.45 + highBand * 0.75 + crispPink * 0.30) *
                        wireBuzz * exp(-t * wireDecay) * wireMix
                    val stickCrack = (sin(TWO_PI * 2150.0 * t) * 0.35 + highBand * 0.55) *
                        exp(-t * 170.0) * (0.30 + punch * 0.32)

                    softClip((membrane + snareWires + stickCrack) * baseDrive * punchDrive)
                }

                DrumPadId.TOM_HIGH, DrumPadId.TOM_MID, DrumPadId.TOM_FLOOR -> {
                    val baseFreq = when (padId) {
                        DrumPadId.TOM_HIGH -> 158.0
                        DrumPadId.TOM_MID -> 116.0
                        else -> 80.0
                    }
                    val (freqScale, pitchSweep, decayRate, noiseSnap, baseDrive) = when (kitId) {
                        DrumKitId.ACOUSTIC_STUDIO -> Quintuple(1.0, 68.0, 6.4, 0.26, 1.32)
                        DrumKitId.TRAP_808 -> Quintuple(0.92, 85.0, 8.2, 0.20, 1.44)
                        DrumKitId.VINTAGE_BREAKBEAT -> Quintuple(1.04, 54.0, 8.2, 0.30, 1.40)
                        DrumKitId.SYNTHWAVE_ELECTRONIC -> Quintuple(0.96, 118.0, 5.2, 0.34, 1.46)
                    }
                    val instFreq = baseFreq * freqScale + pitchSweep * exp(-t * 30.0)
                    phase1 += TWO_PI * instFreq / SAMPLE_RATE_HZ
                    phase2 += TWO_PI * (instFreq * 1.593) / SAMPLE_RATE_HZ
                    phase3 += TWO_PI * (instFreq * 2.136) / SAMPLE_RATE_HZ
                    phase4 += TWO_PI * (instFreq * 0.992) / SAMPLE_RATE_HZ

                    val fundamental = (sin(phase1) * 0.78 + sin(phase4) * 0.22) * exp(-t * decayRate)
                    val shellOvertone = (
                        sin(phase2) * 0.62 +
                            sin(phase3) * 0.38
                        ) * exp(-t * (decayRate * 1.55)) * 0.34
                    val stickAttack = (midBand * 0.55 + highBand * 0.45) *
                        exp(-t * 100.0) * (noiseSnap + punch * 0.20)

                    softClip((fundamental + shellOvertone + stickAttack) * baseDrive * punchDrive)
                }

                DrumPadId.HI_HAT_CLOSED -> {
                    val decayRate = when (kitId) {
                        DrumKitId.ACOUSTIC_STUDIO -> 42.0
                        DrumKitId.TRAP_808 -> 55.0
                        DrumKitId.VINTAGE_BREAKBEAT -> 36.0
                        DrumKitId.SYNTHWAVE_ELECTRONIC -> 32.0
                    }
                    // Bell-free dual-noise bronze turbulence + tight chick air
                    val turbulence = softClip((midBand * 0.75 + highBand * 0.65) * (1.0 + hpWhite2 * 0.85))
                    val chick = (turbulence * 0.58 + highBand * 0.52 + crispPink * 0.36) * exp(-t * decayRate)
                    val stickTip = (midBand * 0.65 + lowMidBand * 0.25) * exp(-t * 165.0) * (0.26 + punch * 0.22)
                    softClip((chick + stickTip) * 1.32)
                }

                DrumPadId.HI_HAT_OPEN -> {
                    val decayRate = when (kitId) {
                        DrumKitId.ACOUSTIC_STUDIO -> 5.0
                        DrumKitId.TRAP_808 -> 6.2
                        DrumKitId.VINTAGE_BREAKBEAT -> 5.5
                        DrumKitId.SYNTHWAVE_ELECTRONIC -> 4.5
                    }
                    val pairBeat = 0.88 + 0.12 * sin(TWO_PI * 11.5 * t)
                    val turbulence = softClip((midBand * 0.70 + highBand * 0.60) * (1.0 + hpWhite2 * 0.80))
                    val sizzle = (turbulence * 0.52 + lowMidBand * 0.28 + highBand * 0.48 + crispPink * 0.32) *
                        pairBeat * exp(-t * decayRate)
                    val stickAttack = (midBand * 0.60 + lowMidBand * 0.30) * exp(-t * 95.0) * (0.25 + punch * 0.22)
                    softClip((sizzle + stickAttack) * 1.26)
                }

                DrumPadId.CRASH -> {
                    val decayRate = when (kitId) {
                        DrumKitId.ACOUSTIC_STUDIO -> 2.10
                        DrumKitId.TRAP_808 -> 2.80
                        DrumKitId.VINTAGE_BREAKBEAT -> 2.45
                        DrumKitId.SYNTHWAVE_ELECTRONIC -> 2.00
                    }
                    // Zero sine waves -> zero bell/gong pitch! Pure chaotic multi-band bronze splash
                    val bloomEnv = (0.45 + 0.55 * min(1.0, t / 0.020)) * exp(-t * decayRate)
                    val highShimmerEnv = exp(-t * (decayRate * 1.45))

                    // Chaos brass turbulence from saturating & cross-modulating two noise streams
                    val chaosSplash = softClip((lowMidBand * 0.75 + midBand * 0.85) * (1.0 + hpWhite2 * 0.90))
                    val bodyRoar = (lowMidBand * 0.88 + midBand * 0.78 + pink * 0.36) * bloomEnv
                    val airWash = (highBand * 0.62 + crispPink * 0.26) * highShimmerEnv
                    val stickBurst = (lowMidBand * 0.85 + midBand * 0.65) *
                        exp(-t * 44.0) * (0.34 + punch * 0.26)

                    softClip((chaosSplash * bloomEnv * 0.62 + bodyRoar * 0.82 + airWash + stickBurst) * 1.32)
                }

                DrumPadId.RIDE -> {
                    val decayRate = when (kitId) {
                        DrumKitId.ACOUSTIC_STUDIO -> 2.9
                        DrumKitId.TRAP_808 -> 3.6
                        DrumKitId.VINTAGE_BREAKBEAT -> 3.2
                        DrumKitId.SYNTHWAVE_ELECTRONIC -> 2.7
                    }
                    val woodStick = (
                        sin(TWO_PI * 1380.0 * t + pink * 0.22) * 0.48 +
                            midBand * 0.62
                        ) * exp(-t * 22.0) * (0.45 + punch * 0.16)
                    val chaosWash = softClip((lowMidBand * 0.55 + midBand * 0.70 + highBand * 0.45) * (1.0 + hpWhite2 * 0.65))
                    val bowWash = (chaosWash * 0.58 + lowMidBand * 0.42 + highBand * 0.34) * exp(-t * decayRate)
                    softClip((woodStick + bowWash) * 1.26)
                }
            }

            drySignal[i] = sample
        }

        return finalizeSamples(
            drySignal = drySignal,
            padId = padId,
            profile = profile,
            attackFadeSamples = 52
        )
    }

    private fun finalizeSamples(
        drySignal: DoubleArray,
        padId: DrumPadId,
        profile: DrumSoundProfile,
        attackFadeSamples: Int
    ): ShortArray {
        val processed = applyToneAndRoom(
            drySignal = drySignal,
            padId = padId,
            profile = profile,
            warmth = profile.clampedWarmth.toDouble()
        )

        val numSamples = processed.size
        val samples = ShortArray(numSamples)
        val attackDivisor = attackFadeSamples.coerceAtLeast(1).toDouble()
        for (i in 0 until numSamples) {
            val attackEnv = min(1.0, i.toDouble() / attackDivisor)
            val tailRemaining = (numSamples - 1 - i).toDouble()
            val releaseEnv = min(1.0, tailRemaining / 560.0)
            val shaped = softClip(processed[i]) * attackEnv * releaseEnv * 30200.0
            samples[i] = shaped.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return samples
    }

    /**
     * Applies variable tonal warmth/brightness tilt, gentle top-end phone-speaker smoothing,
     * early studio reflections, 5-comb + 2-allpass room ambience, and parallel room-mic bus compression.
     */
    private fun applyToneAndRoom(
        drySignal: DoubleArray,
        padId: DrumPadId,
        profile: DrumSoundProfile,
        warmth: Double
    ): DoubleArray {
        val n = drySignal.size
        val out = DoubleArray(n)

        // 1. Variable Warmth / Brightness Tilt + silky 9.5kHz top-end smoothing for cymbals
        val cymbalBaseWarmth = if (padId == DrumPadId.CRASH || padId == DrumPadId.RIDE) {
            max(warmth, 0.52)
        } else {
            warmth
        }
        val lpAlpha = (0.64 - cymbalBaseWarmth * 0.44).coerceIn(0.14, 0.72)
        var lpState = 0.0
        var smoothState = 0.0
        val airBoost = (0.45 - warmth).coerceAtLeast(0.0) * 0.35

        // 2. Early Studio / Arena Wall Reflections (samples at 44.1kHz: ~8ms, 15ms, 23ms, 34ms)
        val erDelays = intArrayOf(353, 661, 1013, 1499)
        val erGains = doubleArrayOf(0.42, 0.32, 0.24, 0.18)
        val erBuffer = DoubleArray(1600)
        var erIdx = 0

        // 3. Five Prime Comb Delays (~29ms, ~37ms, ~47ms, ~61ms, ~73ms) + 2 Allpass Diffusers
        val combDelays = intArrayOf(1279, 1637, 2081, 2689, 3221)
        val combBuffers = Array(combDelays.size) { DoubleArray(combDelays[it]) }
        val combIndices = IntArray(combDelays.size)
        val combLpStates = DoubleArray(combDelays.size)

        val ap1Delay = 223
        val ap2Delay = 89
        val ap1Buf = DoubleArray(ap1Delay)
        val ap2Buf = DoubleArray(ap2Delay)
        var ap1Idx = 0
        var ap2Idx = 0

        val wetTarget = profile.roomPreset.wetMix.toDouble()
        val effectiveWet = when (padId) {
            DrumPadId.KICK_LEFT, DrumPadId.KICK_RIGHT -> wetTarget * 0.62
            DrumPadId.HI_HAT_CLOSED -> wetTarget * 0.55
            DrumPadId.CRASH -> wetTarget * 0.92
            else -> wetTarget
        }
        val decayNorm = profile.roomPreset.decaySeconds.toDouble().coerceIn(0.12, 0.90)
        val baseFeedback = (0.38 + decayNorm * 0.44).coerceAtMost(0.79)
        val damping = profile.roomPreset.damping.toDouble().coerceIn(0.15, 0.75)

        // Tight low-cut on kick reverb send so arena kick booms cleanly without mud
        var sendHpPrevIn = 0.0
        var sendHpPrevOut = 0.0
        // Room-mic bus compressor envelope follower
        var roomEnv = 0.0

        for (i in 0 until n) {
            val raw = drySignal[i]
            lpState += lpAlpha * (raw - lpState)
            val hp = raw - lpState
            val tonedRaw = when {
                cymbalBaseWarmth > 0.52 ->
                    raw * (1.0 - (cymbalBaseWarmth - 0.5) * 0.72) + lpState * ((cymbalBaseWarmth - 0.5) * 0.72)
                else -> raw + hp * airBoost
            }
            // Gentle 1-pole ultrasonic tame (~10kHz) to keep mobile speakers smooth
            smoothState += 0.72 * (tonedRaw - smoothState)
            val toned = smoothState

            if (effectiveWet <= 0.01) {
                out[i] = toned
                continue
            }

            val sendHp = toned - sendHpPrevIn + 0.955 * sendHpPrevOut
            sendHpPrevIn = toned
            sendHpPrevOut = sendHp
            val roomSend = if (padId == DrumPadId.KICK_LEFT || padId == DrumPadId.KICK_RIGHT) {
                sendHp
            } else {
                toned
            }

            // Early reflections tap buffer
            erBuffer[erIdx] = roomSend
            var erSum = 0.0
            for (e in erDelays.indices) {
                val readPos = (erIdx - erDelays[e] + erBuffer.size) % erBuffer.size
                erSum += erBuffer[readPos] * erGains[e]
            }
            erIdx = (erIdx + 1) % erBuffer.size

            // Dense late reverb combs
            var combSum = 0.0
            for (c in combDelays.indices) {
                val buf = combBuffers[c]
                val idx = combIndices[c]
                val delayed = buf[idx]
                combLpStates[c] = delayed * (1.0 - damping) + combLpStates[c] * damping
                buf[idx] = roomSend + combLpStates[c] * baseFeedback
                combIndices[c] = (idx + 1) % combDelays[c]
                combSum += delayed
            }
            val avgComb = combSum / combDelays.size.toDouble()

            val ap1Out = -avgComb + ap1Buf[ap1Idx]
            ap1Buf[ap1Idx] = avgComb + ap1Out * 0.5
            ap1Idx = (ap1Idx + 1) % ap1Delay

            val ap2Out = -ap1Out + ap2Buf[ap2Idx]
            ap2Buf[ap2Idx] = ap1Out + ap2Out * 0.5
            ap2Idx = (ap2Idx + 1) % ap2Delay

            val rawRoom = erSum * 0.52 + ap2Out * 0.85

            // Overhead / Room-Mic Compressor: brings up tail bloom smoothly on studio & arena presets
            val absRoom = abs(rawRoom)
            roomEnv = if (absRoom > roomEnv) {
                roomEnv * 0.92 + absRoom * 0.08
            } else {
                roomEnv * 0.9985 + absRoom * 0.0015
            }
            val compGain = (1.15 / (1.0 + roomEnv * 1.4)).coerceIn(0.45, 1.35)
            val compressedRoom = softClip(rawRoom * compGain)

            out[i] = toned * (1.0 - effectiveWet * 0.18) + compressedRoom * effectiveWet
        }

        // Peak safety
        var maxAbs = 0.0
        for (v in out) {
            val a = abs(v)
            if (a > maxAbs) maxAbs = a
        }
        if (maxAbs > 1.32) {
            val norm = 1.32 / maxAbs
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
        DrumPadId.KICK_LEFT, DrumPadId.KICK_RIGHT -> if (kitId == DrumKitId.TRAP_808) 0.46 else 0.29
        DrumPadId.SNARE -> 0.28
        DrumPadId.TOM_HIGH -> 0.32
        DrumPadId.TOM_MID -> 0.36
        DrumPadId.TOM_FLOOR -> 0.40
        DrumPadId.HI_HAT_OPEN -> 0.50
        DrumPadId.RIDE -> 0.62
        DrumPadId.CRASH -> 0.78
    }

    private fun softClip(x: Double): Double {
        val clamped = max(-2.5, min(2.5, x))
        return clamped / (1.0 + 0.28 * clamped.pow(2.0))
    }

    private fun decodeRiffWavMono(wavBytes: ByteArray): DoubleArray? {
        if (wavBytes.size < WAV_HEADER_BYTES) return null
        if (wavBytes[0] != 'R'.code.toByte() ||
            wavBytes[1] != 'I'.code.toByte() ||
            wavBytes[2] != 'F'.code.toByte() ||
            wavBytes[3] != 'F'.code.toByte()
        ) {
            return null
        }

        val buffer = ByteBuffer.wrap(wavBytes).order(ByteOrder.LITTLE_ENDIAN)
        var channels = 1
        var bitsPerSample = 16
        var dataOffset = -1
        var dataSize = 0

        var pos = 12
        while (pos + 8 <= wavBytes.size) {
            val chunkId = String(wavBytes, pos, 4, Charsets.US_ASCII)
            val chunkSize = buffer.getInt(pos + 4)
            val chunkDataStart = pos + 8
            if (chunkSize < 0 || chunkDataStart > wavBytes.size) break

            when (chunkId) {
                "fmt " -> if (chunkDataStart + 16 <= wavBytes.size) {
                    channels = buffer.getShort(chunkDataStart + 2).toInt().coerceAtLeast(1)
                    bitsPerSample = buffer.getShort(chunkDataStart + 14).toInt()
                }
                "data" -> {
                    dataOffset = chunkDataStart
                    dataSize = min(chunkSize, wavBytes.size - chunkDataStart)
                    break
                }
            }
            val paddedSize = chunkSize + (chunkSize and 1)
            pos = chunkDataStart + paddedSize
        }

        if (dataOffset < 0 || bitsPerSample != 16) return null
        val bytesPerFrame = channels * 2
        val frameCount = dataSize / bytesPerFrame
        if (frameCount <= 0) return null

        val out = DoubleArray(frameCount)
        var byteIdx = dataOffset
        for (i in 0 until frameCount) {
            var sum = 0.0
            for (ch in 0 until channels) {
                sum += buffer.getShort(byteIdx).toDouble() / 32768.0
                byteIdx += 2
            }
            out[i] = sum / channels.toDouble()
        }
        return out
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
