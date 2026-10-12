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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** True inside a group that shows a tab row: the tabs already say where you are, so the screens inside leave their own title out. */
val LocalInTabbedGroup = compositionLocalOf { false }

/** One section inside a bottom-bar group, shown as a tab along the top of that group. The [key] names it, so a link can open exactly this one. */
class SubTab(val key: String, val label: String, val content: @Composable () -> Unit)

/** Where a group's selected tab is, given the saved [key]: a key that no longer exists (a changed profile) falls back to the first tab. */
fun selectedIndex(tabs: List<SubTab>, key: String): Int = tabs.indexOfFirst { it.key == key }.coerceAtLeast(0)

/**
 * A bottom-bar destination that holds several related screens as tabs along the top, so the bottom bar stays at four or five items
 * however many services are added. The sections can be changed by tapping a tab or by swiping sideways. With only one section there is no tab
 * row (and nothing to swipe).
 */
@Composable
fun GroupTabs(tabs: List<SubTab>, selectedKey: String, onSelect: (String) -> Unit, header: (@Composable () -> Unit)? = null) {
    val index = selectedIndex(tabs, selectedKey)
    Column(Modifier.fillMaxSize()) {
        if (tabs.size > 1) {
            Box(Modifier.statusBarsPadding()) {
                val row: @Composable () -> Unit = {
                    tabs.forEachIndexed { i, tab ->
                        Tab(
                            selected = i == index,
                            onClick = { onSelect(tab.key) },
                            text = { Text(tab.label, fontWeight = if (i == index) FontWeight.Bold else FontWeight.Normal) },
                        )
                    }
                }
                if (tabs.size > 4) {
                    PrimaryScrollableTabRow(selectedTabIndex = index, edgePadding = 8.dp, containerColor = MaterialTheme.colorScheme.background, tabs = row)
                } else {
                    PrimaryTabRow(selectedTabIndex = index, containerColor = MaterialTheme.colorScheme.background, tabs = row)
                }
            }
        }
        header?.invoke()
        // The screens inside bring their own top bars; the tab row has already taken the status bar space.
        Box(Modifier.weight(1f).let { if (tabs.size > 1) it.consumeWindowInsets(WindowInsets.statusBars) else it }) {
            if (tabs.size > 1) {
                val pager = rememberPagerState(initialPage = index) { tabs.size }
                val currentTabs by rememberUpdatedState(tabs)
                val currentKey by rememberUpdatedState(selectedKey)
                // A tapped tab or a link moves the pages; a swipe that settles on another page selects that tab. Until the pages have caught up
                // with the selection (the pager may have restored an older page, or be moving towards a linked tab) its position is not a choice.
                var caughtUp by remember { mutableStateOf(false) }
                LaunchedEffect(index) {
                    caughtUp = false
                    if (pager.currentPage != index) pager.animateScrollToPage(index)
                    caughtUp = true
                }
                LaunchedEffect(pager) {
                    snapshotFlow { if (caughtUp) pager.settledPage else -1 }.collect { page ->
                        if (page >= 0) currentTabs.getOrNull(page)?.let { if (it.key != currentKey) onSelect(it.key) }
                    }
                }
                HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { currentTabs.getOrNull(it)?.key ?: it }) { page ->
                    CompositionLocalProvider(LocalInTabbedGroup provides true) { currentTabs.getOrNull(page)?.content?.invoke() }
                }
            } else {
                tabs.firstOrNull()?.content?.invoke()
            }
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
