package com.drumsheesh.ui.components

import android.graphics.Paint as AndroidPaint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drumsheesh.core.coach.HitClassification
import com.drumsheesh.core.coach.HitJudgement
import com.drumsheesh.core.coach.RhythmPocketCoach
import com.drumsheesh.core.coach.ScheduledGrooveStep
import com.drumsheesh.core.model.DrumPadId
import com.drumsheesh.ui.theme.StudioColors
import com.drumsheesh.ui.theme.StudioTokens
import kotlin.math.abs

/**
 * 6 compact horizontal lanes on the Groove Highway (`stitch-prompt.md` Screen 1).
 */
enum class HighwayLane(
    val rowIndex: Int,
    val label: String,
    val laneColor: Color
) {
    KICK(0, "KICK", StudioColors.KickRingViolet),
    SNARE(1, "SNARE", StudioColors.SnareRingCoral),
    CLOSED_HH(2, "CLOSED HH", StudioColors.CymbalRingBrass),
    OPEN_HH(3, "OPEN HH", StudioColors.GoodBlue),
    TOMS(4, "TOMS", StudioColors.TomRingCyan),
    CYMBALS(5, "CYMBALS", StudioColors.ElectricAmberHover);

    companion object {
        fun forPad(padId: DrumPadId): HighwayLane = when (padId) {
            DrumPadId.KICK_LEFT, DrumPadId.KICK_RIGHT -> KICK
            DrumPadId.SNARE -> SNARE
            DrumPadId.HI_HAT_CLOSED -> CLOSED_HH
            DrumPadId.HI_HAT_OPEN -> OPEN_HH
            DrumPadId.TOM_HIGH, DrumPadId.TOM_MID, DrumPadId.TOM_FLOOR -> TOMS
            DrumPadId.CRASH, DrumPadId.RIDE -> CYMBALS
        }
    }
}

/**
 * Semantic color mapping for [HitClassification] badges and Now-Line flashes.
 */
fun hitClassificationColor(classification: HitClassification): Color = when (classification) {
    HitClassification.PERFECT -> StudioColors.PerfectGreen
    HitClassification.GOOD -> StudioColors.GoodBlue
    HitClassification.EARLY, HitClassification.LATE -> StudioColors.DriftWarning
    HitClassification.MISS -> StudioColors.MissOrRecRed
}

/**
 * 60/120fps scrolling Groove Highway Canvas (`Req 1.3`).
 *
 * Renders the 6-lane horizontal note guide (`KICK`, `SNARE`, `CLOSED HH`, `OPEN HH`, `TOMS`, `CYMBALS`),
 * incoming note pills scrolling from right to left toward the `#FBBF24` Now-Line, Now-Line hit flash,
 * and the live [HitJudgement] pill with **zero per-frame object allocations** inside `Canvas`.
 */
@Composable
fun GrooveHighwayRenderer(
    scheduledSteps: List<ScheduledGrooveStep>,
    playheadTimeMs: Long,
    lookaheadWindowMs: Long = 2_400L,
    pastWindowMs: Long = 260L,
    lastJudgement: HitJudgement? = null,
    isNowLineFlashing: Boolean = false,
    modifier: Modifier = Modifier
) {
    val laneTextPaint = remember {
        AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            color = StudioColors.TextPrimaryDark.copy(alpha = 0.85f).toArgb()
            textSize = 20f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
    }

    val noteBorderStroke = remember { Stroke(width = 2.5f) }
    val nowLineGlowStroke = remember { Stroke(width = 10f) }
    val nowLineCoreStroke = remember { Stroke(width = 4f) }
    val noteCornerRadius = remember { CornerRadius(10f, 10f) }
    val lanes = remember { HighwayLane.entries }

    val badgeText = remember(lastJudgement) {
        lastJudgement?.let { RhythmPocketCoach.formatJudgementBadge(it) } ?: "READY · HIT PADS"
    }
    val badgeColor = remember(lastJudgement) {
        lastJudgement?.let { hitClassificationColor(it.classification) } ?: StudioColors.TextSecondaryDark
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(104.dp)
            .clip(RoundedCornerShape(StudioTokens.RadiusLg))
            .background(StudioColors.SurfaceElevatedDark.copy(alpha = 0.96f))
            .border(1.dp, StudioColors.BorderDark, RoundedCornerShape(StudioTokens.RadiusLg))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasW = size.width
            val canvasH = size.height
            val laneCount = lanes.size
            val laneHeight = canvasH / laneCount.toFloat()
            laneTextPaint.textSize = (laneHeight * 0.56f).coerceIn(16f, 24f)

            // Now-Line sits at 22% from the left edge so incoming notes scroll right -> left
            val labelGutterWidth = canvasW * 0.14f
            val nowLineX = canvasW * 0.22f
            val scrollableRightWidth = (canvasW - nowLineX).coerceAtLeast(1f)
            val scrollableLeftWidth = (nowLineX - labelGutterWidth).coerceAtLeast(1f)

            // 1. Draw alternating lane backgrounds and horizontal dividers
            for (i in 0 until laneCount) {
                val lane = lanes[i]
                val topY = i * laneHeight
                val rowBg = if (i % 2 == 0) {
                    StudioColors.SurfaceDark.copy(alpha = 0.65f)
                } else {
                    StudioColors.StageBackgroundDark.copy(alpha = 0.55f)
                }
                drawRect(
                    color = rowBg,
                    topLeft = Offset(0f, topY),
                    size = Size(canvasW, laneHeight)
                )
                drawLine(
                    color = StudioColors.BorderDark.copy(alpha = 0.7f),
                    start = Offset(0f, topY + laneHeight),
                    end = Offset(canvasW, topY + laneHeight),
                    strokeWidth = 1f
                )

                // Draw lane label in left gutter
                drawContext.canvas.nativeCanvas.drawText(
                    lane.label,
                    16f,
                    topY + (laneHeight * 0.64f),
                    laneTextPaint
                )
            }

            // 2. Draw incoming & recently passed note pills without allocating collections
            val minVisibleMs = playheadTimeMs - pastWindowMs
            val maxVisibleMs = playheadTimeMs + lookaheadWindowMs

            for (idx in scheduledSteps.indices) {
                val step = scheduledSteps[idx]
                val expectedMs = step.expectedTimeMs
                if (expectedMs < minVisibleMs || expectedMs > maxVisibleMs) continue

                val lane = HighwayLane.forPad(step.padId)
                val timeOffsetMs = expectedMs - playheadTimeMs

                val centerX = if (timeOffsetMs >= 0L) {
                    val norm = timeOffsetMs.toFloat() / lookaheadWindowMs.toFloat()
                    nowLineX + norm * scrollableRightWidth
                } else {
                    val norm = abs(timeOffsetMs).toFloat() / pastWindowMs.toFloat()
                    nowLineX - norm * scrollableLeftWidth
                }

                val pillWidth = (28f + step.velocity * 18f)
                val pillHeight = (laneHeight * 0.62f).coerceAtLeast(10f)
                val topY = lane.rowIndex * laneHeight + (laneHeight - pillHeight) * 0.5f
                val leftX = centerX - (pillWidth * 0.5f)

                val distanceToNowMs = abs(timeOffsetMs)
                val isCrossingNow = distanceToNowMs <= RhythmPocketCoach.GOOD_WINDOW_MS
                val fillAlpha = when {
                    timeOffsetMs < -RhythmPocketCoach.GOOD_WINDOW_MS -> 0.32f
                    isCrossingNow -> 0.95f
                    else -> 0.78f
                }

                drawRoundRect(
                    color = lane.laneColor.copy(alpha = fillAlpha),
                    topLeft = Offset(leftX, topY),
                    size = Size(pillWidth, pillHeight),
                    cornerRadius = noteCornerRadius
                )
                drawRoundRect(
                    color = if (isCrossingNow) StudioColors.ElectricAmber else lane.laneColor,
                    topLeft = Offset(leftX, topY),
                    size = Size(pillWidth, pillHeight),
                    cornerRadius = noteCornerRadius,
                    style = noteBorderStroke
                )
            }

            // 3. Draw vertical #FBBF24 Now-Line + optional hit flash glow
            if (isNowLineFlashing) {
                drawLine(
                    color = badgeColor.copy(alpha = 0.42f),
                    start = Offset(nowLineX, 0f),
                    end = Offset(nowLineX, canvasH),
                    strokeWidth = nowLineGlowStroke.width
                )
            }
            drawLine(
                color = StudioColors.ElectricAmber,
                start = Offset(nowLineX, 0f),
                end = Offset(nowLineX, canvasH),
                strokeWidth = nowLineCoreStroke.width
            )
        }

        // Top-right Live HitJudgement Badge Overlay (only recomposes when lastJudgement changes)
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(StudioTokens.SpaceSmall)
                .clip(RoundedCornerShape(StudioTokens.RadiusSm))
                .background(StudioColors.StageBackgroundDark.copy(alpha = 0.90f))
                .border(1.dp, badgeColor, RoundedCornerShape(StudioTokens.RadiusSm))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = badgeText,
                color = badgeColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
