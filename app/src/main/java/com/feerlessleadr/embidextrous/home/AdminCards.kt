package com.feerlessleadr.embidextrous.home

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feerlessleadr.embidextrous.data.PluginChecks
import com.feerlessleadr.embidextrous.ui.Brand
import com.feerlessleadr.embidextrous.ui.IconBadge

@Composable
private fun AdminCard(title: String, icon: ImageVector, tint: Color, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = HomeCardShape) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconBadge(icon, tint, size = 40.dp)
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            content()
        }
    }
}

@Composable
private fun Detail(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant, bold: Boolean = false) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = if (bold) FontWeight.Bold else null)
}

/** Names for a sentence: the first few, then "and N more". */
private fun names(list: List<String>, max: Int = 3): String =
    if (list.size <= max) list.joinToString(", ") else list.take(max).joinToString(", ") + " and ${list.size - max} more"

/** How the smart lists are doing, with Refresh all. */
@Composable
fun SmartListsCard(admin: AdminHome, nowMs: Long) {
    val state by admin.lists.state.collectAsStateWithLifecycle()
    val problem by admin.problem.collectAsStateWithLifecycle()
    val warn = warnColor()
    AdminCard("Smart lists", Icons.AutoMirrored.Filled.PlaylistPlay, MaterialTheme.colorScheme.primary) {
        val snapshot = AdminModel.lists(state.lists, state.status)
        when {
            problem != null -> Detail(problem.orEmpty(), warn)
            state.loading -> Detail("Loading…")
            state.error != null -> Detail(state.error.orEmpty(), warn)
            else -> {
                Detail(
                    if (snapshot.total == 0) "No smart lists yet." else "${snapshot.total} lists, ${snapshot.enabled} switched on.",
                    MaterialTheme.colorScheme.onSurface,
                )
                if (snapshot.refreshing > 0) {
                    Detail(if (snapshot.refreshing == 1) "Refreshing 1 list…" else "Refreshing ${snapshot.refreshing} lists…")
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                if (snapshot.failed.isNotEmpty()) {
                    Detail("Last refresh failed: ${names(snapshot.failed)}.", warn, bold = true)
                } else if (snapshot.total > 0 && snapshot.refreshing == 0) {
                    Detail("Every list's last refresh worked.", okColor())
                }
                snapshot.lastRefreshed?.let {
                    Detail("Last refresh " + DateUtils.getRelativeTimeSpanString(it.toEpochMilli(), nowMs, DateUtils.MINUTE_IN_MILLIS))
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

/** Emby itself: version, who is connected, failed scheduled tasks. Read only. */
@Composable
fun ServerCard(vm: AdminHomeViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snapshot = state.snapshot
    val warn = warnColor()
    AdminCard("Server", Icons.Default.Dns, MaterialTheme.colorScheme.primary) {
        when {
            snapshot == null -> Detail(if (state.loading) "Loading…" else "Could not read the server's state.")
            else -> {
                val info = snapshot.info
                if (info == null) {
                    Detail("Could not read the server's state.", warn)
                } else {
                    Detail("Emby ${info.version}" + if (info.name.isNotEmpty()) " · ${info.name}" else "", MaterialTheme.colorScheme.onSurface, bold = true)
                    if (info.restartPending) Detail("A restart is pending.", warn, bold = true)
                    if (info.updateAvailable) Detail("An Emby update is available.", warn, bold = true)
                }
                snapshot.sessions?.let { s ->
                    Detail(
                        when {
                            s.active == 0 -> "Nobody is connected."
                            else -> "${s.active} connected, ${s.playing} playing" + if (s.transcoding > 0) ", ${s.transcoding} transcoding" else ""
                        },
                    )
                }
                snapshot.failedTasks?.let { failed ->
                    if (failed.isEmpty()) Detail("Scheduled tasks are fine.", okColor())
                    else Detail("Failed last time: ${names(failed.map { it.name })}.", warn, bold = true)
                }
            }
        }
    }
}

/** Installed plugins: the two this app works with, whether Emby's catalog has updates, and the full list on request. */
@Composable
fun PluginsCard(vm: AdminHomeViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snapshot = state.snapshot
    val warn = warnColor()
    val ok = okColor()
    var showAll by remember { mutableStateOf(false) }
    AdminCard("Plugins", Icons.Default.Extension, MaterialTheme.colorScheme.primary) {
        val plugins = snapshot?.plugins
        if (plugins == null) {
            Detail(if (state.loading) "Loading…" else "Could not read the plugins.", if (state.loading) MaterialTheme.colorScheme.onSurfaceVariant else warn)
        } else {
            Detail("${plugins.size} installed.", MaterialTheme.colorScheme.onSurface)
            when (val verdict = PluginChecks.hallMonitor(plugins)) {
                PluginChecks.Verdict.Missing -> Detail("Hall Monitor is not installed.", warn, bold = true)
                is PluginChecks.Verdict.TooOld -> Detail("Hall Monitor ${verdict.version} is older than the ${PluginChecks.HALL_MONITOR_MIN} this app needs.", warn, bold = true)
                is PluginChecks.Verdict.Fine -> Detail("Hall Monitor ${verdict.version}", ok)
            }
            PluginChecks.smartLists(plugins)?.let { Detail("SmartLists ${it.version}", ok) }
                ?: Detail("SmartLists is not installed.", warn, bold = true)
            snapshot.pluginUpdates?.let { updates ->
                if (updates.isEmpty()) Detail("No updates in Emby's plugin catalog.")
                else Detail("Updates in Emby's catalog: ${names(updates)}.", warn, bold = true)
            }
            Detail("Plugins installed from GitHub are not checked here yet.")
            TextButton(onClick = { showAll = !showAll }) { Text(if (showAll) "Hide the list" else "Show all plugins") }
            if (showAll) {
                for (p in plugins) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(p.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(p.version, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
