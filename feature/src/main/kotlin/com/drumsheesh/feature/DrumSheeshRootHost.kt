package com.drumsheesh.feature

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.drumsheesh.core.model.DrumKitId
import com.drumsheesh.core.model.DrumPadId
import com.drumsheesh.ui.theme.StudioColors
import com.drumsheesh.ui.theme.StudioTokens

@Composable
fun DrumSheeshRootHost(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(StudioColors.StageBackgroundDark)
            .padding(StudioTokens.SpaceDefault),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(StudioTokens.SpaceSmall)
        ) {
            Text(
                text = "DRUM SHEESH",
                style = MaterialTheme.typography.headlineLarge,
                color = StudioColors.ElectricAmber
            )
            Text(
                text = "${DrumPadId.entries.size}-Pad Multi-Touch Stage • ${DrumKitId.entries.size} Studio Kits Ready",
                style = MaterialTheme.typography.bodyMedium,
                color = StudioColors.TextSecondaryDark
            )
        }
    }
}
