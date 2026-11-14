package com.drumsheesh.core.engine

import com.drumsheesh.core.model.SnareArticulation
import com.drumsheesh.core.model.SnareHitParameters
import com.drumsheesh.core.model.SnareVelocityTier
import com.drumsheesh.core.model.SnareZone
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.pow

/**
 * Pure-Kotlin snare drum interaction engine handling:
 * - Multi-tier velocity sensitivity (Ghost Notes up to Fortissimo Impact)
 * - Multi-zone surface mapping (Center Head, Edge Head, Cross-Stick, Rimshot)
 * - Smooth gain interpolation between velocity layers
 * - Round-robin sample alternation + subtle pitch humanization to eliminate the "machine gun" effect
 */
object SnareInteractionEngine {
    const val CENTER_ZONE_MAX_RADIUS: Float = 0.46f
    const val EDGE_ZONE_MAX_RADIUS: Float = 0.78f
    const val ROUND_ROBIN_VARIANTS: Int = 2

    private val roundRobinCounter = AtomicInteger(0)
    private val microPitchRates = floatArrayOf(0.991f, 1.008f, 0.996f, 1.013f)
    private val microGainOffsets = floatArrayOf(0.98f, 1.02f, 0.99f, 1.01f)

    fun resolveZone(radialDistance: Float): SnareZone {
        val d = radialDistance.coerceIn(0f, 1.2f)
        return when {
            d <= CENTER_ZONE_MAX_RADIUS -> SnareZone.CENTER_HEAD
            d <= EDGE_ZONE_MAX_RADIUS -> SnareZone.EDGE_HEAD
            else -> SnareZone.RIM_SIDE
        }
    }

    fun resolveVelocityTier(velocity: Float): SnareVelocityTier {
        val v = velocity.coerceIn(0f, 1f)
        return when {
            v < 0.30f -> SnareVelocityTier.PIANISSIMO_GHOST
            v < 0.50f -> SnareVelocityTier.PIANO_SOFT
            v < 0.72f -> SnareVelocityTier.MEZZO_STANDARD
            v < 0.88f -> SnareVelocityTier.FORTE_ACCENT
            else -> SnareVelocityTier.FORTISSIMO_IMPACT
        }
    }

    /**
     * Resolves a snare surface strike from normalized radial distance (`0f` center -> `1f` outer rim),
     * vertical offset relative to snare center (`dyNormalized < 0f` = top rim), and optional touch pressure.
     */
    fun resolveStrike(
        radialDistance: Float,
        dyNormalized: Float = 0f,
        touchPressure: Float = -1f,
        roundRobinStepOverride: Int? = null
    ): SnareHitParameters {
        val clampedDist = radialDistance.coerceIn(0f, 1f)
        val zone = resolveZone(clampedDist)
        val isRimTopArc = dyNormalized < 0f
        val hasDynamicPressure = touchPressure in 0.05f..0.95f

        val baseRadialVelocity = when (zone) {
            SnareZone.CENTER_HEAD -> {
                val frac = (clampedDist / CENTER_ZONE_MAX_RADIUS).coerceIn(0f, 1f)
                1.00f - frac * 0.38f // 1.00 dead center -> 0.62 mid head
            }
            SnareZone.EDGE_HEAD -> {
                val frac = ((clampedDist - CENTER_ZONE_MAX_RADIUS) /
                    (EDGE_ZONE_MAX_RADIUS - CENTER_ZONE_MAX_RADIUS)).coerceIn(0f, 1f)
                0.62f - frac * 0.38f // 0.62 -> 0.24 ghost/edge
            }
            SnareZone.RIM_SIDE -> {
                if (isRimTopArc) 0.94f else 0.52f
            }
        }

        val effectiveVelocity = if (hasDynamicPressure) {
            val normalizedPressure = ((touchPressure - 0.05f) / 0.90f).coerceIn(0f, 1f)
            (baseRadialVelocity * 0.45f + normalizedPressure * 0.55f).coerceIn(0.18f, 1.0f)
        } else {
            baseRadialVelocity.coerceIn(0.18f, 1.0f)
        }

        val velocityTier = resolveVelocityTier(effectiveVelocity)
        val articulation = resolveArticulation(
            zone = zone,
            velocityTier = velocityTier,
            velocity = effectiveVelocity,
            isRimTopArc = isRimTopArc,
            touchPressure = touchPressure
        )

        val step = (roundRobinStepOverride ?: roundRobinCounter.getAndIncrement()) and 0x7FFF_FFFF
        val roundRobinIndex = step % ROUND_ROBIN_VARIANTS
        val microIdx = step % microPitchRates.size

        // Smooth continuous gain curve across velocity tiers to prevent abrupt stepping
        val continuousCurve = (0.24f + 0.76f * effectiveVelocity.pow(0.82f)).coerceIn(0.20f, 1.0f)
        val smoothedGain = (continuousCurve * 0.78f + velocityTier.gainMultiplier * 0.22f) *
            microGainOffsets[microIdx]

        return SnareHitParameters(
            articulation = articulation,
            zone = zone,
            velocityTier = velocityTier,
            velocity = effectiveVelocity,
            radialDistance = clampedDist,
            gainMultiplier = smoothedGain.coerceIn(0.18f, 1.0f),
            roundRobinIndex = roundRobinIndex,
            pitchJitterRate = microPitchRates[microIdx]
        )
    }

    private fun resolveArticulation(
        zone: SnareZone,
        velocityTier: SnareVelocityTier,
        velocity: Float,
        isRimTopArc: Boolean,
        touchPressure: Float
    ): SnareArticulation {
        return when (zone) {
            SnareZone.RIM_SIDE -> {
                when {
                    touchPressure in 0.05f..0.50f -> SnareArticulation.CROSS_STICK
                    touchPressure in 0.76f..0.95f -> SnareArticulation.RIMSHOT
                    isRimTopArc -> SnareArticulation.RIMSHOT
                    else -> SnareArticulation.CROSS_STICK
                }
            }
            SnareZone.EDGE_HEAD -> {
                if (velocity < 0.32f) SnareArticulation.GHOST_NOTE else SnareArticulation.EDGE_CRISP
            }
            SnareZone.CENTER_HEAD -> {
                when (velocityTier) {
                    SnareVelocityTier.PIANISSIMO_GHOST -> SnareArticulation.GHOST_NOTE
                    SnareVelocityTier.PIANO_SOFT,
                    SnareVelocityTier.MEZZO_STANDARD -> SnareArticulation.CENTER_SOFT
                    SnareVelocityTier.FORTE_ACCENT,
                    SnareVelocityTier.FORTISSIMO_IMPACT -> SnareArticulation.CENTER_HARD
                }
            }
        }
    }
}
