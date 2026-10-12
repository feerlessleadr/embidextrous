package com.feerlessleadr.embidextrous.server

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.feerlessleadr.embidextrous.data.GhPlugin
import com.feerlessleadr.embidextrous.data.GhOrder
import com.feerlessleadr.embidextrous.data.GhRun
import com.feerlessleadr.embidextrous.data.GhRunMode
import com.feerlessleadr.embidextrous.data.GhStatus
import com.feerlessleadr.embidextrous.data.GhrpiGate
import com.feerlessleadr.embidextrous.data.GhrpiInfo
import com.feerlessleadr.embidextrous.home.AdminHomeViewModel
import com.feerlessleadr.embidextrous.home.HomeCardShape
import com.feerlessleadr.embidextrous.home.PluginsCard
import com.feerlessleadr.embidextrous.home.okColor
import com.feerlessleadr.embidextrous.home.warnColor
import com.feerlessleadr.embidextrous.ui.IconBadge
import com.feerlessleadr.embidextrous.ui.Pill
import com.feerlessleadr.embidextrous.watching.ConfirmDialog

private enum class ManagerPage { Main, Add, History, Settings }

/** The Server group's Plugins tab: the plugin manager (GitHub Repo Plugin Install), with the list of every installed plugin underneath. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginManagerTab(vm: PluginManagerViewModel, adminVm: AdminHomeViewModel) {
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(vm) { owner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) { vm.keepFresh() } }
    LaunchedEffect(adminVm) { owner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) { adminVm.keepFresh() } }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    var page by rememberSaveable { mutableStateOf(ManagerPage.Main) }
    val gate = state.gate
    val manage = (gate as? GhrpiGate.Ready)?.info?.allowManage == true

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, containerColor = Color.Transparent) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.detail != null -> PluginDetailScreen(state.detail!!, vm, manage, state.status?.canSelfRestart == true)
                gate !is GhrpiGate.Ready -> PullToRefreshBox(isRefreshing = false, onRefresh = vm::load, modifier = Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(12.dp)) {
                        item { GateCard(gate, onRetry = vm::load) }
                        item { PluginsCard(adminVm) }
                    }
                }
                page == ManagerPage.Add -> AddPluginScreen(vm, onBack = { page = ManagerPage.Main })
                page == ManagerPage.History -> HistoryScreen(vm, onBack = { page = ManagerPage.Main })
                page == ManagerPage.Settings -> ManagerSettingsScreen(vm, manage, onBack = { page = ManagerPage.Main })
                else -> ManagerMain(vm, adminVm, manage, onOpen = { page = it })
            }
        }
    }
}

/** Why the manager is not usable yet, and what to do about it. */
@Composable
private fun GateCard(gate: GhrpiGate, onRetry: () -> Unit) {
    val warn = warnColor()
    Card(Modifier.fillMaxWidth(), shape = HomeCardShape) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconBadge(if (gate is GhrpiGate.Checking) Icons.Default.Extension else Icons.Default.Lock, if (gate is GhrpiGate.Checking) MaterialTheme.colorScheme.primary else warn, size = 44.dp)
                Text("Plugin manager", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            when (gate) {
                GhrpiGate.Checking -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("Looking for the GitHub Repo Plugin Install plugin…")
                }
                GhrpiGate.NotInstalled -> Explain(
                    "The GitHub Repo Plugin Install plugin is not on this server",
                    "It checks GitHub for new versions of plugins like Hall Monitor and SmartLists and installs them. Install it from its GitHub releases (feerlessleadr/Emby.GitHubRepoPluginInstall), restart Emby, then come back here.",
                )
                is GhrpiGate.Off -> Explain(
                    "The phone API is switched off",
                    "Open the Emby dashboard, go to Plugins, then GitHub Repo Plugin Install, and tick \"Allow the phone app to control this plugin\". The app cannot turn this on for you: that is deliberate, so a lost or stolen phone cannot widen its own permissions.",
                )
                is GhrpiGate.PluginTooOld -> Explain("The plugin is too old for this app", "It speaks API version ${gate.info.apiVersion} and this app needs ${com.feerlessleadr.embidextrous.data.Ghrpi.SUPPORTED}. Update the GitHub Repo Plugin Install plugin from the Emby dashboard.")
                is GhrpiGate.AppTooOld -> Explain("This app is too old for the plugin", "The plugin needs API version ${gate.info.minSupportedApiVersion} or newer. Update Embidextrous.")
                is GhrpiGate.Unreachable -> Explain("Could not reach the plugin", gate.message)
                is GhrpiGate.Ready -> Unit
            }
            if (gate !is GhrpiGate.Checking) OutlinedButton(onClick = onRetry) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  Check again")
            }
        }
    }
}

@Composable
private fun Explain(title: String, text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------------------------------------------------------------------------------------------------
// The main page: dashboard, list, and the way to the other pages
// ---------------------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManagerMain(vm: PluginManagerViewModel, adminVm: AdminHomeViewModel, manage: Boolean, onOpen: (ManagerPage) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val status = state.status
    val ok = okColor()
    val warn = warnColor()
    var asking by remember { mutableStateOf<String?>(null) }
    val updatable = state.plugins.filter { it.status.hasUpdate }
    PullToRefreshBox(isRefreshing = false, onRefresh = vm::load, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(12.dp)) {
            if (status?.restartPending == true) {
                item {
                    Card(Modifier.fillMaxWidth(), shape = HomeCardShape) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                IconBadge(Icons.Default.SystemUpdate, warn, size = 40.dp)
                                Text("Restart Emby to finish updating", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                            for (p in status.pendingRestart) {
                                Text("${p.name}: ${p.fromVersion ?: "?"} → ${p.toVersion ?: "?"}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (state.restarting) {
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                                Text("Restarting… this takes about a minute.", style = MaterialTheme.typography.bodySmall)
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    if (status.canSelfRestart) Button(onClick = { asking = "restart" }, modifier = Modifier.weight(1f)) { Text("Restart now") }
                                    OutlinedButton(onClick = vm::dismissRestart, modifier = Modifier.weight(1f)) { Text("Dismiss") }
                                }
                                if (!status.canSelfRestart) Text("Emby cannot restart itself here, so restart it by hand.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth(), shape = HomeCardShape) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            IconBadge(Icons.Default.Extension, MaterialTheme.colorScheme.primary, size = 44.dp)
                            Column(Modifier.weight(1f)) {
                                Text("GitHub plugins", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text(
                                    when {
                                        status == null -> "Loading…"
                                        status.counts.total == 0 -> "None added yet"
                                        else -> "${status.counts.total} plugins"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            val n = status?.updatesReady ?: 0
                            if (status != null) {
                                if (status.counts.failed > 0) Pill("${status.counts.failed} failed", warn)
                                else if (n > 0) Pill("$n update${if (n == 1) "" else "s"}", warn)
                                else if (status.counts.total > 0) Pill("All current", ok)
                            }
                        }
                        val run = status?.run
                        if (run != null && run.isRunning) {
                            LinearProgressIndicator(progress = { if (run.total > 0) (run.done.toFloat() / run.total).coerceIn(0f, 1f) else 0f }, modifier = Modifier.fillMaxWidth())
                            Text("${run.done} of ${run.total}" + (run.current?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(onClick = { vm.run(GhRunMode.Check, "Checking for updates") }, modifier = Modifier.weight(1f)) { Text("Check") }
                                Button(onClick = { asking = "available" }, enabled = (status?.updatesReady ?: 0) > 0, modifier = Modifier.weight(1f)) { Text("Update ${status?.updatesReady ?: 0}") }
                            }
                            if ((status?.counts?.total ?: 0) > 0) {
                                TextButton(onClick = { asking = "all" }) { Text("Update everything, even what is current") }
                            }
                        }
                    }
                }
            }
            if (state.pluginsLoaded && state.plugins.isEmpty()) {
                item { Text("No GitHub plugins yet. ${if (manage) "Tap Add a plugin below." else "Adding plugins needs the second switch on the plugin's page in Emby."}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp)) }
            }
            items(GhOrder.sorted(state.plugins), key = { it.id }) { plugin -> GhPluginRow(plugin) { vm.openDetail(plugin) } }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (manage) Button(onClick = { onOpen(ManagerPage.Add) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text("  Add a plugin")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { vm.loadHistory(); onOpen(ManagerPage.History) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("  History")
                        }
                        OutlinedButton(onClick = { vm.loadSettings(); onOpen(ManagerPage.Settings) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("  Settings")
                        }
                    }
                }
            }
            item { PluginsCard(adminVm) }
        }
    }
    when (asking) {
        "available" -> ConfirmDialog(
            title = "Update ${updatable.size} plugin${if (updatable.size == 1) "" else "s"}?",
            message = updatable.joinToString("\n") { "${it.name}: ${it.installedVersion ?: "?"} → ${it.latestVersion ?: "?"}" } + "\n\nEmby needs a restart afterwards to start using them. Nothing restarts by itself.",
            confirm = "Update",
            onDismiss = { asking = null },
            onConfirm = { asking = null; vm.run(GhRunMode.Available, "Updating ${updatable.size} plugin${if (updatable.size == 1) "" else "s"}") },
        )
        "all" -> ConfirmDialog(
            title = "Update everything?",
            message = "This installs the newest release of every GitHub plugin, including those with auto-update switched off. Each current file is backed up first. Emby needs a restart afterwards.",
            confirm = "Update everything",
            onDismiss = { asking = null },
            onConfirm = { asking = null; vm.run(GhRunMode.All, "Updating everything") },
        )
        "restart" -> ConfirmDialog(
            title = "Restart Emby now?",
            message = "Everyone watching is interrupted, and the server is offline for about a minute. Hall Monitor and this app reconnect by themselves.",
            confirm = "Restart",
            onDismiss = { asking = null },
            onConfirm = { asking = null; vm.restart() },
        )
    }
}

@Composable
private fun GhPluginRow(plugin: GhPlugin, onClick: () -> Unit) {
    val ok = okColor()
    val warn = warnColor()
    val (label, color) = when (plugin.status) {
        GhStatus.Failed -> "Failed" to warn
        GhStatus.UpdateAvailable -> "Update" to warn
        GhStatus.UpdateWaiting -> "Update waiting" to warn
        GhStatus.UpToDate -> "Current" to ok
        GhStatus.NoRelease -> "No release" to MaterialTheme.colorScheme.onSurfaceVariant
        else -> "Not checked" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(Modifier.fillMaxWidth().clip(HomeCardShape).clickable(onClick = onClick), shape = HomeCardShape) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Text(plugin.name.take(1).uppercase(), color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(plugin.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val versions = when {
                    plugin.status.hasUpdate -> "${plugin.installedVersion ?: "?"} → ${plugin.latestVersion ?: "?"}"
                    plugin.installedVersion != null -> plugin.installedVersion
                    plugin.latestVersion != null -> "latest ${plugin.latestVersion}"
                    else -> plugin.owner
                }
                Text(versions, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (plugin.status == GhStatus.Failed && !plugin.lastError.isNullOrBlank()) {
                    Text(plugin.lastError, style = MaterialTheme.typography.bodySmall, color = warn, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Pill(label, color)
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------------------------------------------------------------------------------------------------------
// One plugin
// ---------------------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PluginDetailScreen(plugin: GhPlugin, vm: PluginManagerViewModel, manage: Boolean, canSelfRestart: Boolean) {
    val warn = warnColor()
    val ok = okColor()
    var asking by remember { mutableStateOf<String?>(null) }
    var filter by remember(plugin.id, plugin.assetNameFilter) { mutableStateOf(plugin.assetNameFilter) }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(plugin.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { IconButton(onClick = vm::closeDetail) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
        )
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(12.dp)) {
            item {
                Card(Modifier.fillMaxWidth(), shape = HomeCardShape) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(plugin.statusText.ifEmpty { plugin.status.name }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            if (plugin.restartPending) Pill("Restart pending", warn)
                        }
                        Text(
                            "Installed ${plugin.installedVersion ?: "by hand, not by this plugin"} · latest ${plugin.latestVersion ?: "unknown"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(plugin.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (plugin.status == GhStatus.Failed && !plugin.lastError.isNullOrBlank()) Text(plugin.lastError, style = MaterialTheme.typography.bodyMedium, color = warn)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = { vm.checkOne(plugin) }, modifier = Modifier.weight(1f)) { Text("Check") }
                            Button(
                                onClick = { asking = "update" },
                                enabled = plugin.status.hasUpdate || plugin.status == GhStatus.Failed,
                                modifier = Modifier.weight(1f),
                            ) { Text(if (plugin.status == GhStatus.Failed) "Retry" else "Update") }
                        }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth(), shape = HomeCardShape) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        SwitchRow("Update automatically", "The daily task installs new releases of this plugin by itself.", plugin.autoUpdate) { vm.setPluginSwitches(plugin, autoUpdate = it) }
                        SwitchRow("Include pre-releases", "Also offer releases marked as pre-release on GitHub.", plugin.allowPreRelease) { vm.setPluginSwitches(plugin, allowPreRelease = it) }
                        if (plugin.assetNames.size > 1) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                            Text("Which file to install", style = MaterialTheme.typography.bodyLarge)
                            Text("This release has several files. Pick the one this plugin should use.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                plugin.assetNames.forEach { name ->
                                    FilterChip(selected = filter == name, onClick = { filter = name; vm.setPluginSwitches(plugin, assetNameFilter = name) }, label = { Text(name) })
                                }
                                if (filter.isNotEmpty()) TextButton(onClick = { filter = ""; vm.setPluginSwitches(plugin, assetNameFilter = "") }) { Text("Clear the choice") }
                            }
                        }
                    }
                }
            }
            if (!plugin.releaseNotes.isNullOrBlank()) {
                item {
                    Card(Modifier.fillMaxWidth(), shape = HomeCardShape) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Release notes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(plugin.releaseNotes, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth(), shape = HomeCardShape) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Previous versions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        val backups = plugin.backups
                        if (backups.isNullOrEmpty()) {
                            Text(if (backups == null) "Loading…" else "No backup yet. One is made each time this plugin is updated.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            backups.forEach { b ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(b.version ?: "unknown version", style = MaterialTheme.typography.bodyMedium)
                                    Text(ago(b.createdMs, System.currentTimeMillis()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            OutlinedButton(onClick = { asking = "restore" }) { Text("Put back the newest backup") }
                        }
                    }
                }
            }
            if (manage) {
                item {
                    OutlinedButton(onClick = { asking = "remove" }, modifier = Modifier.fillMaxWidth()) { Text("Remove this plugin…", color = warn) }
                }
            }
        }
    }
    when (asking) {
        "update" -> ConfirmDialog(
            title = "Update ${plugin.name}?",
            message = "Installs ${plugin.latestVersion ?: "the newest release"}. The current file is backed up first, and Emby needs a restart afterwards.",
            confirm = "Update",
            onDismiss = { asking = null },
            onConfirm = { asking = null; vm.updateOne(plugin) },
        )
        "restore" -> ConfirmDialog(
            title = "Put back the previous version?",
            message = "The newest backup replaces the current file, and automatic updates are switched off for this plugin so the next daily run does not undo it. Emby may need a restart.",
            confirm = "Put back",
            onDismiss = { asking = null },
            onConfirm = { asking = null; vm.restore(plugin) },
        )
        "remove" -> ConfirmDialog(
            title = "Remove ${plugin.name}?",
            message = "This deletes the plugin's file from the server. Its settings may be kept, but it stops working at the next Emby restart. This cannot be undone from here.",
            confirm = "Remove",
            onDismiss = { asking = null },
            onConfirm = { asking = null; vm.remove(plugin) },
        )
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

// ---------------------------------------------------------------------------------------------------------
// Add a plugin
// ---------------------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddPluginScreen(vm: PluginManagerViewModel, onBack: () -> Unit) {
    LaunchedEffect(vm) { vm.loadAvailable() }
    val state by vm.state.collectAsStateWithLifecycle()
    var address by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Add a plugin") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } })
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(12.dp)) {
            item {
                Card(Modifier.fillMaxWidth(), shape = HomeCardShape) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("From a GitHub address", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Adding a plugin installs someone else's code on your server. Only add plugins you trust.", style = MaterialTheme.typography.bodySmall, color = warnColor())
                        OutlinedTextField(
                            value = address,
                            onValueChange = { address = it },
                            label = { Text("owner/repository") },
                            placeholder = { Text("feerlessleadr/hall-monitor-emby") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(onClick = { confirm = address }, enabled = address.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth()) {
                            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary) else Text("Add")
                        }
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search the registries") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            when {
                state.availableLoading -> item { Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Text("Asking the registries (this can take a while)…") } }
                state.availableError != null -> item { Text(state.availableError.orEmpty(), color = warnColor(), modifier = Modifier.padding(8.dp)) }
                else -> {
                    val list = state.available.orEmpty().filter { query.isBlank() || it.name.contains(query, true) || (it.description ?: "").contains(query, true) }
                    if (list.isEmpty()) item { Text("Nothing matches.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp)) }
                    items(list, key = { it.url }) { a ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable(enabled = !a.alreadyAdded) { confirm = a.url }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(a.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = if (a.alreadyAdded) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                                a.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                            }
                            if (a.alreadyAdded) Pill("Added", okColor())
                        }
                    }
                }
            }
        }
    }
    confirm?.let { target ->
        ConfirmDialog(
            title = "Add this plugin?",
            message = "$target\n\nThe plugin looks it up on GitHub right away (a few seconds). It is only installed when you update it afterwards.",
            confirm = "Add",
            onDismiss = { confirm = null },
            onConfirm = {
                confirm = null
                busy = true
                vm.add(target) { done -> busy = false; if (done) { address = ""; vm.loadAvailable() } }
            },
        )
    }
}

// ---------------------------------------------------------------------------------------------------------
// History
// ---------------------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryScreen(vm: PluginManagerViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("History") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } })
        val history = state.history
        when {
            history == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            history.isEmpty() -> EmptyNote(Icons.Default.History, "No runs yet", "Updates and checks that install something are listed here.")
            else -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(12.dp)) {
                items(history, key = { it.id }) { RunCard(it) }
            }
        }
    }
}

@Composable
private fun RunCard(run: GhRun) {
    val ok = okColor()
    val warn = warnColor()
    Card(Modifier.fillMaxWidth(), shape = HomeCardShape) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${run.trigger.ifEmpty { "Run" }} · ${ago(run.startedMs, System.currentTimeMillis())}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (run.failed > 0) Pill("${run.failed} failed", warn) else if (run.updated > 0) Pill("${run.updated} updated", ok) else Pill("Nothing to do", MaterialTheme.colorScheme.onSurfaceVariant)
            }
            for (r in run.results) {
                val good = r.outcome == "Updated" || r.outcome == "UpToDate"
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(if (good) Icons.Default.CheckCircle else Icons.Default.Warning, contentDescription = null, tint = if (good) ok else warn, modifier = Modifier.size(18.dp))
                    Column {
                        Text(r.name + (if (r.toVersion != null) " → ${r.toVersion}" else ""), style = MaterialTheme.typography.bodyMedium)
                        if (!r.message.isNullOrBlank()) Text(r.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------
// Settings and registries
// ---------------------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManagerSettingsScreen(vm: PluginManagerViewModel, manage: Boolean, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val settings = state.settings
    var token by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Plugin manager settings") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } })
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(12.dp)) {
            item {
                Card(Modifier.fillMaxWidth(), shape = HomeCardShape) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("GitHub token", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "Without a token GitHub allows 60 requests an hour. A token raises that to 5,000 and is needed for private repositories. It is sent to the plugin and never read back.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(if (settings == null) "Loading…" else if (settings.tokenSet) "A token is saved on the server." else "No token saved.", style = MaterialTheme.typography.bodyMedium)
                        OutlinedTextField(
                            value = token,
                            onValueChange = { token = it },
                            label = { Text("New token") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { vm.saveToken(token); token = "" }, enabled = token.isNotBlank()) { Text("Save") }
                            OutlinedButton(onClick = vm::testToken) { Text("Test") }
                            if (settings?.tokenSet == true) OutlinedButton(onClick = vm::clearToken) { Text("Remove") }
                        }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth(), shape = HomeCardShape) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Scheduled updates", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        SwitchRow(
                            "Restart Emby after a scheduled update",
                            "The daily task restarts the server as soon as it installs something. Updates you start from this app never restart it by themselves.",
                            settings?.restartServerAfterInstall == true,
                        ) { vm.setRestartAfterInstall(it) }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth(), shape = HomeCardShape) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Registries", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Lists of plugins the Add screen can offer. Adding or editing registries is done on the plugin's page in Emby" + if (manage) "." else " (and needs the second switch).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val registries = state.registries
                        if (registries == null) Text("Loading…") else registries.forEach { r ->
                            SwitchRow(r.name + if (r.builtIn) " (built in)" else "", r.rawUrl, r.enabled) { vm.setRegistryEnabled(r, it) }
                        }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth(), shape = HomeCardShape) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Permissions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("The phone API and the add/remove switch are changed only on the plugin's own page in the Emby dashboard, never from this app.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Pill("Phone API: on", okColor())
                            if (manage) Pill("Add and remove: on", okColor()) else Pill("Add and remove: off", MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
