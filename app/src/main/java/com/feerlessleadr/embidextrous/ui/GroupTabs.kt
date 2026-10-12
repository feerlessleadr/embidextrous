package com.feerlessleadr.embidextrous.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** True inside a group that shows a tab row: the tabs already say where you are, so the screens inside leave their own title out. */
val LocalInTabbedGroup = androidx.compose.runtime.compositionLocalOf { false }

/** One section inside a bottom-bar group, shown as a tab along the top of that group. */
class SubTab(val label: String, val content: @Composable () -> Unit)

/**
 * A bottom-bar destination that holds several related screens as tabs along the top, so the bottom bar stays at four or five items
 * however many services are added. With only one section there is no tab row at all.
 */
@Composable
fun GroupTabs(tabs: List<SubTab>, selected: Int, onSelect: (Int) -> Unit, header: (@Composable () -> Unit)? = null) {
    val index = selected.coerceIn(0, tabs.lastIndex)
    Column(Modifier.fillMaxSize()) {
        if (tabs.size > 1) {
            Box(Modifier.statusBarsPadding()) {
                val row: @Composable () -> Unit = {
                    tabs.forEachIndexed { i, tab ->
                        Tab(
                            selected = i == index,
                            onClick = { onSelect(i) },
                            text = { Text(tab.label, fontWeight = if (i == index) FontWeight.Bold else FontWeight.Normal) },
                        )
                    }
                }
                if (tabs.size > 4) {
                    androidx.compose.material3.PrimaryScrollableTabRow(selectedTabIndex = index, edgePadding = 8.dp, containerColor = MaterialTheme.colorScheme.background, tabs = row)
                } else {
                    PrimaryTabRow(selectedTabIndex = index, containerColor = MaterialTheme.colorScheme.background, tabs = row)
                }
            }
        }
        header?.invoke()
        // The screens inside bring their own top bars; the tab row has already taken the status bar space.
        Box(Modifier.weight(1f).let { if (tabs.size > 1) it.consumeWindowInsets(WindowInsets.statusBars) else it }) {
            androidx.compose.runtime.CompositionLocalProvider(LocalInTabbedGroup provides (tabs.size > 1)) { tabs[index].content() }
        }
    }
}

/** A stand-in for a section that is planned but not built yet (shown in debug builds only, to show where it will live). */
@Composable
fun ComingSoon(icon: ImageVector, title: String, text: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconBadge(icon, MaterialTheme.colorScheme.primary, size = 88.dp)
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Pill("Coming soon", MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}
