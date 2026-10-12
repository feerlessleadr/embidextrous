package com.feerlessleadr.embidextrous.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feerlessleadr.embidextrous.data.ServerAdminParser
import com.feerlessleadr.embidextrous.home.AdminHomeViewModel
import com.feerlessleadr.embidextrous.home.HomeCardShape
import com.feerlessleadr.embidextrous.home.okColor
import com.feerlessleadr.embidextrous.home.warnColor
import com.feerlessleadr.embidextrous.ui.IconBadge
import com.feerlessleadr.embidextrous.ui.Pill
import com.feerlessleadr.embidextrous.watching.ConfirmDialog

/** A slim strip across the Server group: the Emby version, whether it is up to date, and Restart behind a menu (it always asks first). */
@Composable
fun ServerHeader(adminVm: AdminHomeViewModel, tools: ServerToolsViewModel) {
    val state by adminVm.state.collectAsStateWithLifecycle()
    val info = state.snapshot?.info
    var menu by remember { mutableStateOf(false) }
    var asking by remember { mutableStateOf(false) }
    val warn = warnColor()
    val ok = okColor()
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            if (info != null) "Emby ${info.version}" else "Emby",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (info != null) {
            if (info.updateAvailable) Pill("Update available", warn) else Pill("Up to date", ok)
            if (info.restartPending) Pill("Restart pending", warn)
        }
        Box(Modifier.weight(1f))
        Box {
            IconButton(onClick = { menu = true }, enabled = info != null) { Icon(Icons.Default.MoreVert, contentDescription = "Server actions") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(if (info?.canSelfRestart == false) "Restart Emby (not possible here)" else "Restart Emby…") },
                    enabled = info?.canSelfRestart == true,
                    leadingIcon = { Icon(Icons.Default.Refresh, null) },
                    onClick = { menu = false; asking = true },
                )
            }
        }
    }
    if (asking) {
        ConfirmDialog(
            title = "Restart Emby?",
            message = "Everyone watching is interrupted, and the server is offline for a minute or so. Hall Monitor and this app reconnect by themselves.",
            confirm = "Restart",
            onDismiss = { asking = false },
            onConfirm = { asking = false; tools.restart() },
        )
    }
}

/** The media libraries with how much is in each, and the buttons to scan for new files. */
@Composable
fun LibrariesScreen(vm: ServerToolsViewModel) {
    LaunchedEffect(vm) { vm.loadLibraries() }
    val state by vm.libraries.collectAsStateWithLifecycle()
    var scanAllAsk by remember { mutableStateOf(false) }
    var refreshAsk by remember { mutableStateOf<com.feerlessleadr.embidextrous.data.LibraryInfo?>(null) }
    ToolScreen(vm, onRefresh = vm::loadLibraries) {
        when {
            !state.loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            state.error != null && state.items.isEmpty() -> EmptyNote(Icons.Default.Warning, "Could not load the libraries", state.error.orEmpty())
            state.items.isEmpty() -> EmptyNote(Icons.Default.VideoLibrary, "No libraries", "Add libraries in the Emby dashboard.")
            else -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(12.dp)) {
                item {
                    Button(onClick = { scanAllAsk = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Text("  Scan all libraries")
                    }
                }
                items(state.items, key = { it.library.id }) { row ->
                    val lib = row.library
                    val (icon, noun) = when (lib.type) {
                        "movies" -> Icons.Default.Movie to ServerAdminParser.countFilter(lib.type).second
                        "tvshows" -> Icons.Default.Tv to ServerAdminParser.countFilter(lib.type).second
                        "music" -> Icons.Default.MusicNote to ServerAdminParser.countFilter(lib.type).second
                        else -> Icons.Default.Folder to ServerAdminParser.countFilter(lib.type).second
                    }
                    Card(Modifier.fillMaxWidth(), shape = HomeCardShape) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            IconBadge(icon, MaterialTheme.colorScheme.primary, size = 48.dp)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(lib.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    row.count?.let { "$it $noun" } ?: "Counting…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                if (lib.paths.isNotEmpty()) {
                                    Text(lib.paths.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            // Playlists and collections are made by Emby and the plugins, not scanned from folders.
                            if (lib.type != "boxsets" && lib.type != "playlists") OutlinedButton(onClick = { refreshAsk = lib }) { Text("Scan") }
                        }
                    }
                }
            }
        }
    }
    if (scanAllAsk) {
        ConfirmDialog(
            title = "Scan all libraries?",
            message = "Emby looks for new, changed and removed files in every library. On a big library this takes a while and uses the server's disk and processor.",
            confirm = "Scan",
            onDismiss = { scanAllAsk = false },
            onConfirm = { scanAllAsk = false; vm.scanAll() },
        )
    }
    refreshAsk?.let { lib ->
        ConfirmDialog(
            title = "Scan ${lib.name}?",
            message = "Emby looks for new, changed and removed files in this library and refreshes their details.",
            confirm = "Scan",
            onDismiss = { refreshAsk = null },
            onConfirm = { refreshAsk = null; vm.refresh(lib) },
        )
    }
}
