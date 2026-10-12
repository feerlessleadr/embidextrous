package com.feerlessleadr.embidextrous.watching

import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.feerlessleadr.embidextrous.AppContainer
import com.feerlessleadr.embidextrous.data.TrGate
import com.feerlessleadr.embidextrous.data.TrPlay
import com.feerlessleadr.embidextrous.data.TrStream
import com.feerlessleadr.embidextrous.data.TrUser
import com.feerlessleadr.embidextrous.data.TrViolation
import com.feerlessleadr.embidextrous.data.TrWindow
import com.feerlessleadr.embidextrous.home.HomeCardShape
import com.feerlessleadr.embidextrous.home.okColor
import com.feerlessleadr.embidextrous.home.warnColor
import com.feerlessleadr.embidextrous.ui.IconBadge
import com.feerlessleadr.embidextrous.ui.Pill

private enum class TrSection(val label: String) { Now("Now"), History("History"), Violations("Violations"), People("People") }

private fun ago(ms: Long?, now: Long): String = when {
    ms == null -> ""
    now - ms < 60_000L -> "just now"
    else -> DateUtils.getRelativeTimeSpanString(ms, now, DateUtils.MINUTE_IN_MILLIS).toString()
}

private fun hours(ms: Long): String {
    val minutes = ms / 60_000
    return if (minutes < 60) "$minutes min" else "%d h %02d min".format(minutes / 60, minutes % 60)
}

/** The Watching group's Activity tab: what Tracearr has seen (now, history, violations, people). Read only. */
@Composable
fun TracearrTab(vm: TracearrViewModel, container: AppContainer) {
    val connection by container.tracearr.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    // Tracearr pushes live events while this tab is on screen; it is asked again only while that push is down.
    LaunchedEffect(connection) { owner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) { vm.keepFresh() } }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, containerColor = Color.Transparent) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val gate = state.gate) {
                TrGate.NotConfigured -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { IntroCard() }
                    item { TracearrConnectionCard(container) }
                }
                TrGate.Checking -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                is TrGate.Ready -> TracearrReady(vm, container, gate)
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { ProblemCard(gate, onRetry = vm::load) }
                    item { TracearrConnectionCard(container) }
                }
            }
        }
    }
}

@Composable
private fun IntroCard() {
    Card(Modifier.fillMaxWidth(), shape = HomeCardShape) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconBadge(Icons.Default.Insights, MaterialTheme.colorScheme.primary, size = 44.dp)
                Text("Activity from Tracearr", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Text(
                "Tracearr watches your media server and keeps the history: what each person watched, for how long, on what, and any rule it flagged. Connect it here to see that next to Hall Monitor.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProblemCard(gate: TrGate, onRetry: () -> Unit) {
    val warn = warnColor()
    Card(Modifier.fillMaxWidth(), shape = HomeCardShape) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconBadge(Icons.Default.Warning, warn, size = 44.dp)
                Text(
                    when (gate) {
                        TrGate.KeyRejected -> "Tracearr did not accept the key"
                        is TrGate.RateLimited -> "Tracearr is limiting requests"
                        else -> "Could not reach Tracearr"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                when (gate) {
                    TrGate.KeyRejected -> "Make a new key in Tracearr (Settings, Data & API) and replace it below."
                    is TrGate.RateLimited -> "Each key may make a limited number of requests a minute. Wait a moment and try again."
                    is TrGate.Unreachable -> gate.message + " Tracearr may only be reachable over your VPN."
                    else -> ""
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onRetry) { Text("Try again") }
        }
    }
}

/** The address and key of the Tracearr server: set up, replace or disconnect. Used on the Activity tab and in Settings. */
@Composable
fun TracearrConnectionCard(container: AppContainer, showTitle: Boolean = false) {
    val connection by container.tracearr.collectAsStateWithLifecycle()
    var address by rememberSaveable(connection?.url) { mutableStateOf(connection?.url.orEmpty()) }
    var key by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf<String?>(null) }
    Card(Modifier.fillMaxWidth(), shape = HomeCardShape) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (showTitle) Text("Tracearr", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                if (connection == null) "Enter your Tracearr address and an API key (in Tracearr: Settings, Data & API). The key is stored encrypted on this phone and only ever sent to that address."
                else "Connected. Change the address or paste a new key to replace them.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = address,
                onValueChange = { address = it; problem = null },
                label = { Text("Tracearr address") },
                placeholder = { Text("https://tracearr.example.com") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = key,
                onValueChange = { key = it; problem = null },
                label = { Text(if (connection == null) "API key" else "New API key") },
                placeholder = { Text("trr_pub_…") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            problem?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        // A blank key while connected keeps the saved key and only changes the address.
                        val useKey = key.ifBlank { container.tracearr.value?.key.orEmpty() }
                        problem = container.saveTracearr(address, useKey)
                        if (problem == null) key = ""
                    },
                    enabled = address.isNotBlank() && (key.isNotBlank() || connection != null),
                ) { Text(if (connection == null) "Connect" else "Save") }
                if (connection != null) OutlinedButton(onClick = { container.clearTracearr(); key = ""; address = "" }) { Text("Disconnect") }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------

@Composable
private fun TracearrReady(vm: TracearrViewModel, container: AppContainer, gate: TrGate.Ready) {
    val state by vm.state.collectAsStateWithLifecycle()
    var section by rememberSaveable { mutableStateOf(TrSection.Now) }
    var menu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    val ok = okColor()
    val warn = warnColor()
    val now = System.currentTimeMillis()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Tracearr ${gate.tracearrVersion}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            gate.servers.forEach { s -> Pill(s.name + if (s.status == "up") "" else " · ${s.status}", if (s.status == "up") ok else warn) }
            Box(Modifier.weight(1f))
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Tracearr options") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Change connection…") }, onClick = { menu = false; editing = !editing })
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TrSection.entries.forEach { s -> FilterChip(selected = section == s, onClick = { section = s; editing = false }, label = { Text(s.label) }) }
        }
        Box(Modifier.weight(1f)) {
            if (editing) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp)) { item { TracearrConnectionCard(container) } }
            } else {
                PullToRefreshBox(isRefreshing = false, onRefresh = vm::load, modifier = Modifier.fillMaxSize()) {
                    when (section) {
                        TrSection.Now -> NowSection(vm, state, now)
                        TrSection.History -> HistorySection(vm, state, now)
                        TrSection.Violations -> ViolationsSection(vm, state, now)
                        TrSection.People -> PeopleSection(state)
                    }
                }
            }
        }
    }
}

@Composable
private fun Poster(vm: TracearrViewModel, path: String?, modifier: Modifier = Modifier) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, path) { value = if (path != null) vm.poster(path) else null }
    val shape = RoundedCornerShape(10.dp)
    val b = bitmap
    if (b != null) {
        Image(b.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier.clip(shape))
    } else {
        Box(modifier.clip(shape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Empty(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, text: String) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
        IconBadge(icon, MaterialTheme.colorScheme.primary, size = 80.dp)
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---- Now ------------------------------------------------------------------------------------------------

@Composable
private fun NowSection(vm: TracearrViewModel, state: TracearrState, now: Long) {
    val streams = state.streams
    when {
        streams == null && state.streamsError != null -> Empty(Icons.Default.Warning, "Could not load streams", state.streamsError)
        streams == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        streams.streams.isEmpty() -> Empty(Icons.Default.PlayArrow, "Nothing is playing", "Streams Tracearr sees appear here. Pull down to look again.")
        else -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(12.dp)) {
            item {
                val s = streams.summary
                Text(
                    "${s.total} playing · ${s.directPlays} direct play, ${s.directStreams} direct stream, ${s.transcodes} transcoding" + if (s.totalBitrate.isNotBlank() && s.totalBitrate != "—") " · ${s.totalBitrate}" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            items(streams.streams, key = { it.id }) { StreamCard(vm, it) }
        }
    }
}

@Composable
private fun StreamCard(vm: TracearrViewModel, s: TrStream) {
    val warn = warnColor()
    Card(Modifier.fillMaxWidth(), shape = HomeCardShape) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Poster(vm, s.posterPath, Modifier.size(width = 56.dp, height = 84.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(s.title.ifEmpty { "Playing" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    s.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Text(s.username, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(listOfNotNull(s.device, s.platform).distinct().joinToString(" · ") + (s.resolution?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Pill(if (s.paused) "Paused" else if (s.transcode) "Transcoding" else "Direct", if (s.paused || s.transcode) warn else okColor())
            }
            val total = s.durationMs
            if (total != null && total > 0) LinearProgressIndicator(progress = { (s.progressMs.toFloat() / total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        }
    }
}

// ---- History --------------------------------------------------------------------------------------------

@Composable
private fun HistorySection(vm: TracearrViewModel, state: TracearrState, now: Long) {
    val h = state.history
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(12.dp)) {
        if (state.users.size > 1) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    FilterChip(selected = state.person == null, onClick = { vm.choosePerson(null) }, label = { Text("Everyone") })
                    state.users.take(6).forEach { u -> FilterChip(selected = state.person == u.id, onClick = { vm.choosePerson(u.id) }, label = { Text(u.username, maxLines = 1) }) }
                }
            }
        }
        when {
            !h.loaded && h.error == null -> item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            h.error != null && h.items.isEmpty() -> item { Text(h.error, color = warnColor(), modifier = Modifier.padding(8.dp)) }
            h.items.isEmpty() -> item { Text("Nothing watched yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp)) }
            else -> {
                items(h.items, key = { it.id }) { PlayRow(vm, it, now) }
                if (h.nextCursor != null) item {
                    TextButton(onClick = vm::loadMoreHistory, enabled = !h.loadingMore, modifier = Modifier.fillMaxWidth()) { Text(if (h.loadingMore) "Loading…" else "Load older plays") }
                }
            }
        }
    }
}

@Composable
private fun PlayRow(vm: TracearrViewModel, p: TrPlay, now: Long) {
    val ok = okColor()
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Poster(vm, p.posterPath, Modifier.size(width = 48.dp, height = 72.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(p.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            p.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            Text(
                listOf(p.username, hours(p.watchedMs) + (p.percentComplete?.let { " · ${it.toInt()}%" } ?: ""), ago(p.startedMs, now)).filter { it.isNotEmpty() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (p.watched) Pill("Watched", ok)
    }
}

// ---- Violations -----------------------------------------------------------------------------------------

@Composable
private fun ViolationsSection(vm: TracearrViewModel, state: TracearrState, now: Long) {
    val v = state.violations
    when {
        !v.loaded && v.error == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        v.error != null && v.items.isEmpty() -> Empty(Icons.Default.Warning, "Could not load violations", v.error)
        v.items.isEmpty() -> Empty(Icons.Default.Report, "No violations", "When one of Tracearr's rules fires (too many streams, an unusual place) it is listed here.")
        else -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(12.dp)) {
            items(v.items, key = { it.id }) { ViolationCard(it, now) }
            if (v.nextCursor != null) item { TextButton(onClick = vm::loadMoreViolations, enabled = !v.loadingMore, modifier = Modifier.fillMaxWidth()) { Text(if (v.loadingMore) "Loading…" else "Load older violations") } }
        }
    }
}

@Composable
private fun ViolationCard(v: TrViolation, now: Long) {
    val warn = warnColor()
    val color = when (v.severity) {
        "high" -> MaterialTheme.colorScheme.error
        "warning" -> warn
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(Modifier.fillMaxWidth(), shape = HomeCardShape) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconBadge(Icons.Default.Report, color, size = 40.dp)
                Column(Modifier.weight(1f)) {
                    Text(v.ruleName.ifEmpty { "Rule" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(v.username + " · " + ago(v.createdMs, now), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Pill(v.severity.replaceFirstChar { it.uppercase() }, color)
            }
            val detail = listOfNotNull(v.mediaTitle, v.ipAddress, v.serverName.takeIf { it.isNotEmpty() }).joinToString(" · ")
            if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodyMedium)
            Pill(if (v.acknowledged) "Acknowledged in Tracearr" else "Waiting for you in Tracearr", if (v.acknowledged) okColor() else warn)
        }
    }
}

// ---- People ---------------------------------------------------------------------------------------------

@Composable
private fun PeopleSection(state: TracearrState) {
    when {
        !state.usersLoaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        state.users.isEmpty() -> Empty(Icons.Default.Groups, "No people yet", "Tracearr lists the people who have watched.")
        else -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(12.dp)) {
            items(state.users, key = { it.id }) { u -> PersonCard(u, state.stats[u.id]) }
        }
    }
}

@Composable
private fun PersonCard(user: TrUser, stats: com.feerlessleadr.embidextrous.data.TrUserStats?) {
    Card(Modifier.fillMaxWidth(), shape = HomeCardShape) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                    Text(user.username.take(1).uppercase(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
                Column(Modifier.weight(1f)) {
                    Text(user.username, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(user.accounts.filter { !it.removed }.joinToString(", ") { it.serverType.replaceFirstChar { c -> c.uppercase() } }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (stats == null) {
                Text("Loading numbers…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Window("7 days", stats.last7, Modifier.weight(1f))
                    Window("30 days", stats.last30, Modifier.weight(1f))
                    Window("All time", stats.allTime, Modifier.weight(1f))
                }
                if (stats.topGenres.isNotEmpty()) {
                    Text("Favourite: " + stats.topGenres.take(3).joinToString(", ") { it.first }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun Window(label: String, w: TrWindow, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(hours(w.watchTimeMs), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text("${w.plays} play${if (w.plays == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
