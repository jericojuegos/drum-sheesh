package com.drumsheesh.core.model

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Studio acoustic room simulation presets rendered deterministically by [com.drumsheesh.core.audio.ProceduralDrumSynthesizer].
 */
enum class RoomAmbiencePreset(
    val id: String,
    val displayName: String,
    val wetMix: Float,
    val decaySeconds: Float,
    val damping: Float
) {
    DRY_BOOTH(
        id = "dry-booth",
        displayName = "Dry Booth",
        wetMix = 0.05f,
        decaySeconds = 0.14f,
        damping = 0.68f
    ),
    WOOD_ROOM(
        id = "wood-room",
        displayName = "Wood Room",
        wetMix = 0.22f,
        decaySeconds = 0.34f,
        damping = 0.44f
    ),
    LIVE_STAGE(
        id = "live-stage",
        displayName = "Live Room",
        wetMix = 0.36f,
        decaySeconds = 0.56f,
        damping = 0.30f
    ),
    ARENA_VERB(
        id = "arena",
        displayName = "Big Arena",
        wetMix = 0.52f,
        decaySeconds = 0.80f,
        damping = 0.22f
    )
}

/**
 * One-tap studio sound presets shaping room acoustics, transient punch, tonal warmth, and pad tuning.
 */
enum class SoundCharacterPreset(
    val id: String,
    val displayName: String,
    val subtitle: String
) {
    STUDIO_NATURAL(
        id = "studio-natural",
        displayName = "Studio Natural",
        subtitle = "Balanced wood room, natural shell ring & crisp cymbals"
    ),
    TIGHT_DRY_POP(
        id = "tight-pop",
        displayName = "Tight Pop",
        subtitle = "Dry booth isolation, tight snappy snare & controlled toms"
    ),
    WARM_VINTAGE(
        id = "warm-vintage",
        displayName = "Warm Vintage",
        subtitle = "Ribbon-mic warmth, deeper tuned shells & fat kick thump"
    ),
    ARENA_ROCK(
        id = "arena-rock",
        displayName = "Arena Rock",
        subtitle = "Big room ambience, hard beater click & explosive crash"
    );

    fun toProfile(): DrumSoundProfile = when (this) {
        STUDIO_NATURAL -> DrumSoundProfile(
            characterPreset = this,
            roomPreset = RoomAmbiencePreset.WOOD_ROOM,
            punchAmount = 0.64f,
            warmthTone = 0.56f,
            padSettings = emptyMap()
        )
        TIGHT_DRY_POP -> DrumSoundProfile(
            characterPreset = this,
            roomPreset = RoomAmbiencePreset.DRY_BOOTH,
            punchAmount = 0.80f,
            warmthTone = 0.46f,
            padSettings = mapOf(
                DrumPadId.SNARE to PadMixSetting(volume = 1.10f, pitchSemitones = 1.0f),
                DrumPadId.KICK_LEFT to PadMixSetting(volume = 1.08f, pitchSemitones = 0.5f),
                DrumPadId.KICK_RIGHT to PadMixSetting(volume = 1.08f, pitchSemitones = 0.5f),
                DrumPadId.HI_HAT_CLOSED to PadMixSetting(volume = 0.86f, pitchSemitones = 0f),
                DrumPadId.CRASH to PadMixSetting(volume = 0.90f, pitchSemitones = -1.0f),
                DrumPadId.TOM_HIGH to PadMixSetting(volume = 0.98f, pitchSemitones = 0.5f),
                DrumPadId.TOM_MID to PadMixSetting(volume = 0.98f, pitchSemitones = 0f)
            )
        )
        WARM_VINTAGE -> DrumSoundProfile(
            characterPreset = this,
            roomPreset = RoomAmbiencePreset.WOOD_ROOM,
            punchAmount = 0.52f,
            warmthTone = 0.86f,
            padSettings = mapOf(
                DrumPadId.SNARE to PadMixSetting(volume = 1.04f, pitchSemitones = -1.5f),
                DrumPadId.KICK_LEFT to PadMixSetting(volume = 1.14f, pitchSemitones = -1.0f),
                DrumPadId.KICK_RIGHT to PadMixSetting(volume = 1.14f, pitchSemitones = -1.0f),
                DrumPadId.TOM_HIGH to PadMixSetting(volume = 1.02f, pitchSemitones = -1.5f),
                DrumPadId.TOM_MID to PadMixSetting(volume = 1.02f, pitchSemitones = -1.5f),
                DrumPadId.TOM_FLOOR to PadMixSetting(volume = 1.08f, pitchSemitones = -2.0f),
                DrumPadId.CRASH to PadMixSetting(volume = 0.88f, pitchSemitones = -2.0f)
            )
        )
        ARENA_ROCK -> DrumSoundProfile(
            characterPreset = this,
            roomPreset = RoomAmbiencePreset.ARENA_VERB,
            punchAmount = 0.90f,
            warmthTone = 0.52f,
            padSettings = mapOf(
                DrumPadId.KICK_LEFT to PadMixSetting(volume = 1.18f, pitchSemitones = -0.5f),
                DrumPadId.KICK_RIGHT to PadMixSetting(volume = 1.18f, pitchSemitones = -0.5f),
                DrumPadId.SNARE to PadMixSetting(volume = 1.16f, pitchSemitones = -0.5f),
                DrumPadId.TOM_HIGH to PadMixSetting(volume = 1.10f, pitchSemitones = -1.0f),
                DrumPadId.TOM_MID to PadMixSetting(volume = 1.10f, pitchSemitones = -1.0f),
                DrumPadId.TOM_FLOOR to PadMixSetting(volume = 1.14f, pitchSemitones = -1.5f),
                DrumPadId.CRASH to PadMixSetting(volume = 1.04f, pitchSemitones = -1.5f),
                DrumPadId.RIDE to PadMixSetting(volume = 0.98f, pitchSemitones = -1.0f)
            )
        )
    }
}

/**
 * Per-pad mix & tuning controls applied immediately on playback.
 */
data class PadMixSetting(
    val volume: Float = 1.0f,
    val pitchSemitones: Float = 0f
) {
    val clampedVolume: Float
        get() = volume.coerceIn(0f, MAX_VOLUME)

    val clampedPitchSemitones: Float
        get() = pitchSemitones.coerceIn(MIN_SEMITONES, MAX_SEMITONES)

    val playbackRate: Float
        get() = 2.0.pow(clampedPitchSemitones.toDouble() / 12.0).toFloat().coerceIn(0.5f, 2.0f)

    companion object {
        const val MAX_VOLUME: Float = 1.25f
        const val MIN_SEMITONES: Float = -6f
        const val MAX_SEMITONES: Float = 6f
    }
}

/**
 * Full studio sound configuration for a drum kit.
 */
data class DrumSoundProfile(
    val characterPreset: SoundCharacterPreset? = SoundCharacterPreset.STUDIO_NATURAL,
    val roomPreset: RoomAmbiencePreset = RoomAmbiencePreset.WOOD_ROOM,
    val punchAmount: Float = 0.58f,
    val warmthTone: Float = 0.48f,
    val padSettings: Map<DrumPadId, PadMixSetting> = emptyMap()
) {
    val clampedPunch: Float
        get() = punchAmount.coerceIn(0f, 1f)

    val clampedWarmth: Float
        get() = warmthTone.coerceIn(0f, 1f)

    fun settingFor(padId: DrumPadId): PadMixSetting = padSettings[padId] ?: PadMixSetting()

    /**
     * Compact key identifying synthesis-affecting parameters (room, punch, warmth).
     * Per-pad volume and pitch are applied in real time via SoundPool playback params.
     */
    fun synthCacheKey(): String {
        val punchBucket = (clampedPunch * 20f).roundToInt() * 5
        val warmthBucket = (clampedWarmth * 20f).roundToInt() * 5
        return "${roomPreset.id}_p${punchBucket}_w${warmthBucket}"
    }

    companion object {
        fun defaultForKit(kitId: DrumKitId): DrumSoundProfile = when (kitId) {
            DrumKitId.ACOUSTIC_STUDIO -> SoundCharacterPreset.STUDIO_NATURAL.toProfile()
            DrumKitId.TRAP_808 -> DrumSoundProfile(
                characterPreset = null,
                roomPreset = RoomAmbiencePreset.DRY_BOOTH,
                punchAmount = 0.78f,
                warmthTone = 0.56f
            )
            DrumKitId.VINTAGE_BREAKBEAT -> SoundCharacterPreset.WARM_VINTAGE.toProfile().copy(
                padSettings = emptyMap()
            )
            DrumKitId.SYNTHWAVE_ELECTRONIC -> DrumSoundProfile(
                characterPreset = null,
                roomPreset = RoomAmbiencePreset.LIVE_STAGE,
                punchAmount = 0.70f,
                warmthTone = 0.38f
            )
        }
    }
}
