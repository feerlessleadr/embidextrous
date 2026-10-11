package com.feerlessleadr.embidextrous.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("How will you use this app?", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Text(
                "This decides which tabs and Home cards this phone shows. You can change it any time in Settings. It does not lock anything.",
                style = MaterialTheme.typography.bodyMedium,
            )
            for (profile in listOf(AppProfile.Parent, AppProfile.Administrator)) {
                Card(Modifier.fillMaxWidth().clickable { onChosen(profile, false) }) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(profile.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(profile.summary, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Card(Modifier.fillMaxWidth().clickable { custom = true }) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(AppProfile.Custom.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(AppProfile.Custom.summary, style = MaterialTheme.typography.bodyMedium)
                    if (custom) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Show the Smart Lists tab", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Switch(checked = smartLists, onCheckedChange = { smartLists = it })
                        }
                        Button(onClick = { onChosen(AppProfile.Custom, smartLists) }, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
                    }
                }
            }
        }
    }
}
