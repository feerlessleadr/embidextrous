package com.feerlessleadr.embidextrous.home

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feerlessleadr.embidextrous.data.PluginChecks
import com.feerlessleadr.embidextrous.ui.IconBadge
import com.feerlessleadr.embidextrous.ui.Pill

/** A card with an icon header. [onClick] makes the whole header a way into the matching screen. */
@Composable
private fun AdminCard(title: String, icon: ImageVector, onClick: (() -> Unit)? = null, trailing: @Composable () -> Unit = {}, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = HomeCardShape) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.fillMaxWidth().let { if (onClick != null) it.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick) else it },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconBadge(icon, MaterialTheme.colorScheme.primary, size = 44.dp)
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                trailing()
                if (onClick != null) Icon(Icons.Default.ChevronRight, contentDescription = "Open", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
        }
    }
}

/** A number with a caption and a small icon, in a soft rounded tile. Tiles sit side by side in a row. */
@Composable
private fun StatTile(icon: ImageVector, value: String, label: String, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.primary) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** One line: a small coloured icon, a title and, under it, an optional explanation. */
@Composable
private fun StatusRow(icon: ImageVector, tint: Color, title: String, subtitle: String? = null, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconBadge(icon, tint, size = 34.dp)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

/** Names for a sentence: the first few, then "and N more". */
private fun names(list: List<String>, max: Int = 3): String =
    if (list.size <= max) list.joinToString(", ") else list.take(max).joinToString(", ") + " and ${list.size - max} more"

private fun ago(epochMs: Long, nowMs: Long): String =
    DateUtils.getRelativeTimeSpanString(epochMs, nowMs, DateUtils.MINUTE_IN_MILLIS).toString()

/** How the smart lists are doing: counts, what is wrong, the first few lists, and Refresh all. */
@Composable
fun SmartListsCard(admin: AdminHome, nowMs: Long) {
    val state by admin.lists.state.collectAsStateWithLifecycle()
    val problem by admin.problem.collectAsStateWithLifecycle()
    val warn = warnColor()
    val ok = okColor()
    val snapshot = AdminModel.lists(state.lists, state.status)
    AdminCard("Smart lists", Icons.AutoMirrored.Filled.PlaylistPlay, onClick = admin.onOpenLists) {
        when {
            problem != null -> StatusRow(Icons.Default.Warning, warn, "Smart Lists is not available", problem)
            state.loading -> Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.error != null -> StatusRow(Icons.Default.Warning, warn, "Could not load the lists", state.error)
            snapshot.total == 0 -> StatusRow(
                Icons.AutoMirrored.Filled.PlaylistPlay, MaterialTheme.colorScheme.primary, "No smart lists yet",
                "Open Smart Lists to make your first one.",
            )
            else -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    StatTile(Icons.AutoMirrored.Filled.PlaylistPlay, snapshot.total.toString(), if (snapshot.total == 1) "list" else "lists", Modifier.weight(1f))
                    StatTile(Icons.Default.CheckCircle, snapshot.enabled.toString(), "switched on", Modifier.weight(1f), tint = ok)
                    StatTile(Icons.Default.VideoLibrary, snapshot.totalItems.toString(), "items", Modifier.weight(1f))
                }
                when {
                    snapshot.refreshing > 0 -> {
                        StatusRow(Icons.Default.Sync, MaterialTheme.colorScheme.primary, if (snapshot.refreshing == 1) "Refreshing 1 list…" else "Refreshing ${snapshot.refreshing} lists…")
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    snapshot.failed.isNotEmpty() -> StatusRow(Icons.Default.Warning, warn, "Last refresh failed", names(snapshot.failed))
                    else -> StatusRow(
                        Icons.Default.CheckCircle, ok, "Every refresh worked",
                        snapshot.lastRefreshed?.let { "Last one " + ago(it.toEpochMilli(), nowMs) },
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (row in snapshot.rows) ListPreviewRow(row, nowMs)
                    if (snapshot.total > snapshot.rows.size) {
                        Text(
                            "and ${snapshot.total - snapshot.rows.size} more",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = admin.lists::refreshAll,
                enabled = problem == null && snapshot.total > 0 && snapshot.refreshing == 0,
                modifier = Modifier.weight(1f),
            ) { Text("Refresh all") }
            OutlinedButton(onClick = admin.onOpenLists, modifier = Modifier.weight(1f)) { Text("Open lists") }
        }
    }
}

@Composable
private fun ListPreviewRow(row: ListRow, nowMs: Long) {
    val warn = warnColor()
    val ok = okColor()
    val (dot, detail) = when (row.state) {
        ListState.Off -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) to "Switched off"
        ListState.Failed -> warn to "Last refresh failed"
        ListState.Refreshing -> MaterialTheme.colorScheme.primary to "Refreshing…"
        ListState.Ok -> ok to listOfNotNull(
            row.itemCount?.let { "$it items" },
            row.lastRefreshed?.let { "refreshed " + ago(it.toEpochMilli(), nowMs) },
        ).joinToString(" · ").ifEmpty { "Not refreshed yet" }
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
        Column(Modifier.weight(1f)) {
            Text(row.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (row.state == ListState.Off) Pill("Off", MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Emby itself: version, who is connected, scheduled tasks. Read only. */
@Composable
fun ServerCard(vm: AdminHomeViewModel, onOpen: (() -> Unit)? = null) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snapshot = state.snapshot
    val warn = warnColor()
    val ok = okColor()
    AdminCard("Server", Icons.Default.Dns, onClick = onOpen) {
        val info = snapshot?.info
        when {
            snapshot == null -> Text(if (state.loading) "Loading…" else "Could not read the server's state.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            info == null -> StatusRow(Icons.Default.Warning, warn, "Could not read the server's state")
            else -> {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Emby ${info.version}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    if (info.name.isNotEmpty()) Text(info.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (info.updateAvailable) Pill("Update available", warn) else Pill("Up to date", ok)
                        if (info.restartPending) Pill("Restart pending", warn)
                    }
                }
            }
        }
        snapshot?.sessions?.let { s ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                StatTile(Icons.Default.Group, s.active.toString(), "connected", Modifier.weight(1f))
                StatTile(Icons.Default.PlayArrow, s.playing.toString(), "playing", Modifier.weight(1f))
                StatTile(Icons.Default.Sync, s.transcoding.toString(), "transcoding", Modifier.weight(1f))
            }
        }
        snapshot?.tasks?.let { tasks ->
            if (tasks.failed.isEmpty()) {
                StatusRow(Icons.Default.Schedule, ok, "Scheduled tasks are fine", if (tasks.total > 0) "All ${tasks.total} ran as expected last time." else null)
            } else {
                StatusRow(Icons.Default.Warning, warn, "${tasks.failed.size} failed last time", names(tasks.failed.map { it.name }))
            }
        }
    }
}

/** Installed plugins: the two this app works with, whether Emby's catalog has updates, and the full list on request. */
@Composable
fun PluginsCard(vm: AdminHomeViewModel, onOpen: (() -> Unit)? = null) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snapshot = state.snapshot
    val warn = warnColor()
    val ok = okColor()
    var showAll by remember { mutableStateOf(false) }
    val plugins = snapshot?.plugins
    AdminCard("Plugins", Icons.Default.Extension, onClick = onOpen, trailing = { if (plugins != null) Pill("${plugins.size} installed", MaterialTheme.colorScheme.primary) }) {
        if (plugins == null) {
            Text(if (state.loading) "Loading…" else "Could not read the plugins.", color = if (state.loading) MaterialTheme.colorScheme.onSurfaceVariant else warn)
        } else {
            when (val verdict = PluginChecks.hallMonitor(plugins)) {
                PluginChecks.Verdict.Missing -> StatusRow(Icons.Default.Timer, warn, "Hall Monitor", "Not installed") { Pill("Missing", warn) }
                is PluginChecks.Verdict.TooOld -> StatusRow(Icons.Default.Timer, warn, "Hall Monitor ${verdict.version}", "This app needs ${PluginChecks.HALL_MONITOR_MIN} or newer") { Pill("Update", warn) }
                is PluginChecks.Verdict.Fine -> StatusRow(Icons.Default.Timer, ok, "Hall Monitor ${verdict.version}", "Works with this app") { Pill("OK", ok) }
            }
            val smart = PluginChecks.smartLists(plugins)
            if (smart == null) StatusRow(Icons.AutoMirrored.Filled.PlaylistPlay, warn, "SmartLists", "Not installed") { Pill("Missing", warn) }
            else StatusRow(Icons.AutoMirrored.Filled.PlaylistPlay, ok, "SmartLists ${smart.version}", "Works with this app") { Pill("OK", ok) }
            snapshot.pluginUpdates?.let { updates ->
                if (updates.isEmpty()) StatusRow(Icons.Default.SystemUpdate, ok, "No updates in Emby's catalog")
                else StatusRow(Icons.Default.SystemUpdate, warn, "${updates.size} update${if (updates.size == 1) "" else "s"} in Emby's catalog", names(updates))
            }
            Text(
                "Plugins installed from GitHub are not checked here yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { showAll = !showAll }) { Text(if (showAll) "Hide the list" else "Show all ${plugins.size} plugins") }
            if (showAll) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (p in plugins) {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)).padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                                Text(p.name.take(1).uppercase(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                            Text(p.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(p.version, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
