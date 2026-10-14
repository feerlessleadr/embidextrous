package com.feerlessleadr.embidextrous.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feerlessleadr.embidextrous.AppContainer
import com.feerlessleadr.embidextrous.notifications.EventSpec
import com.feerlessleadr.embidextrous.notifications.NotifAvailability
import com.feerlessleadr.embidextrous.notifications.NotifPoster
import com.feerlessleadr.embidextrous.notifications.NotifRule
import com.feerlessleadr.embidextrous.notifications.NotifRules
import com.feerlessleadr.embidextrous.notifications.NotifService
import com.feerlessleadr.embidextrous.notifications.ParamSpec
import com.feerlessleadr.embidextrous.notifications.ParamType
import com.feerlessleadr.embidextrous.notifications.QuietHours
import com.feerlessleadr.embidextrous.watching.AlertService
import java.util.Locale

private fun minuteText(minute: Int): String = String.format(Locale.US, "%d:%02d %s", if (minute / 60 % 12 == 0) 12 else minute / 60 % 12, minute % 60, if (minute / 60 < 12) "AM" else "PM")

/** The services this phone can configure (Hall Monitor for everyone; the rest follow the account and the setup choice). */
@Composable
private fun availableServices(container: AppContainer): List<NotifService> {
    val session by container.session.collectAsStateWithLifecycle()
    val profile by container.settings.profile.collectAsStateWithLifecycle()
    val customSmartLists by container.settings.customSmartLists.collectAsStateWithLifecycle()
    return NotifAvailability.services(profile, session?.isAdministrator == true, customSmartLists)
}

/** Settings > Notifications: this phone's permission, pause and quiet hours, then one row per service. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(container: AppContainer, onBack: () -> Unit, onService: (NotifService) -> Unit) {
    val services = availableServices(container)
    val rules by container.settings.notifRules.collectAsStateWithLifecycle()
    val switches by container.settings.serviceSwitches.collectAsStateWithLifecycle()
    val paused by container.settings.notifPaused.collectAsStateWithLifecycle()
    val quiet by container.settings.quietHours.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(AlertService.canNotify(context)) }
    var editQuiet by remember { mutableStateOf(false) }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> allowed = granted }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("On this phone", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (!allowed) {
                        Text("Android is blocking notifications from this app, so no alert can be shown.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                        Button(onClick = {
                            if (android.os.Build.VERSION.SDK_INT >= 33) askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }) { Text("Allow notifications") }
                    }
                    ToggleRow("Pause all notifications", "Nothing is shown while this is on. The alerts you chose are kept.", paused) { container.settings.setNotifPaused(it) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    ToggleRow(
                        "Quiet hours",
                        if (quiet.enabled) "Alerts arrive without sound from ${minuteText(quiet.startMinute)} to ${minuteText(quiet.endMinute)}." else "Alerts that arrive at night can come without sound.",
                        quiet.enabled,
                    ) { container.settings.setQuietHours(quiet.copy(enabled = it)) }
                    if (quiet.enabled) OutlinedButton(onClick = { editQuiet = true }) { Text("Change the hours") }
                    Text(
                        "Some phones stop background apps to save battery. If alerts stop arriving, allow Embidextrous to run without battery restrictions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = {
                        try {
                            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (e: ActivityNotFoundException) {
                            // This phone has no such screen; the text above still tells what to look for.
                        }
                    }) { Text("Open battery settings") }
                }
            }
            Text("Choose what each service tells you", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
            for (service in services) {
                val on = switches[service.name] == true
                val count = rules.count { it.enabled && com.feerlessleadr.embidextrous.notifications.NotifCatalog.spec(it.event)?.service == service }
                Card(Modifier.fillMaxWidth().clickable { onService(service) }, shape = RoundedCornerShape(24.dp)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Default.Notifications, MaterialTheme.colorScheme.primary, size = 40.dp)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(service.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (!on) "Off" else if (count == 0) "On, but no alert is switched on" else "On: $count alert${if (count == 1) "" else "s"} switched on",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                }
            }
            if (services.size == 1) {
                Text(
                    "Other services (Label Maker, Hall Pass, the plugin manager and more) are for an Emby administrator account on the Administrator setup. You can change that setup under Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
    if (editQuiet) QuietHoursDialog(quiet, onDismiss = { editQuiet = false }) { container.settings.setQuietHours(it); editQuiet = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuietHoursDialog(current: QuietHours, onDismiss: () -> Unit, onSave: (QuietHours) -> Unit) {
    val start = rememberTimePickerState(current.startMinute / 60, current.startMinute % 60, false)
    val end = rememberTimePickerState(current.endMinute / 60, current.endMinute % 60, false)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quiet hours") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("From", style = MaterialTheme.typography.titleSmall)
                TimeInput(start)
                Text("Until", style = MaterialTheme.typography.titleSmall)
                TimeInput(end)
            }
        },
        confirmButton = { Button(onClick = { onSave(current.copy(startMinute = start.hour * 60 + start.minute, endMinute = end.hour * 60 + end.minute)) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** One service: its switch, its alert types with their rules (add, edit, delete, switch each), the Android sound settings and a test button. */
@Composable
fun NotificationServiceScreen(container: AppContainer, service: NotifService, onBack: () -> Unit) {
    val context = LocalContext.current
    val rules by container.settings.notifRules.collectAsStateWithLifecycle()
    val switches by container.settings.serviceSwitches.collectAsStateWithLifecycle()
    val pluginKey by container.pluginKey.collectAsStateWithLifecycle()
    val tracearr by container.tracearr.collectAsStateWithLifecycle()
    val specs = remember(service, rules) { container.notifHub.specs(service) }
    val on = switches[service.name] == true
    var editing by remember { mutableStateOf<Pair<NotifRule, EventSpec>?>(null) }
    var pendingOn by remember { mutableStateOf(false) }
    var denied by remember { mutableStateOf(false) }
    // Whether the plugin is there and new enough to send alerts (the plugins that push their own events).
    var pluginNote by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(service) {
        if (service.apiBase == null) return@LaunchedEffect
        pluginNote = try {
            val api = container.api()
            if (api == null || com.feerlessleadr.embidextrous.notifications.NotifCatalog.sendsAlerts(api.pluginInfo(service))) null
            else "This version of ${service.title} cannot send alerts yet. Update the plugin from the Server tab, then come back."
        } catch (e: com.feerlessleadr.embidextrous.data.ApiException) {
            if (e.status == 404) "${service.title} is not installed on this server, or it is too old to send alerts." else null
        }
    }

    // Hall Monitor's user names (for "only these users") come from its live status while this screen is open.
    if (service == NotifService.HallMonitor) {
        DisposableEffect(Unit) {
            container.hallMonitor.acquire()
            onDispose { container.hallMonitor.release() }
        }
    }
    val hmState by container.hallMonitor.state.collectAsStateWithLifecycle()
    // The alert list of a plugin that publishes one is fetched whenever the screen opens, so new alert types appear.
    androidx.compose.runtime.LaunchedEffect(service, on) { if (on && service.apiBase != null) container.notifHub.refreshCatalog(service) }

    fun setOn(value: Boolean) {
        container.settings.setServiceEnabled(service, value)
        AlertService.sync(context)
    }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        denied = !granted
        if (granted && pendingOn) setOn(true)
        pendingOn = false
    }

    NotificationServiceScaffold(service.title, onBack) {
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ToggleRow("Alerts from ${service.title}", if (on) "On" else "Off. Nothing is watched and nothing is sent for this service.", on) { value ->
                    if (value && !AlertService.canNotify(context)) {
                        pendingOn = true
                        askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        denied = false
                        setOn(value)
                    }
                }
                pluginNote?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                if (denied) Text("Notifications are blocked for this app, so this cannot be switched on.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                if (on && service == NotifService.HallMonitor && pluginKey == null) {
                    Text("These alerts need the Hall Monitor key. Enter it in Settings, under Hall Monitor. Until then nothing is watched.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                if (on && service == NotifService.Tracearr && tracearr == null) {
                    Text("These alerts need the Tracearr address and key. Enter them in Settings, under Tracearr.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                if (service.apiBase != null) {
                    Text(
                        "${service.title} also has its own switches on the plugin's page in Emby (Phone alerts). An alert switched off there is never sent, so it cannot reach this phone whatever you choose here.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { try { context.startActivity(NotifPoster.channelSettings(context, service)) } catch (e: ActivityNotFoundException) { } }) { Text("Sound and vibration") }
                    OutlinedButton(onClick = { NotifPoster.test(context, service) }, enabled = AlertService.canNotify(context)) { Text("Send a test") }
                }
            }
        }
        for (spec in specs) {
            val mine = rules.filter { it.event == spec.id }
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(spec.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(spec.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    for (rule in mine) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(ruleSummary(rule, spec, hmState.cards.associate { it.status.userId to it.status.username }), style = MaterialTheme.typography.bodyLarge)
                            }
                            Switch(checked = rule.enabled, onCheckedChange = { v -> container.setNotifRules(rules.map { if (it.id == rule.id) it.copy(enabled = v) else it }) })
                            if (spec.params.isNotEmpty() || spec.perUser) {
                                IconButton(onClick = { editing = rule to spec }) { Icon(Icons.Default.Edit, contentDescription = "Change this alert") }
                            }
                            if (!spec.unique) IconButton(onClick = { container.setNotifRules(rules.filterNot { it.id == rule.id }) }) { Icon(Icons.Default.Delete, contentDescription = "Delete this alert") }
                        }
                    }
                    if (mine.isEmpty() || !spec.unique) {
                        OutlinedButton(onClick = {
                            val fresh = NotifRule(NotifRules.newId(), spec.id, true, spec.defaultParams())
                            container.setNotifRules(rules + fresh)
                            if (spec.params.isNotEmpty() || spec.perUser) editing = fresh to spec
                        }) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Text(if (spec.unique) "Switch this alert on" else "Add another", modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }
        }
    }
    editing?.let { (rule, spec) ->
        RuleEditorDialog(
            rule = rule, spec = spec, userNames = hmState.cards.associate { it.status.userId to it.status.username },
            onDismiss = { editing = null },
            onSave = { updated -> container.setNotifRules(rules.map { if (it.id == updated.id) updated else it }); editing = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationServiceScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

/** One line describing a rule: its settings and who it covers. */
fun ruleSummary(rule: NotifRule, spec: EventSpec, userNames: Map<String, String> = emptyMap()): String {
    val parts = ArrayList<String>()
    for (p in spec.params) {
        val value = rule.params[p.name] ?: p.default
        parts.add(
            when (p.type) {
                ParamType.Choice -> p.choices.firstOrNull { it.first == value }?.second ?: value
                ParamType.Int -> if (p.name == "percent") "$value%" else if (p.name.equals("minutes", true)) "$value minutes" else "${p.label}: $value"
                ParamType.Text -> if (p.name == "time") "at ${value}" else value
                ParamType.Bool -> if (value == "true") p.label else ""
            },
        )
    }
    if (spec.perUser) parts.add(if (rule.users.isEmpty()) "everyone" else rule.users.joinToString(", ") { userNames[it] ?: "a user" })
    return parts.filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { "Always on for this service" }
}

@Composable
private fun RuleEditorDialog(rule: NotifRule, spec: EventSpec, userNames: Map<String, String>, onDismiss: () -> Unit, onSave: (NotifRule) -> Unit) {
    var values by remember { mutableStateOf(spec.params.associate { it.name to (rule.params[it.name] ?: it.default) }) }
    var chosen by remember { mutableStateOf(rule.users.toSet()) }
    val valid = spec.params.all { p -> paramValid(p, values[p.name].orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(spec.title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (p in spec.params) {
                    when (p.type) {
                        ParamType.Choice -> {
                            Text(p.label, style = MaterialTheme.typography.titleSmall)
                            for ((value, label) in p.choices) {
                                Row(Modifier.fillMaxWidth().clickable { values = values + (p.name to value) }, verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = values[p.name] == value, onClick = { values = values + (p.name to value) })
                                    Text(label)
                                }
                            }
                        }
                        ParamType.Bool -> ToggleRow(p.label, "", values[p.name] == "true") { values = values + (p.name to it.toString()) }
                        else -> OutlinedTextField(
                            value = values[p.name].orEmpty(),
                            onValueChange = { t -> values = values + (p.name to if (p.type == ParamType.Int) t.filter { it.isDigit() }.take(6) else t.take(12)) },
                            label = { Text(p.label) },
                            supportingText = if (p.name == "time") ({ Text("24-hour time, for example 20:00") }) else null,
                            singleLine = true,
                            isError = !paramValid(p, values[p.name].orEmpty()),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                if (spec.perUser) {
                    Text("Who this covers", style = MaterialTheme.typography.titleSmall)
                    Row(Modifier.fillMaxWidth().clickable { chosen = emptySet() }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = chosen.isEmpty(), onClick = { chosen = emptySet() })
                        Text("Every user")
                    }
                    val everyone = (userNames.keys + chosen).distinct()
                    for (id in everyone) {
                        Row(
                            Modifier.fillMaxWidth().clickable { chosen = if (id in chosen) chosen - id else chosen + id },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = id in chosen, onCheckedChange = { chosen = if (it) chosen + id else chosen - id })
                            Text(userNames[id] ?: "A user that is not in the list now")
                        }
                    }
                    if (userNames.isEmpty()) Text("Users appear here when Hall Monitor is connected.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { Button(enabled = valid, onClick = { onSave(rule.copy(params = values, users = chosen.toList())) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun paramValid(p: ParamSpec, value: String): Boolean = when (p.type) {
    ParamType.Int -> value.toIntOrNull()?.let { (p.min == 0 && p.max == 0) || it in p.min..p.max } == true
    ParamType.Text -> if (p.name == "time") com.feerlessleadr.embidextrous.notifications.HallMonitorEngine.parseTime(value) != null else true
    ParamType.Choice -> p.choices.isEmpty() || p.choices.any { it.first == value }
    ParamType.Bool -> true
}
