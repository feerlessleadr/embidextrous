package com.feerlessleadr.embidextrous.labelmaker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.feerlessleadr.embidextrous.data.LabelMaker
import com.feerlessleadr.embidextrous.data.LabelMakerParser
import com.feerlessleadr.embidextrous.data.LmAlbum
import com.feerlessleadr.embidextrous.data.LmCandidate
import com.feerlessleadr.embidextrous.data.LmDuplicateChoice
import com.feerlessleadr.embidextrous.data.LmGate
import com.feerlessleadr.embidextrous.data.LmPreview
import com.feerlessleadr.embidextrous.data.LmStatus
import com.feerlessleadr.embidextrous.home.HomeCardShape
import com.feerlessleadr.embidextrous.home.okColor
import com.feerlessleadr.embidextrous.home.warnColor
import com.feerlessleadr.embidextrous.ui.IconBadge
import com.feerlessleadr.embidextrous.ui.Pill
import com.feerlessleadr.embidextrous.watching.ConfirmDialog

@Composable
private fun stateColor(state: String): Color = when (state) {
    "Done", "Ready" -> okColor()
    "NeedsReview", "NoMatch", "Duplicate" -> warnColor()
    "Failed" -> MaterialTheme.colorScheme.error
    "Applying", "Matching", "Waiting" -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** One short sentence about what the person should do, from the plugin's status. Pure, so it is unit tested. */
internal fun lmHint(status: LmStatus): String {
    val review = status.count("NeedsReview")
    val ready = status.count("Ready")
    val blocking = status.problems.any { it.blocking }
    return when {
        blocking -> "A lane has a problem and is stopped. See below."
        status.paused -> "Label Maker is paused. Resume it to look at the inbox again."
        review > 0 -> "$review ${if (review == 1) "album needs" else "albums need"} your decision."
        ready > 0 && status.dryRun -> "$ready ${if (ready == 1) "album is" else "albums are"} Ready. Dry run is on, so nothing is filed yet."
        ready > 0 -> "$ready ${if (ready == 1) "album is" else "albums are"} Ready and will be filed on the next look."
        else -> "Nothing needs you right now."
    }
}

/** "3 minutes ago" for the plugin's last-look time (an ISO time in UTC), or an empty string when it has not looked yet or the time is unreadable. */
internal fun lmAgo(utc: String, nowMillis: Long): String {
    if (utc.isBlank()) return ""
    val then = runCatching { java.time.Instant.parse(utc).toEpochMilli() }.getOrNull() ?: return ""
    val seconds = ((nowMillis - then) / 1000).coerceAtLeast(0)
    return when {
        seconds < 90 -> "just now"
        seconds < 3600 -> "${seconds / 60} minutes ago"
        seconds < 86_400 -> "${seconds / 3600} ${if (seconds / 3600 == 1L) "hour" else "hours"} ago"
        else -> "${seconds / 86_400} ${if (seconds / 86_400 == 1L) "day" else "days"} ago"
    }
}

/** The Library group's Label Maker tab. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabelMakerTab(vm: LabelMakerViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(vm) { vm.load() }
    LaunchedEffect(vm) { vm.live() }
    val gate = state.gate

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, containerColor = Color.Transparent) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (gate !is LmGate.Ready) {
                PullToRefreshBox(isRefreshing = false, onRefresh = vm::load, modifier = Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp)) { item { GateCard(gate, vm::load) } }
                }
            } else {
                AlbumList(vm, state)
            }
        }
    }
    state.detail?.let { AlbumSheet(vm, it) }
}

@Composable
private fun GateCard(gate: LmGate, onRetry: () -> Unit) {
    val warn = warnColor()
    Card(Modifier.fillMaxWidth(), shape = HomeCardShape) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconBadge(if (gate is LmGate.Checking) Icons.Default.Search else Icons.Default.Lock, if (gate is LmGate.Checking) MaterialTheme.colorScheme.primary else warn, size = 44.dp)
                Text("Label Maker", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            when (gate) {
                LmGate.Checking -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("Looking for the Label Maker plugin…")
                }
                LmGate.NotInstalled -> Text(
                    "The Label Maker plugin is not on this server. It matches new music downloads on MusicBrainz, tags them, and files them into your library. " +
                        "Install it from github.com/feerlessleadr/label-maker-emby (or with the GitHub Repo Plugin Install plugin), restart Emby, then come back here.",
                )
                is LmGate.TooOld -> Text("Label Maker ${gate.info.version.ifEmpty { "(unknown version)" }} is too old for this tab: it needs ${LabelMaker.MIN_PLUGIN} or newer. Update the plugin from the Server tab.")
                is LmGate.Unreachable -> Text(gate.message)
                is LmGate.Ready -> Unit
            }
            if (gate !is LmGate.Checking) OutlinedButton(onClick = onRetry) { Text("Check again") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlbumList(vm: LabelMakerViewModel, state: LabelMakerState) {
    var confirmDryRunOff by rememberSaveable { mutableStateOf(false) }
    PullToRefreshBox(isRefreshing = false, onRefresh = vm::load, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(12.dp)) {
            item { StatusCard(state.status, vm, onDryRunOff = { confirmDryRunOff = true }) }
            item { Filters(vm, state) }
            items(state.albums, key = { it.id }) { album -> AlbumCard(album, onOpen = { vm.openAlbum(album) }) }
            item {
                when {
                    state.loading -> Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) }
                    state.albums.isEmpty() -> Text(
                        if (state.filter == LmFilter.Review && state.search.isBlank()) "Nothing is waiting for a decision." else "Nothing here with these filters.",
                        Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.albums.size < state.total -> {
                        LaunchedEffect(state.albums.size) { vm.loadMore() }
                        Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) { TextButton(onClick = vm::loadMore) { Text("Show more (${state.albums.size} of ${state.total})") } }
                    }
                    else -> Text("${state.albums.size} ${if (state.albums.size == 1) "album" else "albums"}", Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    if (confirmDryRunOff) {
        ConfirmDialog(
            title = "Turn dry run off?",
            message = "Label Maker will tag and file the albums that are Ready, and every album that matches well enough from now on, without asking you first. " +
                "Every filed album can be undone from its page. A lane can still be set to always ask first on the plugin's Emby page.",
            confirm = "Turn off dry run",
            onDismiss = { confirmDryRunOff = false },
            onConfirm = { vm.setDryRun(false); confirmDryRunOff = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusCard(status: LmStatus?, vm: LabelMakerViewModel, onDryRunOff: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = HomeCardShape) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Label Maker", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (status == null) {
                Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            Text(lmHint(status), style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("NeedsReview", "Ready", "NoMatch", "Failed", "Waiting", "Applying", "Duplicate").forEach { s ->
                    val n = status.count(s)
                    if (n > 0) Pill("${LabelMaker.stateLabel(s)} $n", stateColor(s))
                }
            }
            val ago = lmAgo(status.lastPassUtc, System.currentTimeMillis())
            Text(
                if (ago.isEmpty()) "Has not looked at the inbox yet." else "Last looked at the inbox $ago.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (status.musicBrainzTrouble) {
                Text("MusicBrainz has not been answering for a while, so new albums wait and are tried again.", style = MaterialTheme.typography.bodySmall, color = warnColor())
            }
            status.lanes.forEach { lane ->
                lane.problems.forEach { p ->
                    Text(
                        "${lane.name}: ${p.message}" + if (p.blocking) " (stopped until fixed)" else "",
                        style = MaterialTheme.typography.bodySmall, color = if (p.blocking) MaterialTheme.colorScheme.error else warnColor(),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Dry run", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (status.dryRun) "On: Label Maker only reports what it would do." else "Off: matching albums are filed automatically.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = status.dryRun, onCheckedChange = { on -> if (on) vm.setDryRun(true) else onDryRunOff() })
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::checkNow, enabled = !status.paused) { Text("Check inbox now") }
                OutlinedButton(onClick = { vm.setPaused(!status.paused) }) { Text(if (status.paused) "Resume" else "Pause") }
            }
        }
    }
}

@Composable
private fun Filters(vm: LabelMakerViewModel, state: LabelMakerState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LmFilter.entries.forEach { f ->
                val n = f.count(state.status)
                FilterChip(selected = state.filter == f, onClick = { vm.setFilter(f) }, label = { Text(if (n != null && n > 0 && f != LmFilter.All) "${f.label} $n" else f.label) })
            }
        }
        val lanes = state.status?.lanes.orEmpty()
        if (lanes.size > 1) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = state.laneId.isEmpty(), onClick = { vm.setLane("") }, label = { Text("All lanes") })
                lanes.forEach { lane -> FilterChip(selected = state.laneId == lane.id, onClick = { vm.setLane(lane.id) }, label = { Text(lane.name) }) }
            }
        }
        OutlinedTextField(
            value = state.search,
            onValueChange = vm::setSearch,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Search artist, album or folder") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        )
    }
}

private fun titleOf(album: LmAlbum): String = (album.artist.ifEmpty { "Unknown artist" }) + " – " + (album.album.ifEmpty { "Unknown album" })

@Composable
private fun AlbumCard(album: LmAlbum, onOpen: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onOpen), shape = HomeCardShape) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(titleOf(album), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Pill(if (album.discarded) "In the trash" else LabelMaker.stateLabel(album.state), if (album.discarded) MaterialTheme.colorScheme.onSurfaceVariant else stateColor(album.state))
            }
            if (album.reason.isNotEmpty()) Text(album.reason, style = MaterialTheme.typography.bodySmall)
            if (album.bestTitle.isNotEmpty() && album.state in setOf("Ready", "NeedsReview", "Duplicate")) {
                Text(
                    "Best match: ${album.bestTitle}" + (if (album.bestArtist.isNotEmpty()) " by ${album.bestArtist}" else "") + " (${LabelMakerParser.percent(album.score)})",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                listOf(album.laneName, "${album.files} ${if (album.files == 1) "file" else "files"}", if (album.kind == "Single") "single" else "").filter { it.isNotEmpty() }.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** States in which an empty list of editions really means that none fits (a waiting album has simply not been matched yet). */
private val NO_FIT_STATES = setOf("NeedsReview", "NoMatch", "Failed", "Duplicate")

/** An action waiting for the person's yes. */
private data class PendingAction(val action: String, val title: String, val message: String, val confirm: String)

private val KEEP_TAGS = PendingAction("KeepTags", "Keep your tags and file it?", "The album is filed using the tags already in its files, unchanged. This is done now, even when dry run is on. You can undo it afterwards.", "File it")
private val DISCARD = PendingAction("Discard", "Move to the trash folder?", "The files move to the _Trash folder inside their inbox. Nothing is deleted, and you can restore the album from here.", "Move to trash")
private val UNDO = PendingAction("Undo", "Put it back in the inbox?", "Every file goes back to the inbox with its old tags and names.", "Put back")
private val SPLIT = PendingAction("Split", "Split by album?", "Each album in this folder gets a folder of its own inside it, and then each is looked at on its own.", "Split")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AlbumSheet(vm: LabelMakerViewModel, d: LmDetailState) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pending by remember { mutableStateOf<PendingAction?>(null) }
    val album = d.album
    val busy = LabelMaker.isBusy(album.state) || d.busy
    val canChoose = album.state != "Done" && !album.discarded && !LabelMaker.isBusy(album.state)

    // The tab's own snackbar sits behind the sheet, so the sheet shows the messages itself.
    val sheetSnackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.messages.collect { sheetSnackbar.showSnackbar(it) } }

    ModalBottomSheet(onDismissRequest = vm::closeAlbum, sheetState = sheetState) {
      Box(Modifier.fillMaxWidth()) {
        LazyColumn(
            Modifier.fillMaxWidth().navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(titleOf(album), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Pill(if (album.discarded) "In the trash" else LabelMaker.stateLabel(album.state), if (album.discarded) MaterialTheme.colorScheme.onSurfaceVariant else stateColor(album.state))
                        if (album.held) Pill("Left alone until you check again", MaterialTheme.colorScheme.onSurfaceVariant)
                        if (d.loading || d.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                    if (album.reason.isNotEmpty()) Text(album.reason, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        listOf(album.laneName, album.folder, "${album.files} ${if (album.files == 1) "file" else "files"}").filter { it.isNotEmpty() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (album.destination.isNotEmpty()) Text("Filed in: ${album.destination}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        album.discarded -> Button(onClick = { vm.action("Restore") }, enabled = !busy) { Text("Restore from the trash") }
                        album.state == "Done" -> OutlinedButton(onClick = { pending = UNDO }, enabled = !busy) { Text("Undo") }
                        LabelMaker.isBusy(album.state) -> Text("Label Maker is working on this album.", style = MaterialTheme.typography.bodySmall)
                        else -> {
                            if (album.bestTitle.isNotEmpty() && album.bestReleaseId.isNotEmpty()) {
                                Button(onClick = { vm.preview(album.bestReleaseId) }, enabled = !busy) { Text(if (album.state == "Duplicate") "See what is in the library" else "Use the best match") }
                            }
                            OutlinedButton(onClick = { pending = KEEP_TAGS }, enabled = !busy) { Text("Keep my tags") }
                            if (LabelMaker.canSplit(album)) OutlinedButton(onClick = { pending = SPLIT }, enabled = !busy) { Text("Split by album") }
                            OutlinedButton(onClick = { vm.action("Skip") }, enabled = !busy) { Text("Skip for now") }
                            OutlinedButton(onClick = { vm.action("Recheck") }, enabled = !busy) { Text("Check again") }
                            OutlinedButton(onClick = { pending = DISCARD }, enabled = !busy) { Text("Discard") }
                        }
                    }
                }
            }
            d.preview?.let { p -> item { PreviewCard(p, d.previewChoice, busy, onChoice = { vm.preview(p.releaseId, it) }, onApply = vm::applyPreview, onClose = vm::clearPreview) } }
            if (canChoose) {
                val shown = d.found ?: d.detail?.candidates.orEmpty()
                item {
                    Text(if (d.found != null) "What was found" else "Editions Label Maker found", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (d.found != null && d.foundMessage.isNotEmpty()) Text(d.foundMessage, style = MaterialTheme.typography.bodySmall)
                    if (shown.isEmpty() && !d.loading) {
                        Text(
                            if (album.state in NO_FIT_STATES || d.found != null) "No edition fits. Search by hand, paste a MusicBrainz link, or keep your own tags."
                            else "Label Maker has not matched this album yet. Check back in a moment, or search by hand.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (d.found != null) TextButton(onClick = vm::clearFound) { Text("Back to the first list") }
                }
                items(shown) { c -> CandidateCard(c, enabled = !busy, onUse = { vm.preview(c.releaseId) }) }
                item { SearchBox(album, d.busy, onSearch = vm::search, onResolve = vm::resolve) }
            }
            d.detail?.let { detail ->
                if (detail.files.isNotEmpty()) {
                    item { FilesSection(detail.files.map { f -> listOf(f.disc.takeIf { it > 0 }?.let { "$it-" }.orEmpty() + (f.track.takeIf { it > 0 }?.toString()?.padStart(2, '0') ?: "  "), f.error.ifEmpty { f.title.ifEmpty { f.file } }, f.artist).filter { it.isNotBlank() }.joinToString("  ") }) }
                }
                if (detail.notes.isNotEmpty()) item { NotesSection(detail.notes) }
            }
            item { Box(Modifier.padding(bottom = 16.dp)) }
        }
        SnackbarHost(sheetSnackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
      }
    }

    pending?.let { p ->
        ConfirmDialog(title = p.title, message = p.message, confirm = p.confirm, onDismiss = { pending = null }, onConfirm = { vm.action(p.action); pending = null })
    }
}

@Composable
private fun CandidateCard(c: LmCandidate, enabled: Boolean, onUse: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = HomeCardShape) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(c.title.ifEmpty { "(no title)" }, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Pill("Fit ${LabelMakerParser.percent(c.score)}", if (c.score >= 0.92) okColor() else warnColor())
            }
            Text(
                listOf(c.artist, c.country, c.date, c.releaseId.take(8)).filter { it.isNotEmpty() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (c.weakFiles > 0) Text("${c.weakFiles} ${if (c.weakFiles == 1) "file fits" else "files fit"} only loosely.", style = MaterialTheme.typography.bodySmall, color = warnColor())
            OutlinedButton(onClick = onUse, enabled = enabled) { Text("See what this would do") }
        }
    }
}

@Composable
private fun SearchBox(album: LmAlbum, busy: Boolean, onSearch: (String, String) -> Unit, onResolve: (String) -> Unit) {
    var artist by rememberSaveable(album.id) { mutableStateOf(album.artist) }
    var title by rememberSaveable(album.id) { mutableStateOf(album.album) }
    var link by rememberSaveable(album.id) { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Search by hand", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        OutlinedTextField(artist, { artist = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Artist") })
        OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Album") })
        Button(onClick = { onSearch(artist, title) }, enabled = !busy && (artist.isNotBlank() || title.isNotBlank())) { Text("Search MusicBrainz") }
        OutlinedTextField(link, { link = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("MusicBrainz link or id") }, supportingText = { Text("A release or release group link works.") })
        OutlinedButton(onClick = { onResolve(link) }, enabled = !busy && link.isNotBlank()) { Text("Look it up") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PreviewCard(p: LmPreview, choice: LmDuplicateChoice, busy: Boolean, onChoice: (LmDuplicateChoice) -> Unit, onApply: () -> Unit, onClose: () -> Unit) {
    var all by rememberSaveable(p.releaseId) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth(), shape = HomeCardShape) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("What this would do", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            val edition = listOf(p.country, p.date).filter { it.isNotEmpty() }.joinToString(" · ")
            Text(
                listOf(p.title, p.artist).filter { it.isNotEmpty() }.joinToString(" by ") + if (edition.isEmpty()) "" else " ($edition)",
                style = MaterialTheme.typography.titleSmall,
            )
            Text("Fit ${LabelMakerParser.percent(p.score)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (p.message.isNotEmpty()) Text(p.message, style = MaterialTheme.typography.bodySmall)
            if (p.destination.isNotEmpty()) Text("It would be filed in: ${p.destination}", style = MaterialTheme.typography.bodySmall)
            p.problems.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            p.notes.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (p.isDuplicate || choice != LmDuplicateChoice.Ask) {
                Text(
                    "${p.duplicateCount} ${if (p.duplicateCount == 1) "file is" else "files are"} already in the library. What should happen to them?",
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LmDuplicateChoice.entries.filter { it != LmDuplicateChoice.Ask }.forEach { c ->
                        FilterChip(selected = choice == c, onClick = { onChoice(c) }, enabled = !busy, label = { Text(c.label) })
                    }
                }
                Text(
                    when (choice) {
                        LmDuplicateChoice.Ask -> "Pick one to see the result. Nothing is overwritten unless you choose Replace, and then the old files are kept in the inbox's _Replaced folder."
                        LmDuplicateChoice.KeepBoth -> "The new copies are numbered \"(2)\" and sit beside the old ones."
                        LmDuplicateChoice.Replace -> "The old files are saved in the inbox's _Replaced folder, and the new ones take their place."
                        LmDuplicateChoice.SkipExisting -> "Only tracks the library does not have are filed. The others are set aside in the inbox's _Duplicates folder."
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val files = if (all) p.files else p.files.take(6)
            files.forEach { f ->
                Text(
                    (if (f.disc > 1) "${f.disc}-" else "") + f.position.toString().padStart(2, '0') + "  " + f.title.ifEmpty { f.source } +
                        (if (f.weak) "  (loose fit)" else "") + (if (f.existing) "  (already in the library)" else ""),
                    style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    color = if (f.weak) warnColor() else MaterialTheme.colorScheme.onSurface,
                )
            }
            if (p.files.size > 6) TextButton(onClick = { all = !all }) { Text(if (all) "Show fewer" else "Show all ${p.files.size} files") }
            Text("Your choice here is carried out even when dry run is on. Every filed album can be undone.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onApply, enabled = p.canApply && !busy) { Text("File this album") }
                OutlinedButton(onClick = onClose) { Text("Close") }
            }
        }
    }
}

@Composable
private fun FilesSection(lines: List<String>) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column {
        TextButton(onClick = { open = !open }) { Text(if (open) "Hide your files" else "Your files (${lines.size})") }
        if (open) lines.forEach { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
private fun NotesSection(notes: List<String>) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column {
        TextButton(onClick = { open = !open }) { Text(if (open) "Hide how Label Maker searched" else "How Label Maker searched") }
        if (open) notes.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
