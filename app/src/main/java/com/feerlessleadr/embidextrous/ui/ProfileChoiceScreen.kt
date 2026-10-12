package com.feerlessleadr.embidextrous.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.feerlessleadr.embidextrous.data.AppProfile

/**
 * Shown once after an administrator account signs in: how will this phone use the app? The answer decides the tabs and the Home
 * cards, can be changed in Settings, and is a convenience, not a lock (two people may share one account).
 */
@Composable
fun ProfileChoiceScreen(onChosen: (AppProfile, Boolean) -> Unit) {
    var custom by remember { mutableStateOf(false) }
    var smartLists by remember { mutableStateOf(true) }
    BrandBackdrop(Modifier.fillMaxSize()) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            LogoMark(56.dp)
            Text(
                "How will you use this app?",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Brand.OnDark,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                "This decides which tabs and Home cards this phone shows. You can change it any time in Settings. It does not lock anything.",
                style = MaterialTheme.typography.bodyMedium,
                color = Brand.OnDarkMuted,
            )
            Spacer(Modifier.height(4.dp))

            ProfileCard(
                icon = Icons.Default.FamilyRestroom,
                tint = accentOnDark(),
                title = AppProfile.Parent.label,
                summary = AppProfile.Parent.summary,
                shows = listOf("Home", "Hall Monitor", "Settings"),
                onClick = { onChosen(AppProfile.Parent, false) },
            )
            ProfileCard(
                icon = Icons.Default.AdminPanelSettings,
                tint = accentOnDark(),
                title = AppProfile.Administrator.label,
                summary = AppProfile.Administrator.summary,
                shows = listOf("Home", "Hall Monitor", "Smart Lists", "Server cards", "Settings"),
                onClick = { onChosen(AppProfile.Administrator, false) },
            )
            ProfileCard(
                icon = Icons.Default.Tune,
                tint = accentOnDark(),
                title = AppProfile.Custom.label,
                summary = AppProfile.Custom.summary,
                shows = listOf("Home", "Hall Monitor", "Settings") + if (custom && smartLists) listOf("Smart Lists") else emptyList(),
                onClick = { custom = true },
                showChevron = !custom,
            ) {
                if (custom) {
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Show the Smart Lists tab", style = MaterialTheme.typography.bodyLarge, color = Brand.OnDark, modifier = Modifier.weight(1f))
                        Switch(
                            checked = smartLists,
                            onCheckedChange = { smartLists = it },
                            colors = SwitchDefaults.colors(checkedTrackColor = accentOnDark(), checkedThumbColor = onAccent(accentOnDark())),
                        )
                    }
                    Button(
                        onClick = { onChosen(AppProfile.Custom, smartLists) },
                        colors = ButtonDefaults.buttonColors(containerColor = accentOnDark(), contentColor = onAccent(accentOnDark())),
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                    ) { Text("Continue", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

/** One choice: a glassy card with an icon, what it is for, and the tabs it shows. Tapping it chooses it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileCard(
    icon: ImageVector,
    tint: Color,
    title: String,
    summary: String,
    shows: List<String>,
    onClick: () -> Unit,
    showChevron: Boolean = true,
    extra: @Composable () -> Unit = {},
) {
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = Color.White.copy(alpha = 0.07f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                IconBadge(icon, tint, size = 52.dp)
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Brand.OnDark)
                    Text(summary, style = MaterialTheme.typography.bodyMedium, color = Brand.OnDarkMuted)
                }
                if (showChevron) Icon(Icons.Default.ChevronRight, contentDescription = null, tint = tint)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (tab in shows) {
                    Text(
                        tab,
                        style = MaterialTheme.typography.labelMedium,
                        color = Brand.OnDark,
                        modifier = Modifier.background(Color.White.copy(alpha = 0.10f), CircleShape).padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            extra()
        }
    }
}
