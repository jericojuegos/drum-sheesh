package com.drumsheesh.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.drumsheesh.core.model.DrumKitId
import com.drumsheesh.core.model.DrumPadId
import com.drumsheesh.core.model.DrumSoundProfile
import com.drumsheesh.core.model.PadMixSetting
import com.drumsheesh.core.model.RoomAmbiencePreset
import com.drumsheesh.core.model.SoundCharacterPreset
import com.drumsheesh.ui.theme.StudioColors
import com.drumsheesh.ui.theme.StudioTokens
import kotlin.math.roundToInt

private val OrderedMixerPads = listOf(
    DrumPadId.SNARE,
    DrumPadId.KICK_RIGHT,
    DrumPadId.KICK_LEFT,
    DrumPadId.HI_HAT_CLOSED,
    DrumPadId.HI_HAT_OPEN,
    DrumPadId.TOM_HIGH,
    DrumPadId.TOM_MID,
    DrumPadId.TOM_FLOOR,
    DrumPadId.CRASH,
    DrumPadId.RIDE
)

/**
 * Landscape-first Studio Sound & Mixer console for adjusting room acoustics, punch,
 * tonal warmth, character presets, and per-pad volume/pitch tuning with tap-to-audition.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SoundMixerModal(
    isOpen: Boolean,
    selectedKit: DrumKitId,
    profile: DrumSoundProfile,
    onDismiss: () -> Unit,
    onApplyCharacterPreset: (SoundCharacterPreset) -> Unit,
    onSelectRoomAmbience: (RoomAmbiencePreset) -> Unit,
    onSetPunchAndWarmth: (punch: Float, warmth: Float) -> Unit,
    onUpdatePadMix: (padId: DrumPadId, volume: Float, pitchSemitones: Float) -> Unit,
    onAuditionPad: (DrumPadId) -> Unit,
    onResetProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isOpen) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(StudioTokens.RadiusLg))
                .background(StudioColors.SurfaceElevatedDark)
                .border(1.dp, StudioColors.BorderDark, RoundedCornerShape(StudioTokens.RadiusLg))
                .padding(StudioTokens.SpaceCompact),
            verticalArrangement = Arrangement.spacedBy(StudioTokens.SpaceSmall)
        ) {
            // Top Studio Console Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(StudioTokens.SpaceSmall)
                ) {
                    Text(
                        text = "STUDIO SOUND & MIXER",
                        color = StudioColors.TextPrimaryDark,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(StudioTokens.RadiusSm))
                            .background(StudioColors.ElectricAmberSubtle)
                            .border(1.dp, StudioColors.ElectricAmber, RoundedCornerShape(StudioTokens.RadiusSm))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = selectedKit.displayName.uppercase(),
                            color = StudioColors.ElectricAmber,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(StudioTokens.RadiusSm))
                            .background(StudioColors.SurfaceDark)
                            .border(1.dp, StudioColors.BorderDark, RoundedCornerShape(StudioTokens.RadiusSm))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = profile.roomPreset.displayName.uppercase(),
                            color = StudioColors.GoodBlue,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(StudioTokens.SpaceSmall)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(StudioTokens.RadiusMd))
                            .background(StudioColors.SurfaceDark)
                            .border(1.dp, StudioColors.BorderDark, RoundedCornerShape(StudioTokens.RadiusMd))
                            .clickable { onResetProfile() }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "RESET",
                            color = StudioColors.TextSecondaryDark,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(StudioTokens.RadiusMd))
                            .background(StudioColors.ElectricAmber)
                            .clickable { onDismiss() }
                            .padding(horizontal = 14.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "DONE",
                            color = StudioColors.StageBackgroundDark,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Two-column console layout: Studio Tone/Room on left, Per-Drum Mixer on right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(StudioTokens.SpaceCompact)
            ) {
                // Left Column: Studio Sound Presets, Room Acoustics, Punch & Warmth
                Column(
                    modifier = Modifier
                        .weight(0.47f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(StudioTokens.RadiusMd))
                        .background(StudioColors.SurfaceDark)
                        .border(1.dp, StudioColors.BorderDark, RoundedCornerShape(StudioTokens.RadiusMd))
                        .padding(StudioTokens.SpaceSmall)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(StudioTokens.SpaceSmall)
                ) {
                    Text(
                        text = "SOUND CHARACTER PRESETS",
                        color = StudioColors.TextSecondaryDark,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SoundCharacterPreset.entries.forEach { preset ->
                            val isSelected = profile.characterPreset == preset
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(StudioTokens.RadiusSm))
                                    .background(
                                        if (isSelected) StudioColors.ElectricAmberSubtle
                                        else StudioColors.SurfaceInnerDark
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) StudioColors.ElectricAmber else StudioColors.BorderDark,
                                        shape = RoundedCornerShape(StudioTokens.RadiusSm)
                                    )
                                    .clickable { onApplyCharacterPreset(preset) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = preset.displayName,
                                    color = if (isSelected) StudioColors.ElectricAmber else StudioColors.TextPrimaryDark,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = preset.subtitle,
                                    color = StudioColors.TextSecondaryDark,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }

                    Text(
                        text = "ROOM ACOUSTICS",
                        color = StudioColors.TextSecondaryDark,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RoomAmbiencePreset.entries.forEach { room ->
                            val active = profile.roomPreset == room
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(StudioTokens.RadiusSm))
                                    .background(
                                        if (active) StudioColors.GoodBlue.copy(alpha = 0.2f)
                                        else StudioColors.SurfaceInnerDark
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (active) StudioColors.GoodBlue else StudioColors.BorderDark,
                                        shape = RoundedCornerShape(StudioTokens.RadiusSm)
                                    )
                                    .clickable { onSelectRoomAmbience(room) }
                                    .padding(horizontal = 9.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = room.displayName,
                                    color = if (active) StudioColors.GoodBlue else StudioColors.TextPrimaryDark,
                                    fontSize = 11.sp,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Punch & Attack Slider
                    ToneControlSlider(
                        label = "PUNCH & ATTACK",
                        valueText = "${(profile.clampedPunch * 100f).roundToInt()}%",
                        value = profile.clampedPunch,
                        onValueChange = { nextPunch ->
                            onSetPunchAndWarmth(nextPunch, profile.clampedWarmth)
                        },
                        accentColor = StudioColors.ElectricAmber
                    )

                    // Warmth / Brightness Slider
                    val warmthPct = (profile.clampedWarmth * 100f).roundToInt()
                    val warmthLabel = when {
                        warmthPct < 35 -> "CRISP $warmthPct%"
                        warmthPct > 65 -> "WARM $warmthPct%"
                        else -> "STUDIO $warmthPct%"
                    }
                    ToneControlSlider(
                        label = "TONE (CRISP ↔ WARM)",
                        valueText = warmthLabel,
                        value = profile.clampedWarmth,
                        onValueChange = { nextWarmth ->
                            onSetPunchAndWarmth(profile.clampedPunch, nextWarmth)
                        },
                        accentColor = StudioColors.AiCoachIndigo
                    )
                }

                // Right Column: Per-Pad Volume & Pitch Tuning with Tap-to-Audition
                Column(
                    modifier = Modifier
                        .weight(0.53f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(StudioTokens.RadiusMd))
                        .background(StudioColors.SurfaceDark)
                        .border(1.dp, StudioColors.BorderDark, RoundedCornerShape(StudioTokens.RadiusMd))
                        .padding(StudioTokens.SpaceSmall),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "DRUM MIX & TUNING (TAP NAME TO TEST HIT)",
                        color = StudioColors.TextSecondaryDark,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(OrderedMixerPads, key = { it.name }) { padId ->
                            val padMix = profile.settingFor(padId)
                            val accent = StudioTokens.categoryRingColor(padId.category)
                            PadMixChannelStrip(
                                padId = padId,
                                padMix = padMix,
                                accentColor = accent,
                                onAudition = { onAuditionPad(padId) },
                                onVolumeChange = { nextVol ->
                                    onUpdatePadMix(padId, nextVol, padMix.clampedPitchSemitones)
                                },
                                onPitchChange = { nextPitch ->
                                    onUpdatePadMix(padId, padMix.clampedVolume, nextPitch)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToneControlSlider(
    label: String,
    valueText: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    accentColor: androidx.compose.ui.graphics.Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(StudioTokens.RadiusSm))
            .background(StudioColors.SurfaceInnerDark)
            .border(1.dp, StudioColors.BorderDark, RoundedCornerShape(StudioTokens.RadiusSm))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = StudioColors.TextPrimaryDark,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = valueText,
                color = accentColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = StudioColors.BorderDark
            )
        )
    }
}

@Composable
private fun PadMixChannelStrip(
    padId: DrumPadId,
    padMix: PadMixSetting,
    accentColor: androidx.compose.ui.graphics.Color,
    onAudition: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onPitchChange: (Float) -> Unit
) {
    val volPct = (padMix.clampedVolume * 100f).roundToInt()
    val semiRounded = (padMix.clampedPitchSemitones * 2f).roundToInt() / 2f
    val pitchText = when {
        semiRounded > 0f -> "+${semiRounded} st"
        semiRounded < 0f -> "$semiRounded st"
        else -> "0 st"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(StudioTokens.RadiusSm))
            .background(StudioColors.SurfaceInnerDark)
            .border(1.dp, StudioColors.BorderDark, RoundedCornerShape(StudioTokens.RadiusSm))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(StudioTokens.RadiusSm))
                    .background(accentColor.copy(alpha = 0.16f))
                    .border(1.dp, accentColor, RoundedCornerShape(StudioTokens.RadiusSm))
                    .clickable { onAudition() }
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "▶ ${padId.displayName}",
                    color = accentColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "VOL $volPct%",
                    color = StudioColors.TextPrimaryDark,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "TUNE $pitchText",
                    color = if (semiRounded != 0f) StudioColors.ElectricAmber else StudioColors.TextSecondaryDark,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.clickable { onPitchChange(0f) }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(StudioTokens.SpaceSmall),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "LEVEL",
                    color = StudioColors.TextSecondaryDark,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
                Slider(
                    value = padMix.clampedVolume,
                    onValueChange = onVolumeChange,
                    valueRange = 0f..PadMixSetting.MAX_VOLUME,
                    colors = SliderDefaults.colors(
                        thumbColor = accentColor,
                        activeTrackColor = accentColor,
                        inactiveTrackColor = StudioColors.BorderDark
                    )
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "TUNING (-6..+6)",
                    color = StudioColors.TextSecondaryDark,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
                Slider(
                    value = padMix.clampedPitchSemitones,
                    onValueChange = { raw ->
                        val snapped = (raw * 2f).roundToInt() / 2f
                        onPitchChange(snapped)
                    },
                    valueRange = PadMixSetting.MIN_SEMITONES..PadMixSetting.MAX_SEMITONES,
                    colors = SliderDefaults.colors(
                        thumbColor = StudioColors.ElectricAmber,
                        activeTrackColor = StudioColors.ElectricAmber,
                        inactiveTrackColor = StudioColors.BorderDark
                    )
                )
            }
        }
    }
}
