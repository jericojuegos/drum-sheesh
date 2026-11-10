package com.drumsheesh.ui.components

import android.graphics.Paint as AndroidPaint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
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
import kotlin.math.min

/**
 * Immutable snapshot of a single pad hit impulse for zero-allocation Canvas rendering.
 */
data class PadHitVisualState(
    val padId: DrumPadId,
    val touchXNorm: Float,
    val touchYNorm: Float,
    val velocity: Float,
    val progress: Float // 0.0f (fresh hit) -> 1.0f (fully decayed)
)

/**
 * Top 52dp Studio Transport HUD Bar (`StudioTopBar`) matching `docs/foundation/design-system.md`.
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
            .height(StudioTokens.TopHudBarHeight)
            .background(StudioColors.SurfaceDark.copy(alpha = 0.94f))
            .border(width = 1.dp, color = StudioColors.BorderDark)
            .padding(horizontal = StudioTokens.SpaceCompact),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Brand Pill + Kit Selector Dropdown + Left/Right Handedness Flip
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(StudioTokens.SpaceSmall)
        ) {
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
                    fontSize = 12.sp,
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
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "KIT: ${selectedKit.displayName.uppercase()}",
                        color = StudioColors.TextPrimaryDark,
                        fontSize = 12.sp,
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
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (isLeftHanded) "L-HAND" else "R-HAND",
                    color = if (isLeftHanded) StudioColors.ElectricAmber else StudioColors.TextPrimaryDark,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Center: Monospace BPM Counter with -/+ controls
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(StudioTokens.RadiusSm))
                    .background(StudioColors.SurfaceElevatedDark)
                    .clickable { onAdjustBpm(-5) }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
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
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "$bpm BPM",
                    color = StudioColors.ElectricAmber,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(StudioTokens.RadiusSm))
                    .background(StudioColors.SurfaceElevatedDark)
                    .clickable { onAdjustBpm(5) }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "+5",
                    color = StudioColors.TextSecondaryDark,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Right: AI Groove Trigger, Loop Drawer Trigger, Live Velocity Meter & Choke Status Badge
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(StudioTokens.SpaceSmall)
        ) {
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
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                val velText = if (lastVelocityPercent != null) {
                    "VEL ${lastVelocityPercent}%"
                } else {
                    "8-TOUCH READY"
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
}

/**
 * Multi-touch 10-pad Drum Stage Canvas (`MultiTouchDrumStageCanvas`).
 *
 * Critical Constraints Enforced:
 * - Immediate pointer-down triggering on `PointerEventPass.Initial` (`changedToDownIgnoreConsumed()`).
 * - Multi-pointer tracking (up to 8 simultaneous fingers) + slide-to-roll detection across pad boundaries.
 * - Zero object allocations (`Path`, `Paint`, `Brush`, or collections) inside the `Canvas` `onDraw` block.
 */
@Composable
fun MultiTouchDrumStageCanvas(
    padZones: List<PadHitZone>,
    isLeftHanded: Boolean,
    activeHits: List<PadHitVisualState>,
    isChokeFlashActive: Boolean,
    onPointerTouchHit: (xNorm: Float, yNorm: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentIsLeftHanded by rememberUpdatedState(isLeftHanded)
    val currentOnPointerTouchHit by rememberUpdatedState(onPointerTouchHit)

    // Pre-allocate native text paints outside Canvas onDraw to guarantee zero per-frame allocations
    val primaryLabelPaint = remember {
        AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            color = StudioColors.TextPrimaryDark.toArgb()
            textAlign = AndroidPaint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = 28f
        }
    }
    val subLabelPaint = remember {
        AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            color = StudioColors.TextSecondaryDark.toArgb()
            textAlign = AndroidPaint.Align.CENTER
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            textSize = 20f
        }
    }

    // Pre-allocate Stroke instances outside Canvas onDraw
    val outerRingStroke = remember { Stroke(width = 5f) }
    val activeRingStroke = remember { Stroke(width = 9f) }
    val innerRingStroke = remember { Stroke(width = 2.5f) }
    val rippleStroke = remember { Stroke(width = 6f) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(StudioColors.StageBackgroundDark)
            .pointerInput(Unit) {
                // Pre-allocated map keyed by pointer ID to track active pad for slide-to-roll
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
                                        isLeftHanded = currentIsLeftHanded
                                    )
                                    if (resolved != null) {
                                        pointerActivePad[pointerKey] = resolved.padId
                                        currentOnPointerTouchHit(xNorm, yNorm)
                                    } else {
                                        pointerActivePad.remove(pointerKey)
                                    }
                                } else {
                                    // Slide-to-roll detection: trigger immediately when a pressed finger enters a new pad
                                    val resolved = PadHitResolver.resolveHit(
                                        xNorm = xNorm,
                                        yNorm = yNorm,
                                        isLeftHanded = currentIsLeftHanded
                                    )
                                    val previousPad = pointerActivePad[pointerKey]
                                    if (resolved != null && resolved.padId != previousPad) {
                                        pointerActivePad[pointerKey] = resolved.padId
                                        currentOnPointerTouchHit(xNorm, yNorm)
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

        // Draw each of the 10 drum pads with zero object allocations inside the loop
        for (i in 0 until padZones.size) {
            val zone = padZones[i]
            val padId = zone.padId
            val cx = zone.centerX * canvasW
            val cy = zone.centerY * canvasH
            val rx = zone.radiusX * canvasW
            val ry = zone.radiusY * canvasH

            // Check if this pad has an active hit impulse
            var latestHit: PadHitVisualState? = null
            for (j in 0 until activeHits.size) {
                val hit = activeHits[j]
                if (hit.padId == padId) {
                    latestHit = hit
                }
            }

            val isHitActive = latestHit != null && latestHit.progress < 1f
            val impulseFactor = if (latestHit != null) (1f - latestHit.progress).coerceIn(0f, 1f) else 0f
            val scale = 1f - 0.045f * impulseFactor

            val categoryColor: Color = StudioTokens.categoryRingColor(padId.category)
            val shellFillColor: Color = if (padId.category == PadCategory.CYMBAL) {
                StudioColors.CymbalSheen
            } else {
                StudioColors.SurfaceDark
            }

            if (zone.isCircular) {
                val radius = min(rx, ry) * scale
                // Outer shell fill
                drawCircle(
                    color = shellFillColor,
                    radius = radius,
                    center = Offset(cx, cy)
                )
                // Inner drum head / cymbal bell
                drawCircle(
                    color = StudioColors.SurfaceInnerDark,
                    radius = radius * 0.76f,
                    center = Offset(cx, cy)
                )
                // Concentric target ring
                drawCircle(
                    color = categoryColor.copy(alpha = 0.28f),
                    radius = radius * 0.42f,
                    center = Offset(cx, cy),
                    style = innerRingStroke
                )
                // Outer category border ring (jumps to 100% Electric Amber on hit)
                drawCircle(
                    color = if (isHitActive) {
                        StudioColors.ElectricAmber
                    } else {
                        categoryColor.copy(alpha = 0.62f)
                    },
                    radius = radius,
                    center = Offset(cx, cy),
                    style = if (isHitActive) activeRingStroke else outerRingStroke
                )

                // Hi-Hat Choke dampener ring on Open Hi-Hat when choked
                if (padId == DrumPadId.HI_HAT_OPEN && isChokeFlashActive) {
                    drawCircle(
                        color = StudioColors.ElectricAmber,
                        radius = radius * 0.88f,
                        center = Offset(cx, cy),
                        style = activeRingStroke
                    )
                }
            } else {
                // Rounded rectangular Kick Pedal pad
                val halfW = rx * scale
                val halfH = ry * scale
                val topLeft = Offset(cx - halfW, cy - halfH)
                val rectSize = Size(halfW * 2f, halfH * 2f)
                val corner = CornerRadius(32f, 32f)

                drawRoundRect(
                    color = shellFillColor,
                    topLeft = topLeft,
                    size = rectSize,
                    cornerRadius = corner
                )
                drawRoundRect(
                    color = StudioColors.SurfaceInnerDark,
                    topLeft = Offset(cx - halfW * 0.84f, cy - halfH * 0.78f),
                    size = Size(halfW * 1.68f, halfH * 1.56f),
                    cornerRadius = CornerRadius(24f, 24f)
                )
                drawRoundRect(
                    color = if (isHitActive) {
                        StudioColors.ElectricAmber
                    } else {
                        categoryColor.copy(alpha = 0.65f)
                    },
                    topLeft = topLeft,
                    size = rectSize,
                    cornerRadius = corner,
                    style = if (isHitActive) activeRingStroke else outerRingStroke
                )
            }

            // Expanding velocity ripple at exact touch coordinate (Req 1.3)
            if (latestHit != null && latestHit.progress < 1f) {
                val rippleAlpha = ((1f - latestHit.progress) * latestHit.velocity).coerceIn(0f, 1f)
                val maxRippleRadius = min(rx, ry) * (0.35f + 0.65f * latestHit.progress)
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

            // Crisp pad primary label & shortcut key badge via pre-allocated native Paint
            drawContext.canvas.nativeCanvas.apply {
                drawText(padId.shortLabel, cx, cy + 4f, primaryLabelPaint)
                drawText("[${padId.shortcutKey}]", cx, cy + 32f, subLabelPaint)
            }
        }
    }
}
