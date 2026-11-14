package com.drumsheesh.ui.components

import android.graphics.Paint as AndroidPaint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drumsheesh.core.engine.PadHitResolver
import com.drumsheesh.core.engine.PadHitZone
import com.drumsheesh.core.model.DrumKitId
import com.drumsheesh.core.model.DrumPadId
import com.drumsheesh.core.model.PadCategory
import com.drumsheesh.ui.theme.StudioColors
import com.drumsheesh.ui.theme.StudioTokens
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Immutable snapshot of a single pad hit impulse for zero-allocation Canvas rendering.
 */
data class PadHitVisualState(
    val padId: DrumPadId,
    val touchXNorm: Float,
    val touchYNorm: Float,
    val velocity: Float,
    val progress: Float, // 0.0f (fresh hit) -> 1.0f (fully decayed)
    val zoneLabel: String? = null
)

/**
 * Top Studio Transport HUD Bar (`StudioTopBar`) matching `docs/foundation/design-system.md`.
 */
@Composable
fun StudioTopBar(
    selectedKit: DrumKitId,
    isLeftHanded: Boolean,
    bpm: Int,
    lastVelocityPercent: Int?,
    isChokeFlashActive: Boolean,
    onSelectKit: (DrumKitId) -> Unit,
    onToggleHandedness: () -> Unit,
    onAdjustBpm: (Int) -> Unit,
    onOpenSoundModal: (() -> Unit)? = null,
    soundBadgeLabel: String? = null,
    onOpenAiModal: (() -> Unit)? = null,
    onToggleLoopDrawer: (() -> Unit)? = null,
    isLoopDrawerOpen: Boolean = false,
    isRecordingLoop: Boolean = false,
    modifier: Modifier = Modifier
) {
    var kitMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .background(StudioColors.SurfaceDark.copy(alpha = 0.96f))
            .border(width = 1.dp, color = StudioColors.BorderDark)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = StudioTokens.SpaceCompact),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(StudioTokens.SpaceSmall)
    ) {
        // Brand Pill + Kit Selector Dropdown + Left/Right Handedness Flip
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(StudioTokens.RadiusSm))
                .background(StudioColors.ElectricAmberSubtle)
                .border(1.dp, StudioColors.ElectricAmber, RoundedCornerShape(StudioTokens.RadiusSm))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = "DRUM SHEESH",
                color = StudioColors.ElectricAmber,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Box {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(StudioTokens.RadiusMd))
                    .background(StudioColors.SurfaceElevatedDark)
                    .border(1.dp, StudioColors.BorderDark, RoundedCornerShape(StudioTokens.RadiusMd))
                    .clickable { kitMenuExpanded = true }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "KIT: ${selectedKit.displayName.uppercase()}",
                    color = StudioColors.TextPrimaryDark,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "▼",
                    color = StudioColors.ElectricAmber,
                    fontSize = 10.sp
                )
            }

            DropdownMenu(
                expanded = kitMenuExpanded,
                onDismissRequest = { kitMenuExpanded = false },
                modifier = Modifier.background(StudioColors.SurfaceElevatedDark)
            ) {
                DrumKitId.entries.forEach { kit ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "${kit.displayName} (${kit.defaultBpm} BPM)",
                                color = if (kit == selectedKit) {
                                    StudioColors.ElectricAmber
                                } else {
                                    StudioColors.TextPrimaryDark
                                },
                                fontWeight = if (kit == selectedKit) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        onClick = {
                            kitMenuExpanded = false
                            onSelectKit(kit)
                        }
                    )
                }
            }
        }

        if (onOpenSoundModal != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(StudioTokens.RadiusMd))
                    .background(StudioColors.GoodBlue.copy(alpha = 0.16f))
                    .border(1.dp, StudioColors.GoodBlue, RoundedCornerShape(StudioTokens.RadiusMd))
                    .clickable { onOpenSoundModal() }
                    .padding(horizontal = 9.dp, vertical = 5.dp)
            ) {
                Text(
                    text = soundBadgeLabel?.let { "🎛 SOUND: $it" } ?: "🎛 SOUND / MIX",
                    color = StudioColors.GoodBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Left/Right Handedness Toggle Pill
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(StudioTokens.RadiusMd))
                .background(
                    if (isLeftHanded) StudioColors.ElectricAmberSubtle else StudioColors.SurfaceElevatedDark
                )
                .border(
                    width = 1.dp,
                    color = if (isLeftHanded) StudioColors.ElectricAmber else StudioColors.BorderDark,
                    shape = RoundedCornerShape(StudioTokens.RadiusMd)
                )
                .clickable { onToggleHandedness() }
                .padding(horizontal = 9.dp, vertical = 5.dp)
        ) {
            Text(
                text = if (isLeftHanded) "L-HAND" else "R-HAND",
                color = if (isLeftHanded) StudioColors.ElectricAmber else StudioColors.TextPrimaryDark,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
            )
        }

        // Monospace BPM Counter with -/+ controls
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(StudioTokens.RadiusSm))
                    .background(StudioColors.SurfaceElevatedDark)
                    .clickable { onAdjustBpm(-5) }
                    .padding(horizontal = 7.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "-5",
                    color = StudioColors.TextSecondaryDark,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(StudioTokens.RadiusMd))
                    .background(StudioColors.SurfaceInnerDark)
                    .border(1.dp, StudioColors.BorderDark, RoundedCornerShape(StudioTokens.RadiusMd))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "$bpm BPM",
                    color = StudioColors.ElectricAmber,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(StudioTokens.RadiusSm))
                    .background(StudioColors.SurfaceElevatedDark)
                    .clickable { onAdjustBpm(5) }
                    .padding(horizontal = 7.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "+5",
                    color = StudioColors.TextSecondaryDark,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        if (onOpenAiModal != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(StudioTokens.RadiusMd))
                    .background(StudioColors.AiCoachIndigo.copy(alpha = 0.18f))
                    .border(1.dp, StudioColors.AiCoachIndigo, RoundedCornerShape(StudioTokens.RadiusMd))
                    .clickable { onOpenAiModal() }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "✦ AI GROOVE",
                    color = StudioColors.AiCoachIndigo,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        if (onToggleLoopDrawer != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(StudioTokens.RadiusMd))
                    .background(
                        when {
                            isRecordingLoop -> StudioColors.MissOrRecRed.copy(alpha = 0.22f)
                            isLoopDrawerOpen -> StudioColors.ElectricAmberSubtle
                            else -> StudioColors.SurfaceElevatedDark
                        }
                    )
                    .border(
                        width = 1.dp,
                        color = when {
                            isRecordingLoop -> StudioColors.MissOrRecRed
                            isLoopDrawerOpen -> StudioColors.ElectricAmber
                            else -> StudioColors.BorderDark
                        },
                        shape = RoundedCornerShape(StudioTokens.RadiusMd)
                    )
                    .clickable { onToggleLoopDrawer() }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = if (isRecordingLoop) "● REC LOOP" else "LOOP REC",
                    color = when {
                        isRecordingLoop -> StudioColors.MissOrRecRed
                        isLoopDrawerOpen -> StudioColors.ElectricAmber
                        else -> StudioColors.TextPrimaryDark
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        if (isChokeFlashActive) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(StudioTokens.RadiusSm))
                    .background(StudioColors.ElectricAmberSubtle)
                    .border(1.dp, StudioColors.ElectricAmber, RoundedCornerShape(StudioTokens.RadiusSm))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "HH CHOKE",
                    color = StudioColors.ElectricAmber,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(StudioTokens.RadiusMd))
                .background(StudioColors.SurfaceElevatedDark)
                .border(1.dp, StudioColors.BorderDark, RoundedCornerShape(StudioTokens.RadiusMd))
                .padding(horizontal = 9.dp, vertical = 4.dp)
        ) {
            val velText = if (lastVelocityPercent != null) {
                "VEL ${lastVelocityPercent}%"
            } else {
                "8-TOUCH"
            }
            Text(
                text = velText,
                style = MaterialTheme.typography.labelSmall,
                color = if (lastVelocityPercent != null) {
                    StudioColors.PerfectGreen
                } else {
                    StudioColors.TextSecondaryDark
                },
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

/**
 * Kit-adaptive studio finish colors for realistic drum shells, heads, and cymbals.
 */
private data class KitFinishTheme(
    val shellPrimary: Color,
    val shellDark: Color,
    val snareHeadLight: Color,
    val snareHeadEdge: Color,
    val tomHeadCenter: Color,
    val tomHeadEdge: Color,
    val cymbalBright: Color,
    val cymbalMid: Color,
    val cymbalDark: Color,
    val isElectronicMesh: Boolean
) {
    companion object {
        fun forKit(kitId: DrumKitId): KitFinishTheme = when (kitId) {
            DrumKitId.ACOUSTIC_STUDIO -> KitFinishTheme(
                shellPrimary = Color(0xFFB43F1C), // Warm Sunburst Maple Lacquer
                shellDark = Color(0xFF451206),
                snareHeadLight = Color(0xFFF2ECE0), // Remo Coated Ambassador Cream
                snareHeadEdge = Color(0xFFC8BFA9),
                tomHeadCenter = Color(0xFF2B3446), // Clear/Ebony Studio Head
                tomHeadEdge = Color(0xFF131824),
                cymbalBright = Color(0xFFFFF0BA), // B20 Cast Bronze
                cymbalMid = Color(0xFFD19335),
                cymbalDark = Color(0xFF70460E),
                isElectronicMesh = false
            )
            DrumKitId.TRAP_808 -> KitFinishTheme(
                shellPrimary = Color(0xFF3E355E), // Matte Obsidian + Violet Anodized Rim
                shellDark = Color(0xFF141122),
                snareHeadLight = Color(0xFF28263E),
                snareHeadEdge = Color(0xFF141322),
                tomHeadCenter = Color(0xFF232138),
                tomHeadEdge = Color(0xFF0E0D18),
                cymbalBright = Color(0xFFEBD089),
                cymbalMid = Color(0xFF9F7C34),
                cymbalDark = Color(0xFF4C3A14),
                isElectronicMesh = true
            )
            DrumKitId.VINTAGE_BREAKBEAT -> KitFinishTheme(
                shellPrimary = Color(0xFFC27A1E), // Vintage Champagne Sparkle Gold
                shellDark = Color(0xFF522D06),
                snareHeadLight = Color(0xFFEBE0C8), // Aged Calfskin/Coated Tone
                snareHeadEdge = Color(0xFFBFA980),
                tomHeadCenter = Color(0xFF362E25),
                tomHeadEdge = Color(0xFF191410),
                cymbalBright = Color(0xFFFCE7A2), // Hand-Hammered Turkish Bronze
                cymbalMid = Color(0xFFBE7F28),
                cymbalDark = Color(0xFF5C380C),
                isElectronicMesh = false
            )
            DrumKitId.SYNTHWAVE_ELECTRONIC -> KitFinishTheme(
                shellPrimary = Color(0xFF4338CA), // Retro Synth Indigo-Cyan
                shellDark = Color(0xFF1E1B4B),
                snareHeadLight = Color(0xFF22284A),
                snareHeadEdge = Color(0xFF11142B),
                tomHeadCenter = Color(0xFF1D2745),
                tomHeadEdge = Color(0xFF0B1020),
                cymbalBright = Color(0xFFE0E7FF), // Brushed Chrome-Bronze Hybrid
                cymbalMid = Color(0xFF94A3B8),
                cymbalDark = Color(0xFF334155),
                isElectronicMesh = true
            )
        }
    }
}

/**
 * Pre-computed per-pad visual geometry and gradient brushes.
 * Rebuilt ONLY when canvas dimensions, handedness, or selected kit change — zero allocations on hit frames!
 */
private class PadVisualGeometry(
    val padId: DrumPadId,
    val cx: Float,
    val cy: Float,
    val rx: Float,
    val ry: Float,
    val radius: Float,
    val shellBrush: Brush,
    val headBrush: Brush,
    val cymbalSweepBrush: Brush?,
    val cymbalBellBrush: Brush?,
    val lugOffsets: Array<Offset>,
    val floorLegEnds: Array<Offset>,
    val badgeCenterY: Float,
    val badgeHalfW: Float,
    val badgeHalfH: Float
)

/**
 * Holds all pre-allocated brushes, geometry, hardware stand points, and native paints outside `Canvas.onDraw`.
 */
private class DrumStageRenderCache {
    private var cachedWidth: Float = -1f
    private var cachedHeight: Float = -1f
    private var cachedZones: List<PadHitZone>? = null
    private var cachedKit: DrumKitId? = null

    var kitTheme: KitFinishTheme = KitFinishTheme.forKit(DrumKitId.ACOUSTIC_STUDIO)
        private set
    var stageSpotlightBrush: Brush = Brush.radialGradient(listOf(Color(0xFF191D30), Color(0xFF0A0B12)))
        private set
    var rugCenter: Offset = Offset.Zero
        private set
    var rugRx: Float = 1f
        private set
    var rugRy: Float = 1f
        private set
    var rackBarStart: Offset = Offset.Zero
        private set
    var rackBarEnd: Offset = Offset.Zero
        private set
    var rackAnchorCenter: Offset = Offset.Zero
        private set

    val padCaches = arrayOfNulls<PadVisualGeometry>(10)
    var padCount: Int = 0
        private set

    fun ensureBuilt(
        canvasW: Float,
        canvasH: Float,
        padZones: List<PadHitZone>,
        selectedKit: DrumKitId
    ) {
        if (cachedWidth == canvasW &&
            cachedHeight == canvasH &&
            cachedZones === padZones &&
            cachedKit == selectedKit
        ) {
            return
        }

        cachedWidth = canvasW
        cachedHeight = canvasH
        cachedZones = padZones
        cachedKit = selectedKit

        val theme = KitFinishTheme.forKit(selectedKit)
        kitTheme = theme

        rugCenter = Offset(canvasW * 0.5f, canvasH * 0.56f)
        rugRx = canvasW * 0.44f
        rugRy = canvasH * 0.43f

        stageSpotlightBrush = Brush.radialGradient(
            colors = listOf(Color(0xFF1F243D), Color(0xFF111322), Color(0xFF08090F)),
            center = Offset(canvasW * 0.5f, canvasH * 0.48f),
            radius = canvasW.coerceAtLeast(canvasH) * 0.72f
        )

        rackBarStart = Offset(canvasW * 0.16f, canvasH * 0.16f)
        rackBarEnd = Offset(canvasW * 0.84f, canvasH * 0.16f)
        rackAnchorCenter = Offset(canvasW * 0.50f, canvasH * 0.74f)

        padCount = padZones.size.coerceAtMost(padCaches.size)
        for (i in 0 until padCount) {
            val zone = padZones[i]
            val cx = zone.centerX * canvasW
            val cy = zone.centerY * canvasH
            val rx = zone.radiusX * canvasW
            val ry = zone.radiusY * canvasH
            val radius = min(rx, ry) * 0.98f

            val shellBrush = Brush.linearGradient(
                colors = listOf(theme.shellPrimary, theme.shellDark),
                start = Offset(cx - radius, cy - radius),
                end = Offset(cx + radius, cy + radius)
            )

            val headBrush = when (zone.padId.category) {
                PadCategory.SNARE -> Brush.radialGradient(
                    colors = listOf(theme.snareHeadLight, theme.snareHeadEdge),
                    center = Offset(cx - radius * 0.12f, cy - radius * 0.12f),
                    radius = radius * 1.1f
                )
                PadCategory.TOM -> Brush.radialGradient(
                    colors = listOf(theme.tomHeadCenter, theme.tomHeadEdge),
                    center = Offset(cx - radius * 0.16f, cy - radius * 0.16f),
                    radius = radius * 1.15f
                )
                PadCategory.KICK -> Brush.linearGradient(
                    colors = listOf(Color(0xFF64748B), Color(0xFF334155), Color(0xFF1E293B)),
                    start = Offset(cx, cy - ry * 0.3f),
                    end = Offset(cx, cy + ry)
                )
                PadCategory.CYMBAL -> Brush.radialGradient(
                    colors = listOf(theme.cymbalBright.copy(alpha = 0.2f), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = radius
                )
            }

            val cymbalSweepBrush = if (zone.padId.category == PadCategory.CYMBAL) {
                Brush.sweepGradient(
                    colors = listOf(
                        theme.cymbalMid,
                        theme.cymbalBright,
                        theme.cymbalMid,
                        theme.cymbalDark,
                        theme.cymbalMid,
                        theme.cymbalBright,
                        theme.cymbalMid,
                        theme.cymbalDark,
                        theme.cymbalMid
                    ),
                    center = Offset(cx, cy)
                )
            } else {
                null
            }

            val cymbalBellBrush = if (zone.padId.category == PadCategory.CYMBAL) {
                Brush.radialGradient(
                    colors = listOf(theme.cymbalBright, theme.cymbalMid, theme.cymbalDark),
                    center = Offset(cx - radius * 0.06f, cy - radius * 0.06f),
                    radius = radius * 0.26f
                )
            } else {
                null
            }

            val lugCount = when (zone.padId) {
                DrumPadId.SNARE -> 10
                DrumPadId.TOM_FLOOR -> 8
                DrumPadId.TOM_HIGH, DrumPadId.TOM_MID -> 6
                else -> 0
            }
            val lugRadius = radius * 0.93f
            val lugs = Array(lugCount) { lugIdx ->
                val angle = (2.0 * PI * lugIdx / lugCount.toDouble()) - (PI / 2.0)
                Offset(
                    x = cx + (cos(angle) * lugRadius).toFloat(),
                    y = cy + (sin(angle) * lugRadius).toFloat()
                )
            }

            val floorLegs = if (zone.padId == DrumPadId.TOM_FLOOR) {
                val legStartAngle = listOf(0.55, 2.55, 4.71)
                Array(3) { legIdx ->
                    val ang = legStartAngle[legIdx]
                    Offset(
                        x = cx + (cos(ang) * radius * 1.18f).toFloat(),
                        y = cy + (sin(ang) * radius * 1.18f).toFloat()
                    )
                }
            } else {
                emptyArray()
            }

            val badgeCenterY = if (zone.isCircular) cy + radius * 0.63f else cy + ry * 0.72f
            val badgeHalfW = if (zone.isCircular) (radius * 0.46f).coerceIn(36f, 68f) else (rx * 0.36f).coerceIn(38f, 66f)
            val badgeHalfH = if (zone.isCircular) (radius * 0.16f).coerceIn(11f, 18f) else (ry * 0.17f).coerceIn(11f, 17f)

            padCaches[i] = PadVisualGeometry(
                padId = zone.padId,
                cx = cx,
                cy = cy,
                rx = rx,
                ry = ry,
                radius = radius,
                shellBrush = shellBrush,
                headBrush = headBrush,
                cymbalSweepBrush = cymbalSweepBrush,
                cymbalBellBrush = cymbalBellBrush,
                lugOffsets = lugs,
                floorLegEnds = floorLegs,
                badgeCenterY = badgeCenterY,
                badgeHalfW = badgeHalfW,
                badgeHalfH = badgeHalfH
            )
        }
    }
}

/**
 * Multi-touch 10-pad Realistic Studio Drum Stage Canvas (`MultiTouchDrumStageCanvas`).
 *
 * Critical Constraints Enforced:
 * - Immediate pointer-down triggering on `PointerEventPass.Initial` (`changedToDownIgnoreConsumed()`).
 * - Multi-pointer tracking (up to 8 simultaneous fingers) + slide-to-roll detection across pad boundaries.
 * - Zero object allocations inside the `Canvas` `onDraw` block (all Brushes, Strokes, Paints, and Lugs cached in [DrumStageRenderCache]).
 */
@Composable
fun MultiTouchDrumStageCanvas(
    padZones: List<PadHitZone>,
    selectedKit: DrumKitId = DrumKitId.ACOUSTIC_STUDIO,
    isLeftHanded: Boolean,
    activeHits: List<PadHitVisualState>,
    isChokeFlashActive: Boolean,
    onPointerTouchHit: (xNorm: Float, yNorm: Float, touchPressure: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentIsLeftHanded by rememberUpdatedState(isLeftHanded)
    val currentOnPointerTouchHit by rememberUpdatedState(onPointerTouchHit)

    val renderCache = remember { DrumStageRenderCache() }

    // Pre-allocated native Paint for crisp drum labels (zero allocations in onDraw)
    val labelTextPaint = remember {
        AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            color = StudioColors.TextPrimaryDark.toArgb()
            textAlign = AndroidPaint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = 22f
        }
    }

    // Pre-allocated Compose Strokes & Colors outside Canvas onDraw
    val chromeRimStroke = remember { Stroke(width = 6f) }
    val outerShellStroke = remember { Stroke(width = 4f) }
    val thinLatheStroke = remember { Stroke(width = 1.4f) }
    val activeGlowStroke = remember { Stroke(width = 8f) }
    val rippleStroke = remember { Stroke(width = 5f) }
    val rugBorderStroke = remember { Stroke(width = 3f) }
    val badgeCornerRadius = remember { CornerRadius(10f, 10f) }
    val pedalCornerRadius = remember { CornerRadius(16f, 16f) }
    val innerPedalCornerRadius = remember { CornerRadius(12f, 12f) }
    val beaterCornerRadius = remember { CornerRadius(6f, 6f) }
    val pedalBoardStroke = remember { Stroke(width = 2f) }

    val chromeRimColor = remember { Color(0xFFE2E8F0) }
    val chromeShadowColor = remember { Color(0xFF475569) }
    val lugBodyColor = remember { Color(0xFFCBD5E1) }
    val standTubeColor = remember { Color(0xFF334155) }
    val standHighlightColor = remember { Color(0xFF64748B) }
    val shadowColor = remember { Color(0x99030408) }
    val badgeBgColor = remember { Color(0xC80B0E17) }
    val feltWasherColor = remember { Color(0xFF111827) }
    val whiteFeltBeaterColor = remember { Color(0xFFF1F5F9) }
    val glossHighlightColor = remember { Color(0x2EFFFFFF) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(StudioColors.StageBackgroundDark)
            .pointerInput(Unit) {
                val pointerActivePad = HashMap<Long, DrumPadId>(16)

                awaitEachGesture {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val stageWidth = size.width.toFloat().coerceAtLeast(1f)
                        val stageHeight = size.height.toFloat().coerceAtLeast(1f)

                        val changes = event.changes
                        var anyPressed = false

                        for (i in 0 until changes.size) {
                            val change = changes[i]
                            val pointerKey = change.id.value
                            if (change.pressed) {
                                anyPressed = true
                                val xNorm = (change.position.x / stageWidth).coerceIn(0f, 1f)
                                val yNorm = (change.position.y / stageHeight).coerceIn(0f, 1f)

                                if (change.changedToDownIgnoreConsumed()) {
                                    val resolved = PadHitResolver.resolveHit(
                                        xNorm = xNorm,
                                        yNorm = yNorm,
                                        isLeftHanded = currentIsLeftHanded,
                                        touchPressure = change.pressure
                                    )
                                    if (resolved != null) {
                                        pointerActivePad[pointerKey] = resolved.padId
                                        currentOnPointerTouchHit(xNorm, yNorm, change.pressure)
                                    } else {
                                        pointerActivePad.remove(pointerKey)
                                    }
                                } else {
                                    val resolved = PadHitResolver.resolveHit(
                                        xNorm = xNorm,
                                        yNorm = yNorm,
                                        isLeftHanded = currentIsLeftHanded,
                                        touchPressure = change.pressure
                                    )
                                    val previousPad = pointerActivePad[pointerKey]
                                    if (resolved != null && resolved.padId != previousPad) {
                                        pointerActivePad[pointerKey] = resolved.padId
                                        currentOnPointerTouchHit(xNorm, yNorm, change.pressure)
                                    } else if (resolved == null) {
                                        pointerActivePad.remove(pointerKey)
                                    }
                                }
                            } else {
                                pointerActivePad.remove(pointerKey)
                            }
                        }

                        if (!anyPressed) {
                            pointerActivePad.clear()
                            break
                        }
                    }
                }
            }
    ) {
        val canvasW = size.width
        val canvasH = size.height
        renderCache.ensureBuilt(canvasW, canvasH, padZones, selectedKit)

        // 1. Studio Floor Spotlight & Woven Drum Rug
        drawRect(brush = renderCache.stageSpotlightBrush, topLeft = Offset.Zero, size = size)
        drawOval(
            color = Color(0xFF141827),
            topLeft = Offset(
                renderCache.rugCenter.x - renderCache.rugRx,
                renderCache.rugCenter.y - renderCache.rugRy
            ),
            size = Size(renderCache.rugRx * 2f, renderCache.rugRy * 2f)
        )
        drawOval(
            color = Color(0xFF282E46),
            topLeft = Offset(
                renderCache.rugCenter.x - renderCache.rugRx,
                renderCache.rugCenter.y - renderCache.rugRy
            ),
            size = Size(renderCache.rugRx * 2f, renderCache.rugRy * 2f),
            style = rugBorderStroke
        )

        // 2. Chrome Drum Rack & Hardware Stand Tubes connecting the kit
        drawLine(
            color = standTubeColor,
            start = renderCache.rackBarStart,
            end = renderCache.rackBarEnd,
            strokeWidth = 10f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = standHighlightColor,
            start = Offset(renderCache.rackBarStart.x, renderCache.rackBarStart.y - 2f),
            end = Offset(renderCache.rackBarEnd.x, renderCache.rackBarEnd.y - 2f),
            strokeWidth = 3f,
            cap = StrokeCap.Round
        )

        for (i in 0 until renderCache.padCount) {
            val geom = renderCache.padCaches[i] ?: continue
            if (geom.padId.category != PadCategory.KICK) {
                val targetAnchor = if (geom.cy < canvasH * 0.35f) {
                    Offset(geom.cx, renderCache.rackBarStart.y)
                } else {
                    renderCache.rackAnchorCenter
                }
                drawLine(
                    color = standTubeColor,
                    start = targetAnchor,
                    end = Offset(geom.cx, geom.cy),
                    strokeWidth = 7f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = standHighlightColor.copy(alpha = 0.65f),
                    start = targetAnchor,
                    end = Offset(geom.cx, geom.cy),
                    strokeWidth = 2f,
                    cap = StrokeCap.Round
                )
            }
        }

        // 3. Draw Each Instrument (Kick Drums first so Toms/Snare sit layered naturally)
        for (pass in 0..1) {
            val drawKicksPass = (pass == 0)
            for (i in 0 until renderCache.padCount) {
                val geom = renderCache.padCaches[i] ?: continue
                val isKick = geom.padId.category == PadCategory.KICK
                if (isKick != drawKicksPass) continue

                var latestHit: PadHitVisualState? = null
                for (j in 0 until activeHits.size) {
                    val hit = activeHits[j]
                    if (hit.padId == geom.padId) {
                        latestHit = hit
                    }
                }

                val isHitActive = latestHit != null && latestHit.progress < 1f
                val impulseFactor = if (latestHit != null) (1f - latestHit.progress).coerceIn(0f, 1f) else 0f
                val scale = 1f - 0.045f * impulseFactor
                val categoryColor = StudioTokens.categoryRingColor(geom.padId.category)

                when (geom.padId.category) {
                    PadCategory.CYMBAL -> drawRealisticCymbal(
                        geom = geom,
                        theme = renderCache.kitTheme,
                        scale = scale,
                        impulseFactor = impulseFactor,
                        isHitActive = isHitActive,
                        isChokeFlashActive = isChokeFlashActive && geom.padId == DrumPadId.HI_HAT_OPEN,
                        shadowColor = shadowColor,
                        feltWasherColor = feltWasherColor,
                        chromeRimColor = chromeRimColor,
                        chromeShadowColor = chromeShadowColor,
                        thinLatheStroke = thinLatheStroke,
                        outerShellStroke = outerShellStroke,
                        activeGlowStroke = activeGlowStroke
                    )

                    PadCategory.SNARE -> drawRealisticSnare(
                        geom = geom,
                        theme = renderCache.kitTheme,
                        scale = scale,
                        impulseFactor = impulseFactor,
                        isHitActive = isHitActive,
                        shadowColor = shadowColor,
                        chromeRimColor = chromeRimColor,
                        chromeShadowColor = chromeShadowColor,
                        lugBodyColor = lugBodyColor,
                        chromeRimStroke = chromeRimStroke,
                        outerShellStroke = outerShellStroke,
                        activeGlowStroke = activeGlowStroke,
                        glossHighlightColor = glossHighlightColor
                    )

                    PadCategory.TOM -> drawRealisticTom(
                        geom = geom,
                        theme = renderCache.kitTheme,
                        scale = scale,
                        impulseFactor = impulseFactor,
                        isHitActive = isHitActive,
                        shadowColor = shadowColor,
                        chromeRimColor = chromeRimColor,
                        chromeShadowColor = chromeShadowColor,
                        lugBodyColor = lugBodyColor,
                        standTubeColor = standTubeColor,
                        chromeRimStroke = chromeRimStroke,
                        outerShellStroke = outerShellStroke,
                        activeGlowStroke = activeGlowStroke,
                        glossHighlightColor = glossHighlightColor
                    )

                    PadCategory.KICK -> drawRealisticKickDrumAndPedal(
                        geom = geom,
                        theme = renderCache.kitTheme,
                        impulseFactor = impulseFactor,
                        isHitActive = isHitActive,
                        shadowColor = shadowColor,
                        chromeRimColor = chromeRimColor,
                        chromeShadowColor = chromeShadowColor,
                        whiteFeltBeaterColor = whiteFeltBeaterColor,
                        pedalCornerRadius = pedalCornerRadius,
                        innerPedalCornerRadius = innerPedalCornerRadius,
                        beaterCornerRadius = beaterCornerRadius,
                        pedalBoardStroke = pedalBoardStroke,
                        outerShellStroke = outerShellStroke,
                        activeGlowStroke = activeGlowStroke
                    )
                }

                // Velocity ripple right at exact finger hit coordinates
                if (latestHit != null && latestHit.progress < 1f) {
                    val rippleAlpha = ((1f - latestHit.progress) * latestHit.velocity).coerceIn(0f, 1f)
                    val maxRippleRadius = min(geom.rx, geom.ry) * (0.28f + 0.62f * latestHit.progress)
                    val touchCenter = Offset(
                        x = latestHit.touchXNorm * canvasW,
                        y = latestHit.touchYNorm * canvasH
                    )
                    drawCircle(
                        color = StudioColors.ElectricAmber.copy(alpha = rippleAlpha),
                        radius = maxRippleRadius,
                        center = touchCenter,
                        style = rippleStroke
                    )
                }

                // Compact translucent label pill cleanly anchored near lower edge (never blocking drum center)
                drawRoundRect(
                    color = badgeBgColor,
                    topLeft = Offset(geom.cx - geom.badgeHalfW, geom.badgeCenterY - geom.badgeHalfH),
                    size = Size(geom.badgeHalfW * 2f, geom.badgeHalfH * 2f),
                    cornerRadius = badgeCornerRadius
                )
                drawRoundRect(
                    color = if (isHitActive) StudioColors.ElectricAmber else categoryColor.copy(alpha = 0.55f),
                    topLeft = Offset(geom.cx - geom.badgeHalfW, geom.badgeCenterY - geom.badgeHalfH),
                    size = Size(geom.badgeHalfW * 2f, geom.badgeHalfH * 2f),
                    cornerRadius = badgeCornerRadius,
                    style = thinLatheStroke
                )

                labelTextPaint.textSize = (geom.badgeHalfH * 1.15f).coerceIn(16f, 24f)
                labelTextPaint.color = if (isHitActive) {
                    StudioColors.ElectricAmber.toArgb()
                } else {
                    StudioColors.TextPrimaryDark.toArgb()
                }
                val badgeText = latestHit?.takeIf { isHitActive }?.zoneLabel ?: geom.padId.shortLabel
                drawContext.canvas.nativeCanvas.drawText(
                    badgeText,
                    geom.cx,
                    geom.badgeCenterY + labelTextPaint.textSize * 0.36f,
                    labelTextPaint
                )
            }
        }
    }
}

private fun DrawScope.drawRealisticCymbal(
    geom: PadVisualGeometry,
    theme: KitFinishTheme,
    scale: Float,
    impulseFactor: Float,
    isHitActive: Boolean,
    isChokeFlashActive: Boolean,
    shadowColor: Color,
    feltWasherColor: Color,
    chromeRimColor: Color,
    chromeShadowColor: Color,
    thinLatheStroke: Stroke,
    outerShellStroke: Stroke,
    activeGlowStroke: Stroke
) {
    val r = geom.radius * scale
    val center = Offset(geom.cx, geom.cy)

    // Drop shadow beneath cymbal
    drawCircle(color = shadowColor, radius = r * 1.02f, center = Offset(geom.cx + 2f, geom.cy + 8f))

    // Open Hi-Hat bottom cymbal peeking edge so it visually looks open/apart
    if (geom.padId == DrumPadId.HI_HAT_OPEN) {
        drawCircle(
            color = theme.cymbalDark,
            radius = r * 1.02f,
            center = Offset(geom.cx + 4f, geom.cy + 7f)
        )
        drawCircle(
            color = theme.cymbalMid.copy(alpha = 0.7f),
            radius = r * 1.02f,
            center = Offset(geom.cx + 4f, geom.cy + 7f),
            style = thinLatheStroke
        )
    }

    // Cast Bronze / Brass SweepGradient Cymbal Body
    val sweepBrush = geom.cymbalSweepBrush
    if (sweepBrush != null) {
        drawCircle(brush = sweepBrush, radius = r, center = center)
    } else {
        drawCircle(color = theme.cymbalMid, radius = r, center = center)
    }

    // Concentric spun lathing grooves across bow
    for (ringIdx in 1..6) {
        val ratio = 0.28f + ringIdx * 0.11f
        drawCircle(
            color = theme.cymbalDark.copy(alpha = 0.34f),
            radius = r * ratio,
            center = center,
            style = thinLatheStroke
        )
    }

    // Outer cymbal rim & active hit flash shimmer
    drawCircle(
        color = if (isHitActive) StudioColors.ElectricAmber else theme.cymbalBright.copy(alpha = 0.7f),
        radius = r,
        center = center,
        style = if (isHitActive) activeGlowStroke else outerShellStroke
    )
    if (isHitActive) {
        drawCircle(
            color = StudioColors.ElectricAmber.copy(alpha = 0.18f * impulseFactor),
            radius = r,
            center = center
        )
    }

    // Machined Raised Cymbal Bell
    val bellBrush = geom.cymbalBellBrush
    if (bellBrush != null) {
        drawCircle(brush = bellBrush, radius = r * 0.24f, center = center)
    } else {
        drawCircle(color = theme.cymbalMid, radius = r * 0.24f, center = center)
    }
    drawCircle(
        color = theme.cymbalDark.copy(alpha = 0.55f),
        radius = r * 0.24f,
        center = center,
        style = thinLatheStroke
    )

    // Black Felt Washer & Chrome Wingnut/Clutch at center post
    drawCircle(color = feltWasherColor, radius = r * 0.085f, center = center)
    drawCircle(color = chromeShadowColor, radius = r * 0.055f, center = center)
    drawCircle(color = chromeRimColor, radius = r * 0.036f, center = center)

    // Hi-Hat Choke Dampener Flash Ring
    if (isChokeFlashActive) {
        drawCircle(
            color = StudioColors.ElectricAmber,
            radius = r * 0.86f,
            center = center,
            style = activeGlowStroke
        )
    }
}

private fun DrawScope.drawRealisticSnare(
    geom: PadVisualGeometry,
    theme: KitFinishTheme,
    scale: Float,
    impulseFactor: Float,
    isHitActive: Boolean,
    shadowColor: Color,
    chromeRimColor: Color,
    chromeShadowColor: Color,
    lugBodyColor: Color,
    chromeRimStroke: Stroke,
    outerShellStroke: Stroke,
    activeGlowStroke: Stroke,
    glossHighlightColor: Color
) {
    val r = geom.radius * scale
    val center = Offset(geom.cx, geom.cy)

    // Drop shadow + 3D cylindrical shell skirt offset downward
    drawCircle(color = shadowColor, radius = r * 1.03f, center = Offset(geom.cx, geom.cy + 10f))
    drawCircle(brush = geom.shellBrush, radius = r * 1.02f, center = Offset(geom.cx, geom.cy + 6f))

    // Snare drumhead (Coated Cream or Carbon Mesh)
    drawCircle(brush = geom.headBrush, radius = r * 0.90f, center = center)

    // Subtle Snare Wire shadows running across translucent center (acoustic/vintage) or mesh dot (electronic)
    if (!theme.isElectronicMesh) {
        val wireColor = Color(0x161E293B)
        for (w in -3..3) {
            val offsetX = w * (r * 0.025f)
            drawLine(
                color = wireColor,
                start = Offset(geom.cx + offsetX, geom.cy - r * 0.82f),
                end = Offset(geom.cx + offsetX, geom.cy + r * 0.82f),
                strokeWidth = 2f
            )
        }
        // Subtle center batter dot / stick impact ring + off-center edge zone guide ring
        drawCircle(
            color = Color(0x18334155),
            radius = r * 0.24f,
            center = center
        )
        drawCircle(
            color = Color(0x14334155),
            radius = r * 0.68f,
            center = center,
            style = outerShellStroke
        )
    } else {
        drawCircle(
            color = glossHighlightColor,
            radius = r * 0.55f,
            center = center,
            style = outerShellStroke
        )
    }

    // Hit surface glow
    if (isHitActive) {
        drawCircle(
            color = StudioColors.ElectricAmber.copy(alpha = 0.22f * impulseFactor),
            radius = r * 0.90f,
            center = center
        )
    }

    // Polished Chrome Triple-Flange Hoop
    drawCircle(color = chromeShadowColor, radius = r * 0.96f, center = center, style = chromeRimStroke)
    drawCircle(
        color = if (isHitActive) StudioColors.ElectricAmber else chromeRimColor,
        radius = r * 0.92f,
        center = center,
        style = if (isHitActive) activeGlowStroke else outerShellStroke
    )

    // 10 Chrome Tension Lugs around perimeter
    for (lug in geom.lugOffsets) {
        val lx = geom.cx + (lug.x - geom.cx) * scale
        val ly = geom.cy + (lug.y - geom.cy) * scale
        drawCircle(color = chromeShadowColor, radius = (r * 0.068f).coerceAtLeast(4f), center = Offset(lx, ly))
        drawCircle(color = lugBodyColor, radius = (r * 0.044f).coerceAtLeast(2.5f), center = Offset(lx, ly))
    }
}

private fun DrawScope.drawRealisticTom(
    geom: PadVisualGeometry,
    theme: KitFinishTheme,
    scale: Float,
    impulseFactor: Float,
    isHitActive: Boolean,
    shadowColor: Color,
    chromeRimColor: Color,
    chromeShadowColor: Color,
    lugBodyColor: Color,
    standTubeColor: Color,
    chromeRimStroke: Stroke,
    outerShellStroke: Stroke,
    activeGlowStroke: Stroke,
    glossHighlightColor: Color
) {
    val r = geom.radius * scale
    val center = Offset(geom.cx, geom.cy)

    // Chrome Floor Tom Legs
    for (legEnd in geom.floorLegEnds) {
        drawLine(
            color = standTubeColor,
            start = center,
            end = legEnd,
            strokeWidth = 7f,
            cap = StrokeCap.Round
        )
        drawCircle(color = lugBodyColor, radius = 5f, center = legEnd)
    }

    // Drop shadow + 3D cylindrical lacquer shell rim
    drawCircle(color = shadowColor, radius = r * 1.03f, center = Offset(geom.cx, geom.cy + 9f))
    drawCircle(brush = geom.shellBrush, radius = r * 1.03f, center = Offset(geom.cx, geom.cy + 5f))

    // Glossy Mylar Tom Head
    drawCircle(brush = geom.headBrush, radius = r * 0.90f, center = center)

    // Classic outer dampening pinstripe ring
    drawCircle(
        color = theme.shellPrimary.copy(alpha = 0.55f),
        radius = r * 0.74f,
        center = center,
        style = outerShellStroke
    )

    // Glossy studio overhead reflection arc
    drawArc(
        color = glossHighlightColor,
        startAngle = 205f,
        sweepAngle = 78f,
        useCenter = false,
        topLeft = Offset(geom.cx - r * 0.76f, geom.cy - r * 0.76f),
        size = Size(r * 1.52f, r * 1.52f),
        style = chromeRimStroke
    )

    if (isHitActive) {
        drawCircle(
            color = StudioColors.ElectricAmber.copy(alpha = 0.20f * impulseFactor),
            radius = r * 0.90f,
            center = center
        )
    }

    // Chrome Counter-Hoop
    drawCircle(color = chromeShadowColor, radius = r * 0.96f, center = center, style = chromeRimStroke)
    drawCircle(
        color = if (isHitActive) StudioColors.ElectricAmber else chromeRimColor,
        radius = r * 0.92f,
        center = center,
        style = if (isHitActive) activeGlowStroke else outerShellStroke
    )

    // Chrome Tension Lugs
    for (lug in geom.lugOffsets) {
        val lx = geom.cx + (lug.x - geom.cx) * scale
        val ly = geom.cy + (lug.y - geom.cy) * scale
        drawCircle(color = chromeShadowColor, radius = (r * 0.068f).coerceAtLeast(4f), center = Offset(lx, ly))
        drawCircle(color = lugBodyColor, radius = (r * 0.044f).coerceAtLeast(2.5f), center = Offset(lx, ly))
    }
}

private fun DrawScope.drawRealisticKickDrumAndPedal(
    geom: PadVisualGeometry,
    theme: KitFinishTheme,
    impulseFactor: Float,
    isHitActive: Boolean,
    shadowColor: Color,
    chromeRimColor: Color,
    chromeShadowColor: Color,
    whiteFeltBeaterColor: Color,
    pedalCornerRadius: CornerRadius,
    innerPedalCornerRadius: CornerRadius,
    beaterCornerRadius: CornerRadius,
    pedalBoardStroke: Stroke,
    outerShellStroke: Stroke,
    activeGlowStroke: Stroke
) {
    val halfW = geom.rx
    val halfH = geom.ry

    // 1. Upper Arched 22" Bass Drum Shell & Hoop Profile
    val drumWidth = halfW * 1.72f
    val drumHeight = halfH * 1.10f
    val drumTopLeft = Offset(geom.cx - drumWidth * 0.5f, geom.cy - halfH * 0.98f)

    // Shell shadow & lacquer bass drum body
    drawOval(
        color = shadowColor,
        topLeft = Offset(drumTopLeft.x, drumTopLeft.y + 8f),
        size = Size(drumWidth, drumHeight)
    )
    drawOval(
        brush = geom.shellBrush,
        topLeft = drumTopLeft,
        size = Size(drumWidth, drumHeight)
    )
    // Dark Bass Drum Batter Head inside the hoop
    drawOval(
        color = Color(0xFF0E121D),
        topLeft = Offset(drumTopLeft.x + drumWidth * 0.06f, drumTopLeft.y + drumHeight * 0.12f),
        size = Size(drumWidth * 0.88f, drumHeight * 0.78f)
    )
    // Bass Hoop Rim (flashes Electric Amber / Kick Violet on strike)
    drawOval(
        color = if (isHitActive) StudioColors.ElectricAmber else theme.shellPrimary,
        topLeft = drumTopLeft,
        size = Size(drumWidth, drumHeight),
        style = if (isHitActive) activeGlowStroke else outerShellStroke
    )

    // 2. Heavy-Duty Cast Metal Kick Pedal Chassis + Footboard
    val pedalW = (halfW * 0.68f).coerceAtLeast(52f)
    val pedalTop = geom.cy - halfH * 0.25f
    val pedalH = halfH * 1.18f
    val depressOffset = 4f * impulseFactor

    // Baseplate shadow
    drawRoundRect(
        color = shadowColor,
        topLeft = Offset(geom.cx - pedalW * 0.58f, pedalTop + 6f),
        size = Size(pedalW * 1.16f, pedalH),
        cornerRadius = pedalCornerRadius
    )
    // Baseplate
    drawRoundRect(
        color = Color(0xFF151924),
        topLeft = Offset(geom.cx - pedalW * 0.56f, pedalTop),
        size = Size(pedalW * 1.12f, pedalH),
        cornerRadius = pedalCornerRadius
    )
    drawRoundRect(
        color = if (isHitActive) StudioColors.ElectricAmber else chromeShadowColor,
        topLeft = Offset(geom.cx - pedalW * 0.56f, pedalTop),
        size = Size(pedalW * 1.12f, pedalH),
        cornerRadius = pedalCornerRadius,
        style = outerShellStroke
    )

    // Twin Chrome Upright Spring Pillars & Horizontal Axle
    val pillarY = pedalTop + pedalH * 0.14f
    val pillarSpan = pedalW * 0.44f
    drawLine(
        color = chromeRimColor,
        start = Offset(geom.cx - pillarSpan, pillarY),
        end = Offset(geom.cx + pillarSpan, pillarY),
        strokeWidth = 5f,
        cap = StrokeCap.Round
    )
    drawCircle(color = chromeShadowColor, radius = 6f, center = Offset(geom.cx - pillarSpan, pillarY))
    drawCircle(color = chromeShadowColor, radius = 6f, center = Offset(geom.cx + pillarSpan, pillarY))

    // Brushed Aluminum Footboard
    val boardW = pedalW * 0.74f
    val boardTop = pedalTop + pedalH * 0.22f + depressOffset
    val boardH = pedalH * 0.70f
    drawRoundRect(
        brush = geom.headBrush,
        topLeft = Offset(geom.cx - boardW * 0.5f, boardTop),
        size = Size(boardW, boardH),
        cornerRadius = innerPedalCornerRadius
    )
    drawRoundRect(
        color = if (isHitActive) StudioColors.ElectricAmber else chromeRimColor.copy(alpha = 0.65f),
        topLeft = Offset(geom.cx - boardW * 0.5f, boardTop),
        size = Size(boardW, boardH),
        cornerRadius = innerPedalCornerRadius,
        style = pedalBoardStroke
    )

    // Raised Footboard Grip Tread Bars
    for (t in 0..3) {
        val barY = boardTop + boardH * (0.18f + t * 0.14f)
        drawLine(
            color = chromeRimColor.copy(alpha = 0.45f),
            start = Offset(geom.cx - boardW * 0.30f, barY),
            end = Offset(geom.cx + boardW * 0.30f, barY),
            strokeWidth = 3f,
            cap = StrokeCap.Round
        )
    }

    // 3. Chrome Beater Shaft + White Felt Beater Head striking forward on Kick
    val beaterHeadY = drumTopLeft.y + drumHeight * (0.58f - 0.18f * impulseFactor)
    drawLine(
        color = chromeRimColor,
        start = Offset(geom.cx, pillarY),
        end = Offset(geom.cx, beaterHeadY),
        strokeWidth = 4.5f,
        cap = StrokeCap.Round
    )
    if (isHitActive) {
        drawCircle(
            color = StudioColors.ElectricAmber.copy(alpha = 0.55f * impulseFactor),
            radius = (pedalW * 0.24f) + 12f * (1f - impulseFactor),
            center = Offset(geom.cx, beaterHeadY)
        )
    }
    drawRoundRect(
        color = whiteFeltBeaterColor,
        topLeft = Offset(geom.cx - pedalW * 0.14f, beaterHeadY - 8f),
        size = Size(pedalW * 0.28f, 15f),
        cornerRadius = beaterCornerRadius
    )
}
