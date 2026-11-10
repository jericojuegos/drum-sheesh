package com.drumsheesh.core.model

enum class DrumKitId(
    val id: String,
    val displayName: String,
    val description: String,
    val defaultBpm: Int
) {
    ACOUSTIC_STUDIO(
        id = "acoustic-studio",
        displayName = "Acoustic Studio",
        description = "Warm birch shells, crisp snare wires, and shimmer bronze cymbals",
        defaultBpm = 110
    ),
    TRAP_808(
        id = "808-trap",
        displayName = "808 Trap",
        description = "Deep sub-bass 808 kick, tight clap-snare, and crisp metallic hats",
        defaultBpm = 140
    ),
    VINTAGE_BREAKBEAT(
        id = "vintage-breakbeat",
        displayName = "Vintage Breakbeat",
        description = "Punchy 70s tape-saturated funk kit with dry pocket snare",
        defaultBpm = 94
    ),
    SYNTHWAVE_ELECTRONIC(
        id = "synthwave-electronic",
        displayName = "Synthwave Electronic",
        description = "Gated 80s analog toms, punchy synth kick, and FM cymbals",
        defaultBpm = 118
    );

    companion object {
        fun fromId(id: String): DrumKitId =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) || it.name.equals(id, ignoreCase = true) }
                ?: ACOUSTIC_STUDIO
    }
}
