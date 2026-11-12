package com.drumsheesh.core.engine

import com.drumsheesh.core.model.DrumPadId
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Defines a normalized [0f..1f] spatial hit zone on the landscape 10-pad Drum Stage.
 */
data class PadHitZone(
    val padId: DrumPadId,
    val centerX: Float,
    val centerY: Float,
    val radiusX: Float,
    val radiusY: Float,
    val isCircular: Boolean = true,
    val isAsymmetrical: Boolean = true
) {
    /**
     * Returns the horizontal center adjusted for Left-Handed mode when the pad is asymmetrical.
     */
    fun effectiveCenterX(isLeftHanded: Boolean): Float =
        if (isLeftHanded && isAsymmetrical) 1.0f - centerX else centerX
}

/**
 * Resolved pad hit result containing the hit [padId], radial [velocity] (`0.45f..1.0f`),
 * normalized stage coordinates, and whether this hit chokes an open hi-hat.
 */
data class PadHitResult(
    val padId: DrumPadId,
    val velocity: Float,
    val normalizedX: Float,
    val normalizedY: Float,
    val isChokeTrigger: Boolean = padId.chokesOpenHiHat
)

/**
 * Resolves normalized touch coordinates `(xNorm, yNorm)` in `[0f..1f]` into a [PadHitResult]
 * with radial velocity (`1.0f` dead-center -> `0.45f` outer rim) and Left/Right-handed mirroring.
 */
object PadHitResolver {
    const val MIN_RADIAL_VELOCITY: Float = 0.45f
    const val MAX_RADIAL_VELOCITY: Float = 1.0f

    private val defaultZones: List<PadHitZone> = listOf(
        // Top Row: Crash (Left), High Tom (Center-Left), Mid Tom (Center-Right), Ride (Right)
        PadHitZone(
            padId = DrumPadId.CRASH,
            centerX = 0.16f,
            centerY = 0.20f,
            radiusX = 0.135f,
            radiusY = 0.185f,
            isCircular = true,
            isAsymmetrical = true
        ),
        PadHitZone(
            padId = DrumPadId.TOM_HIGH,
            centerX = 0.39f,
            centerY = 0.21f,
            radiusX = 0.105f,
            radiusY = 0.16f,
            isCircular = true,
            isAsymmetrical = true
        ),
        PadHitZone(
            padId = DrumPadId.TOM_MID,
            centerX = 0.61f,
            centerY = 0.21f,
            radiusX = 0.105f,
            radiusY = 0.16f,
            isCircular = true,
            isAsymmetrical = true
        ),
        PadHitZone(
            padId = DrumPadId.RIDE,
            centerX = 0.84f,
            centerY = 0.20f,
            radiusX = 0.145f,
            radiusY = 0.195f,
            isCircular = true,
            isAsymmetrical = true
        ),

        // Middle Row: Open Hi-Hat (Outer Left), Closed Hi-Hat (Inner Left), Snare (Center), Floor Tom (Right)
        PadHitZone(
            padId = DrumPadId.HI_HAT_OPEN,
            centerX = 0.10f,
            centerY = 0.52f,
            radiusX = 0.095f,
            radiusY = 0.155f,
            isCircular = true,
            isAsymmetrical = true
        ),
        PadHitZone(
            padId = DrumPadId.HI_HAT_CLOSED,
            centerX = 0.26f,
            centerY = 0.54f,
            radiusX = 0.10f,
            radiusY = 0.16f,
            isCircular = true,
            isAsymmetrical = true
        ),
        PadHitZone(
            padId = DrumPadId.SNARE,
            centerX = 0.48f,
            centerY = 0.52f,
            radiusX = 0.125f,
            radiusY = 0.185f,
            isCircular = true,
            isAsymmetrical = true
        ),
        PadHitZone(
            padId = DrumPadId.TOM_FLOOR,
            centerX = 0.78f,
            centerY = 0.53f,
            radiusX = 0.135f,
            radiusY = 0.195f,
            isCircular = true,
            isAsymmetrical = true
        ),

        // Bottom Row: Dual Bass Drum + Kick Pedals
        PadHitZone(
            padId = DrumPadId.KICK_LEFT,
            centerX = 0.35f,
            centerY = 0.84f,
            radiusX = 0.135f,
            radiusY = 0.135f,
            isCircular = false,
            isAsymmetrical = true
        ),
        PadHitZone(
            padId = DrumPadId.KICK_RIGHT,
            centerX = 0.65f,
            centerY = 0.84f,
            radiusX = 0.135f,
            radiusY = 0.135f,
            isCircular = false,
            isAsymmetrical = true
        )
    )

    /**
     * Returns all 10 [PadHitZone] targets with horizontal coordinates adjusted for [isLeftHanded].
     */
    fun getZonesForHandedness(isLeftHanded: Boolean = false): List<PadHitZone> {
        if (!isLeftHanded) return defaultZones
        return defaultZones.map { zone ->
            zone.copy(centerX = zone.effectiveCenterX(isLeftHanded = true))
        }
    }

    /**
     * Resolves a normalized stage touch `(xNorm, yNorm)` into a [PadHitResult], or `null` if the touch
     * lands on empty stage floor outside all 10 pad zones.
     */
    fun resolveHit(
        xNorm: Float,
        yNorm: Float,
        isLeftHanded: Boolean = false
    ): PadHitResult? {
        if (xNorm.isNaN() || yNorm.isNaN() || xNorm < 0f || xNorm > 1f || yNorm < 0f || yNorm > 1f) {
            return null
        }

        var bestZone: PadHitZone? = null
        var bestNormalizedDistance = Float.MAX_VALUE

        for (zone in defaultZones) {
            val cx = zone.effectiveCenterX(isLeftHanded)
            val cy = zone.centerY
            val dx = (xNorm - cx) / zone.radiusX
            val dy = (yNorm - cy) / zone.radiusY

            val normDistance = if (zone.isCircular) {
                sqrt(dx * dx + dy * dy)
            } else {
                // Rounded pedal box metric (chebyshev-blended radial distance)
                val maxAxis = max(abs(dx), abs(dy))
                val euclidean = sqrt(dx * dx + dy * dy) * 0.7071f
                max(maxAxis, euclidean)
            }

            if (normDistance <= 1.0f && normDistance < bestNormalizedDistance) {
                bestNormalizedDistance = normDistance
                bestZone = zone
            }
        }

        val hitZone = bestZone ?: return null
        val rawVelocity = MAX_RADIAL_VELOCITY -
            bestNormalizedDistance.coerceIn(0f, 1f) * (MAX_RADIAL_VELOCITY - MIN_RADIAL_VELOCITY)
        val clampedVelocity = min(MAX_RADIAL_VELOCITY, max(MIN_RADIAL_VELOCITY, rawVelocity))

        return PadHitResult(
            padId = hitZone.padId,
            velocity = clampedVelocity,
            normalizedX = xNorm,
            normalizedY = yNorm,
            isChokeTrigger = hitZone.padId.chokesOpenHiHat
        )
    }
}

/**
 * Pure-Kotlin state machine that tracks active `HI_HAT_OPEN` audio stream IDs so that any
 * `HI_HAT_CLOSED` trigger immediately returns the open streams to choke/stop.
 */
class HiHatChokeTracker {
    private val activeOpenHiHatStreams = LinkedHashSet<Int>()

    @Synchronized
    fun recordOpenHiHatStream(streamId: Int) {
        if (streamId > 0) {
            activeOpenHiHatStreams.add(streamId)
        }
    }

    @Synchronized
    fun onPadTriggered(padId: DrumPadId): List<Int> {
        if (!padId.chokesOpenHiHat || activeOpenHiHatStreams.isEmpty()) {
            return emptyList()
        }
        val choked = activeOpenHiHatStreams.toList()
        activeOpenHiHatStreams.clear()
        return choked
    }

    @Synchronized
    fun activeOpenStreamCount(): Int = activeOpenHiHatStreams.size

    @Synchronized
    fun clear() {
        activeOpenHiHatStreams.clear()
    }
}
