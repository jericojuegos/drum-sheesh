package com.drumsheesh.core.model

/**
 * Spatial surface zones on the snare drum head and hoop.
 */
enum class SnareZone(val displayLabel: String) {
    CENTER_HEAD("Center"),
    EDGE_HEAD("Edge"),
    RIM_SIDE("Rim")
}

/**
 * Multi-tier dynamic intensity mapping from ghost notes to fortissimo rim/head impact.
 */
enum class SnareVelocityTier(
    val displayLabel: String,
    val gainMultiplier: Float
) {
    PIANISSIMO_GHOST("Ghost", 0.56f),
    PIANO_SOFT("Soft", 0.72f),
    MEZZO_STANDARD("Med", 0.86f),
    FORTE_ACCENT("Forte", 0.96f),
    FORTISSIMO_IMPACT("Full", 1.00f)
}

/**
 * Distinct acoustic and electronic snare articulations triggered by zone + velocity.
 */
enum class SnareArticulation(
    val id: String,
    val displayLabel: String,
    val assetPrefix: String
) {
    GHOST_NOTE(
        id = "ghost_note",
        displayLabel = "GHOST",
        assetPrefix = "snare_ghost"
    ),
    CENTER_SOFT(
        id = "center_soft",
        displayLabel = "SOFT",
        assetPrefix = "snare_med"
    ),
    CENTER_HARD(
        id = "center_hard",
        displayLabel = "SNARE",
        assetPrefix = "snare_hard"
    ),
    EDGE_CRISP(
        id = "edge_crisp",
        displayLabel = "EDGE",
        assetPrefix = "snare_edge"
    ),
    CROSS_STICK(
        id = "cross_stick",
        displayLabel = "SIDESTICK",
        assetPrefix = "snare_sidestick"
    ),
    RIMSHOT(
        id = "rimshot",
        displayLabel = "RIMSHOT",
        assetPrefix = "snare_rimshot"
    )
}

/**
 * Resolved snare strike metadata combining zone detection, velocity tiering,
 * smooth gain interpolation, and anti-"machine gun" round-robin variation.
 */
data class SnareHitParameters(
    val articulation: SnareArticulation,
    val zone: SnareZone,
    val velocityTier: SnareVelocityTier,
    val velocity: Float,
    val radialDistance: Float,
    val gainMultiplier: Float,
    val roundRobinIndex: Int,
    val pitchJitterRate: Float
)
