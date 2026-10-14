package com.feerlessleadr.embidextrous.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feerlessleadr.embidextrous.AppContainer
import com.feerlessleadr.embidextrous.notifications.NotifKind
import com.feerlessleadr.embidextrous.notifications.NotifRule
import com.feerlessleadr.embidextrous.notifications.NotifRules
import com.feerlessleadr.embidextrous.watching.AlertService

/**
 * Settings, Hall Monitor card, notification rules: any number of rules, each on or off and deletable. Rules live on this phone.
 * The first rule is the old "Background alerts" switch, now "limit reached".
 */
@Composable
fun NotifRulesSection(container: AppContainer) {
    val rules by container.settings.notifRules.collectAsStateWithLifecycle()
    val pluginKey by container.pluginKey.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var adding by remember { mutableStateOf(false) }
    var pendingId by remember { mutableStateOf<String?>(null) }
    var permissionDenied by remember { mutableStateOf(false) }

    // Android 13 and later ask before an app may show notifications. A rule is switched on only once that is granted.
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionDenied = !granted
        val id = pendingId
        pendingId = null
        if (id != null) container.setNotifRules(rules.map { if (it.id == id) it.copy(enabled = granted) else it })
    }

    fun switchOn(rule: NotifRule, on: Boolean) {
        if (on && !AlertService.canNotify(context)) {
            pendingId = rule.id
            askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            permissionDenied = false
            container.setNotifRules(rules.map { if (it.id == rule.id) it.copy(enabled = on) else it })
        }
    }

    Text(
        "A percentage rule checks every limit a user has (daily, weekly, monthly and yearly) and sends one notification for each limit that reaches it. This needs Hall Monitor plugin 2.2 or newer. An older plugin reports only the limit closest to running out.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (permissionDenied) {
        Text(
            "Notifications are blocked for this app, so these rules cannot be switched on.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    if (rules.isEmpty()) {
        Text("No rules yet. Add one below.", style = MaterialTheme.typography.bodyMedium)
    }
    for (rule in rules) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text(ruleTitle(rule), style = MaterialTheme.typography.bodyLarge)
                Text(ruleSubtitle(rule), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = rule.enabled, onCheckedChange = { switchOn(rule, it) })
            IconButton(onClick = { container.setNotifRules(rules.filterNot { it.id == rule.id }) }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete this rule")
            }
        }
    }
    OutlinedButton(onClick = { adding = true }) {
        Icon(Icons.Default.Add, contentDescription = null)
        Text("Add a rule", modifier = Modifier.padding(start = 6.dp))
    }
    if (rules.any { it.enabled } && pluginKey == null) {
        Text(
            "These rules need the Hall Monitor key above. Until it is saved, nothing is watched and no notification is sent.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    if (rules.any { it.enabled }) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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

    if (adding) {
        AddRuleDialog(
            onDismiss = { adding = false },
            onSave = { rule ->
                adding = false
                container.setNotifRules(rules + rule.copy(enabled = true))
                if (!AlertService.canNotify(context)) {
                    pendingId = rule.id
                    askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
        )
    }
}

@Composable
private fun AddRuleDialog(onDismiss: () -> Unit, onSave: (NotifRule) -> Unit) {
    var kind by remember { mutableStateOf(NotifKind.PercentUsed) }
    var percentText by remember { mutableStateOf("80") }
    val percent = percentText.toIntOrNull()
    val valid = kind == NotifKind.AllTimeUsed || (percent != null && percent in 1..99)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a notification") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = kind == NotifKind.PercentUsed, onClick = { kind = NotifKind.PercentUsed })
                    Text("A user has used a set percentage of their time")
                }
                if (kind == NotifKind.PercentUsed) {
                    OutlinedTextField(
                        value = percentText,
                        onValueChange = { text -> percentText = text.filter { it.isDigit() }.take(2) },
                        label = { Text("Percent (1 to 99)") },
                        singleLine = true,
                        isError = !valid,
                        modifier = Modifier.padding(start = 48.dp).fillMaxWidth(),
                    )
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = kind == NotifKind.AllTimeUsed, onClick = { kind = NotifKind.AllTimeUsed })
                    Text("A user has used all of their time")
                }
            }
        },
        confirmButton = {
            Button(
                enabled = valid,
                onClick = {
                    onSave(NotifRule(NotifRules.newId(), kind, percent = if (kind == NotifKind.PercentUsed) percent ?: 0 else 0))
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** The rule's name as the Settings list shows it. */
fun ruleTitle(rule: NotifRule): String = when (rule.kind) {
    NotifKind.PercentUsed -> "When a user has used ${rule.percent}% of their time"
    NotifKind.AllTimeUsed -> "When a user has used all of their time"
}

/** What the rule does, in one line. */
fun ruleSubtitle(rule: NotifRule): String = when (rule.kind) {
    NotifKind.PercentUsed -> "Sent once for each user and limit until that limit resets. Playback is still allowed."
    NotifKind.AllTimeUsed -> "Playback stops. The alert has buttons to add 15, 30 or 60 minutes."
}
