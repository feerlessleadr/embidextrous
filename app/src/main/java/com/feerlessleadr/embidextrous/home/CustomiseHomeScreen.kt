package com.feerlessleadr.embidextrous.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feerlessleadr.embidextrous.AppContainer
import com.feerlessleadr.embidextrous.data.HomeCard
import com.feerlessleadr.embidextrous.data.HomeLayout

/** Choose which Home cards show and in what order. The status card at the top always stays. Saved on this phone as it changes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomiseHomeScreen(container: AppContainer, administrator: Boolean, onBack: () -> Unit) {
    val layout by container.settings.homeLayout.collectAsStateWithLifecycle()
    val available = HomeCard.availableFor(administrator)
    val cards = layout.configurable(available)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Customise Home") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = { TextButton(onClick = { container.settings.setHomeLayout(HomeLayout()) }) { Text("Reset") } },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    "The status line at the top always stays. Switch cards off to hide them, and use the arrows to move them.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(4.dp),
                )
            }
            items(cards, key = { it.name }) { card ->
                val index = cards.indexOf(card)
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(card.label, style = MaterialTheme.typography.titleMedium)
                            Text(card.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { container.settings.setHomeLayout(layout.moved(card, -1, available)) }, enabled = index > 0) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move ${card.label} up")
                        }
                        IconButton(onClick = { container.settings.setHomeLayout(layout.moved(card, 1, available)) }, enabled = index < cards.lastIndex) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move ${card.label} down")
                        }
                        Switch(
                            checked = card !in layout.hidden,
                            onCheckedChange = { container.settings.setHomeLayout(layout.toggled(card)) },
                            modifier = Modifier.padding(end = 12.dp),
                        )
                    }
                }
            }
        }
    }
}
