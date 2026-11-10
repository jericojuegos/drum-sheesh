package com.drumsheesh.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.drumsheesh.core.model.PadCategory

object StudioTokens {
    // Spacing Scale
    val SpaceMicro = 4.dp
    val SpaceSmall = 8.dp
    val SpaceCompact = 12.dp
    val SpaceDefault = 16.dp
    val SpaceComfortable = 20.dp
    val SpaceSpacious = 24.dp
    val SpaceLarge = 32.dp

    // Layout Constants
    val TopHudBarHeight = 52.dp
    val TabletBreakpointWidth = 840.dp

    // Corner Radii
    val RadiusSm = 6.dp
    val RadiusMd = 12.dp
    val RadiusLg = 18.dp
    val RadiusXl = 24.dp

    // Motion Constants
    const val PAD_IMPULSE_DECAY_MS = 140L

    fun categoryRingColor(category: PadCategory): Color = when (category) {
        PadCategory.CYMBAL -> StudioColors.CymbalRingBrass
        PadCategory.SNARE -> StudioColors.SnareRingCoral
        PadCategory.TOM -> StudioColors.TomRingCyan
        PadCategory.KICK -> StudioColors.KickRingViolet
    }
}
