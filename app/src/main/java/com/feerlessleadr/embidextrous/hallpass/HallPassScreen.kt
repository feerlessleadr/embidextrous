package com.feerlessleadr.embidextrous.hallpass

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feerlessleadr.embidextrous.data.HpAlbum
import com.feerlessleadr.embidextrous.data.HpGate
import com.feerlessleadr.embidextrous.data.HpPreview
import com.feerlessleadr.embidextrous.data.HpRow
import com.feerlessleadr.embidextrous.data.HpStatus
import com.feerlessleadr.embidextrous.data.HallPass
import com.feerlessleadr.embidextrous.home.HomeCardShape
import com.feerlessleadr.embidextrous.home.okColor
import com.feerlessleadr.embidextrous.home.warnColor
import com.feerlessleadr.embidextrous.ui.IconBadge
import com.feerlessleadr.embidextrous.ui.Pill
import com.feerlessleadr.embidextrous.watching.ConfirmDialog

/** Words for the plugin's decisions. */
internal fun decisionLabel(decision: String): String = when (decision) {
    "Explicit" -> "Explicit"
    "Clean" -> "Clean"
    "LikelyClean" -> "Likely clean"
    "Review" -> "Needs review"
    "Skipped" -> "Skipped"
    "Unscanned" -> "Not scanned"
    else -> decision
}

@Composable
private fun decisionColor(decision: String): Color = when (decision) {
    "Explicit" -> MaterialTheme.colorScheme.error
    "Clean" -> okColor()
    "LikelyClean" -> MaterialTheme.colorScheme.primary
    "Review" -> warnColor()
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** The Library group's Hall Pass tab. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HallPassTab(vm: HallPassViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(vm) { vm.load() }
    val gate = state.gate

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, containerColor = Color.Transparent) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (gate !is HpGate.Ready) {
                PullToRefreshBox(isRefreshing = false, onRefresh = vm::load, modifier = Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp)) { item { GateCard(gate, vm::load) } }
                }
            } else {
                ReviewList(vm, state)
            }
        }
    }
}

@Composable
private fun GateCard(gate: HpGate, onRetry: () -> Unit) {
    val warn = warnColor()
    Card(Modifier.fillMaxWidth(), shape = HomeCardShape) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconBadge(if (gate is HpGate.Checking) Icons.Default.Search else Icons.Default.Lock, if (gate is HpGate.Checking) MaterialTheme.colorScheme.primary else warn, size = 44.dp)
                Text("Hall Pass", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            when (gate) {
                HpGate.Checking -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("Looking for the Hall Pass plugin…")
                }
                HpGate.NotInstalled -> Text(
                    "The Hall Pass plugin is not on this server. It finds which of your music is explicit and tags it, so playlists can leave it out. " +
                        "Install it from github.com/feerlessleadr/hall-pass-emby (or with the GitHub Repo Plugin Install plugin), restart Emby, then come back here.",
                )
                is HpGate.TooOld -> Text("Hall Pass ${gate.info.version} is too old for this tab: it needs ${HallPass.MIN_PLUGIN} or newer. Update the plugin from the Server tab.")
                is HpGate.Unreachable -> Text(gate.message)
                is HpGate.Ready -> Unit
            }
            if (gate !is HpGate.Checking) OutlinedButton(onClick = onRetry) { Text("Check again") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewList(vm: HallPassViewModel, state: HallPassState) {
    var confirmAlbum by remember { mutableStateOf<Pair<HpAlbum, String>?>(null) }
    var confirmScanAll by rememberSaveable { mutableStateOf(false) }
    val showAlbums = state.albumsMode && state.scope == null
    val shown = if (showAlbums) state.albums.size else state.rows.size

    PullToRefreshBox(isRefreshing = false, onRefresh = vm::load, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(12.dp)) {
            item { SummaryCard(state.status, vm, onScanAll = { confirmScanAll = true }) }
            item { Filters(vm, state) }
            if (state.scope != null) {
                item {
                    Card(Modifier.fillMaxWidth(), shape = HomeCardShape) {
                        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${state.scope.album.ifEmpty { "No album" }} · ${state.scope.artist.ifEmpty { "unknown artist" }}",
                                Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                            TextButton(onClick = vm::closeScope) { Text("Show all") }
                        }
                    }
                }
            }
            if (showAlbums) {
                items(state.albums, key = { "${it.artist}\u0000${it.album}" }) { album ->
                    AlbumCard(album, onOpen = { vm.openAlbum(album) }, onMark = { decision -> confirmAlbum = album to decision })
                }
            } else {
                items(state.rows, key = { it.id }) { row -> TrackCard(row, vm) }
            }
            item {
                when {
                    state.loading -> Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) }
                    shown == 0 -> Text(
                        if (state.status?.total == 0) "Nothing scanned yet. Press Scan now." else "Nothing here with these filters.",
                        Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    shown < state.total -> {
                        LaunchedEffect(shown) { vm.loadMore() }
                        Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) { TextButton(onClick = vm::loadMore) { Text("Show more ($shown of ${state.total})") } }
                    }
                    else -> Text("$shown ${if (showAlbums) "albums" else "tracks"}", Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    confirmAlbum?.let { (album, decision) ->
        val word = if (decision == "explicit") "explicit" else "clean"
        ConfirmDialog(
            title = "Mark the whole album $word?",
            message = "${album.matching} track(s) of \"${album.album.ifEmpty { "no album" }}\" by ${album.artist.ifEmpty { "unknown artist" }} will be marked $word. " +
                "Nothing is written to your files until you apply, and you can clear the decision afterwards.",
            confirm = "Mark $word",
            onDismiss = { confirmAlbum = null },
            onConfirm = { vm.decideAlbum(album, decision); confirmAlbum = null },
        )
    }
    if (confirmScanAll) {
        ConfirmDialog(
            title = "Scan every track again?",
            message = "Hall Pass looks at the whole music library again, even tracks it already knows. This can take a while on a big library. Your manual decisions are kept.",
            confirm = "Scan all",
            onDismiss = { confirmScanAll = false },
            onConfirm = { vm.scan(true); confirmScanAll = false },
        )
    }
    state.preview?.let { preview -> ApplyDialog(preview, state.status, onDismiss = vm::clearPreview, onApply = vm::apply) }
}

@Composable
private fun SummaryCard(status: HpStatus?, vm: HallPassViewModel, onScanAll: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = HomeCardShape) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Hall Pass", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (status == null) {
                Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            Text("${status.total} tracks looked at", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Explicit", "Review", "LikelyClean", "Clean", "Skipped").forEach { d ->
                    val n = status.count(d)
                    if (n > 0) Pill("${decisionLabel(d)} $n", decisionColor(d))
                }
            }
            if (status.dryRun) {
                Text("Dry run: scans only record decisions. Tags are written once dry run is switched off on the plugin's Emby page.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (status.scan.isRunning) {
                val fraction = if (status.scan.total > 0) status.scan.processed.toFloat() / status.scan.total else 0f
                LinearProgressIndicator(progress = { fraction }, Modifier.fillMaxWidth())
                Text("Scanning ${status.scan.processed} of ${status.scan.total}", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = vm::cancelScan) { Text("Stop scan") }
            } else if (status.write.isRunning) {
                val fraction = if (status.write.total > 0) status.write.processed.toFloat() / status.write.total else 0f
                LinearProgressIndicator(progress = { fraction }, Modifier.fillMaxWidth())
                Text(status.write.message.ifEmpty { "Writing tags…" }, style = MaterialTheme.typography.bodySmall)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.scan(false) }) { Text("Scan now") }
                    OutlinedButton(onClick = onScanAll) { Text("Scan all again") }
                    OutlinedButton(onClick = vm::loadPreview) { Text("Apply…") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Filters(vm: HallPassViewModel, state: HallPassState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HpFilter.entries.forEach { f ->
                FilterChip(selected = state.filter == f, onClick = { vm.setFilter(f) }, label = { Text(f.label) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !state.albumsMode, onClick = { vm.setAlbumsMode(false) }, label = { Text("Tracks") })
            FilterChip(selected = state.albumsMode, onClick = { vm.setAlbumsMode(true) }, label = { Text("Albums") })
        }
        OutlinedTextField(
            value = state.search,
            onValueChange = vm::setSearch,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Search artist, album or title") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        )
    }
}

@Composable
private fun TrackCard(row: HpRow, vm: HallPassViewModel) {
    var open by rememberSaveable(row.id) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().clickable { open = !open }, shape = HomeCardShape) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(row.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(listOf(row.artist, row.album).filter { it.isNotEmpty() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Pill(decisionLabel(row.decision), decisionColor(row.decision))
            }
            if (row.manual) Text("Your decision: ${row.override.lowercase()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            else if (row.reason.isNotEmpty()) Text(row.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (open) {
                if (row.evidence.isEmpty()) Text(if (row.skipped) "In a skipped genre." else "No evidence.", style = MaterialTheme.typography.bodySmall)
                row.evidence.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.decide(row, "explicit") }, enabled = !(row.manual && row.override == "Explicit")) { Text("Explicit") }
                OutlinedButton(onClick = { vm.decide(row, "clean") }, enabled = !(row.manual && row.override == "Clean")) { Text("Clean") }
                if (row.manual) TextButton(onClick = { vm.decide(row, "clearOverride") }) { Text("Clear") }
            }
        }
    }
}

@Composable
private fun AlbumCard(album: HpAlbum, onOpen: () -> Unit, onMark: (String) -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = HomeCardShape) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(album.album.ifEmpty { "(no album)" }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(album.artist.ifEmpty { "(unknown artist)" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                (if (album.matching == album.total) "${album.total} tracks" else "${album.matching} of ${album.total} tracks") + " · " +
                    album.byDecision.entries.joinToString(", ") { "${decisionLabel(it.key)} ${it.value}" },
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpen) { Text("Open") }
                Button(onClick = { onMark("explicit") }) { Text("All explicit") }
                OutlinedButton(onClick = { onMark("clean") }) { Text("All clean") }
            }
        }
    }
}

@Composable
private fun ApplyDialog(preview: HpPreview, status: HpStatus?, onDismiss: () -> Unit, onApply: () -> Unit) {
    val dry = status?.dryRun == true
    ConfirmDialog(
        title = if (dry) "Dry run is on" else "Write the tags?",
        message = when {
            dry -> "${preview.wouldChange} item(s) would change, but dry run is on, so nothing can be written. Switch dry run off in Settings on the plugin's Emby page when you are happy with the decisions."
            preview.wouldChange == 0 -> "Every track already has the right tag."
            else -> "${preview.wouldChange} item(s) would change" + (if (preview.rollUps > 0) " (${preview.rollUps} albums or artists)" else "") + ": " +
                preview.byTag.entries.joinToString(", ") { "${it.value} to ${it.key}" } + ". Every change is journalled and can be undone from the plugin's History tab."
        },
        confirm = if (dry || preview.wouldChange == 0) "OK" else "Write tags",
        onDismiss = onDismiss,
        onConfirm = if (dry || preview.wouldChange == 0) onDismiss else onApply,
    )
}
