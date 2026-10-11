package com.feerlessleadr.embidextrous.ui

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feerlessleadr.embidextrous.AppContainer
import com.feerlessleadr.embidextrous.data.Catalog
import com.feerlessleadr.embidextrous.data.Density
import com.feerlessleadr.embidextrous.data.RefreshStatus
import com.feerlessleadr.embidextrous.data.SmartListDoc
import com.feerlessleadr.embidextrous.data.Swatch
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListsScreen(
    vm: ListsViewModel,
    container: AppContainer,
    onNew: () -> Unit,
    onEdit: (String) -> Unit,
    onDuplicate: (String) -> Unit,
    onSettings: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val density by container.settings.density.collectAsStateWithLifecycle()
    val colors by container.settings.listColors.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var searching by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }
    // A deleted list can be brought back for a few seconds. A newer delete replaces the snackbar of the older one.
    LaunchedEffect(Unit) {
        vm.deleted.collectLatest { doc ->
            val result = snackbar.showSnackbar("Deleted ${doc.name}", actionLabel = "Undo", duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) vm.undoDelete(doc.id)
        }
    }

    val shown = state.visible
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Embidextrous") },
                actions = {
                    IconButton(onClick = {
                        searching = !searching
                        if (!searching) vm.setQuery("")
                    }) {
                        Icon(if (searching) Icons.Default.Close else Icons.Default.Search, contentDescription = if (searching) "Close search" else "Search lists")
                    }
                    IconButton(onClick = vm::refreshAll, enabled = state.lists.isNotEmpty()) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh all lists")
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNew,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New list") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            AnimatedVisibility(searching, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = vm::setQuery,
                    placeholder = { Text("Search by name") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            if (state.lists.isNotEmpty()) {
                FilterRow(
                    state = state,
                    density = density,
                    onFilter = vm::setFilter,
                    onToggleDensity = { container.settings.setDensity(if (density == Density.Comfortable) Density.Compact else Density.Comfortable) },
                )
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    state.error != null && state.lists.isEmpty() -> ErrorState(state.error.orEmpty(), onRetry = vm::reload, modifier = Modifier.align(Alignment.Center))
                    else -> PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = vm::reload, modifier = Modifier.fillMaxSize()) {
                        if (state.lists.isEmpty()) {
                            Text(
                                "No smart lists yet. Tap \"New list\" to create one.",
                                modifier = Modifier.align(Alignment.Center).padding(32.dp),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else if (shown.isEmpty()) {
                            Text(
                                "No lists match.",
                                modifier = Modifier.align(Alignment.Center).padding(32.dp),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 96.dp),
                                verticalArrangement = Arrangement.spacedBy(if (density == Density.Compact) 6.dp else 10.dp),
                            ) {
                                items(shown, key = { it.id }) { doc ->
                                    ListCard(
                                        modifier = Modifier.animateItem(),
                                        doc = doc,
                                        catalog = state.catalog,
                                        status = state.status,
                                        compact = density == Density.Compact,
                                        mark = colors[doc.id],
                                        busy = doc.id in state.busyIds,
                                        loadCover = { vm.cover(doc) },
                                        onEdit = { onEdit(doc.id) },
                                        onDuplicate = { onDuplicate(doc.id) },
                                        onShare = {
                                            val send = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_SUBJECT, doc.name)
                                                putExtra(Intent.EXTRA_TEXT, vm.shareText(doc))
                                            }
                                            context.startActivity(Intent.createChooser(send, "Share \"${doc.name}\""))
                                        },
                                        onRefresh = { vm.refreshList(doc) },
                                        onToggleEnabled = { vm.setEnabled(doc, !doc.enabled) },
                                        onDelete = { vm.delete(doc) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterRow(state: ListsUiState, density: Density, onFilter: (ListFilter) -> Unit, onToggleDensity: () -> Unit) {
    val counts = ListFilter.entries.associateWith { f -> state.lists.count { it.id !in state.hiddenIds && matchesFilter(it, f, state.status) } }
    Row(verticalAlignment = Alignment.CenterVertically) {
        LazyRow(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // "Failed" only shows up when something failed, and "Disabled" only when something is disabled.
            items(ListFilter.entries.filter { it == ListFilter.All || it == state.filter || (counts[it] ?: 0) > 0 }) { f ->
                FilterChip(
                    selected = state.filter == f,
                    onClick = { onFilter(f) },
                    label = { Text(if (f == ListFilter.All) "All ${counts[f]}" else "${f.label} ${counts[f]}") },
                )
            }
        }
        IconButton(onClick = onToggleDensity) {
            Icon(
                if (density == Density.Comfortable) Icons.AutoMirrored.Filled.ViewList else Icons.Default.ViewAgenda,
                contentDescription = if (density == Density.Comfortable) "Use compact cards" else "Use comfortable cards",
            )
        }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = onRetry) { Text("Try again") }
    }
}

/** The cover of a list's Emby item, or its type icon on a tinted tile until there is one. */
@Composable
private fun Cover(doc: SmartListDoc, size: androidx.compose.ui.unit.Dp, loadCover: suspend () -> Bitmap?) {
    val bitmap by produceState<Bitmap?>(null, doc.embyItemId, doc.lastRefreshed) { value = loadCover() }
    Box(
        Modifier.size(size).clip(RoundedCornerShape(if (size > 48.dp) 12.dp else 8.dp)).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(bitmap, label = "cover") { b ->
            if (b != null) {
                Image(b.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(
                    if (doc.isCollection) Icons.Default.VideoLibrary else Icons.AutoMirrored.Filled.PlaylistPlay,
                    contentDescription = doc.type,
                    tint = if (doc.enabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(size * 0.5f),
                )
            }
        }
    }
}

@Composable
private fun ListCard(
    doc: SmartListDoc,
    catalog: Catalog?,
    status: RefreshStatus,
    compact: Boolean,
    mark: Swatch?,
    busy: Boolean,
    loadCover: suspend () -> Bitmap?,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onShare: () -> Unit,
    onRefresh: () -> Unit,
    onToggleEnabled: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable(doc.id) { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val running = status.running[doc.id]
    val failed = doc.refreshFailed(status)

    Card(
        modifier = modifier.fillMaxWidth().animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (doc.enabled) 0.55f else 0.3f)),
    ) {
        Column {
            if (busy || running != null) {
                if (running != null && running.total > 0) {
                    LinearProgressIndicator(progress = { (running.processed.toFloat() / running.total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
            Row(Modifier.height(IntrinsicSize.Min)) {
                // The colour the list was marked with, as a stripe on the card's edge.
                Box(Modifier.width(5.dp).fillMaxHeight().background(mark?.dotColor() ?: androidx.compose.ui.graphics.Color.Transparent))
                Row(
                    modifier = Modifier.weight(1f).clickable { expanded = !expanded }
                        .padding(start = 12.dp, top = if (compact) 8.dp else 12.dp, bottom = if (compact) 8.dp else 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Cover(doc, size = if (compact) 40.dp else 60.dp, loadCover = loadCover)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(doc.name, style = MaterialTheme.typography.titleMedium, maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            when {
                                running != null -> if (running.total > 0) "Refreshing… ${running.processed} of ${running.total}" else "Refreshing…"
                                doc.enabled -> Describe.summary(doc)
                                else -> "Disabled · ${Describe.summary(doc)}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (compact) 1 else 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (failed && running == null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                                Text("  Last refresh failed", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = if (expanded) "Hide details" else "Show details")
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More actions") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, enabled = !busy, onClick = { menu = false; onEdit() })
                            DropdownMenuItem(text = { Text("Duplicate") }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) }, enabled = !busy, onClick = { menu = false; onDuplicate() })
                            DropdownMenuItem(text = { Text("Share…") }, leadingIcon = { Icon(Icons.Default.Share, null) }, onClick = { menu = false; onShare() })
                            DropdownMenuItem(
                                text = { Text("Refresh now") },
                                leadingIcon = { Icon(Icons.Default.Refresh, null) },
                                enabled = doc.enabled && !busy && running == null,
                                onClick = { menu = false; onRefresh() },
                            )
                            DropdownMenuItem(
                                text = { Text(if (doc.enabled) "Disable" else "Enable") },
                                leadingIcon = { Icon(if (doc.enabled) Icons.Default.PauseCircle else Icons.Default.PlayCircle, null) },
                                enabled = !busy,
                                onClick = { menu = false; onToggleEnabled() },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                enabled = !busy,
                                onClick = { menu = false; onDelete() },
                            )
                        }
                    }
                }
            }
            AnimatedVisibility(expanded, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Details(doc, catalog, status)
            }
        }
    }
}

@Composable
private fun Details(doc: SmartListDoc, catalog: Catalog?, status: RefreshStatus) {
    Column(Modifier.padding(start = 17.dp, end = 16.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        DetailLine("Type", "${doc.type} · ${doc.mediaTypes.joinToString(", ").ifEmpty { "any media" }}")

        val groups = Describe.ruleGroups(doc, catalog)
        if (groups.isEmpty()) {
            DetailLine("Rules", "none (everything of that type)")
        } else {
            groups.forEachIndexed { index, text ->
                DetailLine(if (index == 0) "Rules" else "or", text)
            }
        }

        val sorts = doc.sortOptions
        if (sorts.isNotEmpty()) DetailLine("Sort", sorts.joinToString("; ") { Describe.sort(it, catalog) })

        val limits = listOfNotNull(
            doc.maxItems?.let { "at most $it items" },
            doc.maxPlayTimeMinutes?.let { "at most $it minutes" },
            doc.minItems?.let { "hidden below $it items" },
        )
        if (limits.isNotEmpty()) DetailLine("Limits", limits.joinToString(", "))
        DetailLine("Refresh", Describe.autoRefresh(doc.autoRefresh, catalog))
        status.last[doc.id]?.takeIf { !it.success }?.let { DetailLine("Error", it.error ?: "The last refresh failed.") }
    }
}

@Composable
private fun DetailLine(label: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp).size(width = 52.dp, height = 20.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}