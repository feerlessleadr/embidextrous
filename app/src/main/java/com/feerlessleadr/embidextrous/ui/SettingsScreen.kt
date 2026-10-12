package com.feerlessleadr.embidextrous.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feerlessleadr.embidextrous.AppContainer
import com.feerlessleadr.embidextrous.BuildConfig
import com.feerlessleadr.embidextrous.data.AppProfile
import com.feerlessleadr.embidextrous.data.Density
import com.feerlessleadr.embidextrous.data.Profiles
import com.feerlessleadr.embidextrous.data.Swatch
import com.feerlessleadr.embidextrous.data.ThemeMode

/** One group of settings: a rounded card with an icon and a title. */
@Composable
private fun SettingsCard(title: String, icon: ImageVector, tint: Color, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconBadge(icon, tint, size = 40.dp)
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(container: AppContainer, onBackups: () -> Unit) {
    val theme by container.settings.themeMode.collectAsStateWithLifecycle()
    val accent by container.settings.accent.collectAsStateWithLifecycle()
    val dynamic by container.settings.dynamicColor.collectAsStateWithLifecycle()
    val density by container.settings.density.collectAsStateWithLifecycle()
    val session by container.session.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsCard("Appearance", Icons.Default.Palette, MaterialTheme.colorScheme.primary) {
                ThemeMode.entries.forEach { mode ->
                    Row(
                        Modifier.fillMaxWidth().clickable { container.settings.setThemeMode(mode) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = theme == mode, onClick = { container.settings.setThemeMode(mode) })
                        Text(mode.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                Text("Accent colour", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Swatch.entries.forEach { swatch ->
                        SwatchDot(swatch, selected = !dynamic && accent == swatch, onClick = {
                            container.settings.setDynamicColor(false)
                            container.settings.setAccent(swatch)
                        })
                    }
                }
                Text(
                    if (dynamic) "Using your wallpaper colours." else accent.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (dynamicColorSupported) {
                    Row(Modifier.fillMaxWidth().clickable { container.settings.setDynamicColor(!dynamic) }, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Match my wallpaper", style = MaterialTheme.typography.bodyLarge)
                            Text("Material You: the colours follow your phone's wallpaper", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = dynamic, onCheckedChange = container.settings::setDynamicColor)
                    }
                }
            }

            if (session?.isAdministrator == true) {
                ProfileSection(container)
            }

            WatchingEyeKeySection(container)

            val profile by container.settings.profile.collectAsStateWithLifecycle()
            val customSmartLists by container.settings.customSmartLists.collectAsStateWithLifecycle()
            if (Profiles.showsSmartLists(profile, session?.isAdministrator == true, customSmartLists)) {
                SettingsCard("Smart lists", Icons.AutoMirrored.Filled.PlaylistPlay, MaterialTheme.colorScheme.primary) {
                    Text("Overview", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Density.entries.forEach { d ->
                        Row(Modifier.fillMaxWidth().clickable { container.settings.setDensity(d) }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = density == d, onClick = { container.settings.setDensity(d) })
                            Text(d.label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    Row(Modifier.fillMaxWidth().clickable(onClick = onBackups).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Backup, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                            Text("Smart list backups", style = MaterialTheme.typography.titleMedium)
                            Text("Back up, restore or save a copy of your smart lists. This is not a backup of the app or of Emby itself.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                }
            }

            SettingsCard("Account", Icons.Default.Person, MaterialTheme.colorScheme.primary) {
                Text(session?.userName.orEmpty(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Pill(
                    if (session?.isAdministrator == true) "Administrator: every tool" else "Regular account: Hall Monitor only",
                    if (session?.isAdministrator == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary,
                )
                Text(session?.serverUrl.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = container::signOut, modifier = Modifier.padding(top = 6.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                    Text("  Sign out")
                }
                Text(
                    "Signing out ends this session on the server and removes the access token from this phone. If the server cannot be reached, you can revoke this device in Emby (Dashboard, Devices).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SettingsCard("About", Icons.Default.Info, MaterialTheme.colorScheme.primary) {
                Text("Embidextrous ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Watch-time limits and smart lists for your Emby server, from one app. Open source (MIT): github.com/feerlessleadr/embidextrous",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** One colour of the palette: a filled circle with a check when chosen. */
@Composable
fun SwatchDot(swatch: Swatch, selected: Boolean, onClick: () -> Unit, size: androidx.compose.ui.unit.Dp = 40.dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(swatch.dotColor())
            .let { if (selected) it.then(Modifier.padding(0.dp)) else it }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Default.Check, contentDescription = "${swatch.label}, selected", tint = Color.White, modifier = Modifier.size(size * 0.55f))
    }
}

/** How this phone uses the app: which tabs and Home cards it shows. Not a lock: it can be changed here at any time. */
@Composable
private fun ProfileSection(container: AppContainer) {
    val profile by container.settings.profile.collectAsStateWithLifecycle()
    val customSmartLists by container.settings.customSmartLists.collectAsStateWithLifecycle()
    SettingsCard("How this phone uses the app", Icons.Default.PhoneAndroid, MaterialTheme.colorScheme.primary) {
        AppProfile.entries.forEach { p ->
            Row(Modifier.fillMaxWidth().clickable { container.settings.setProfile(p) }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = profile == p, onClick = { container.settings.setProfile(p) })
                Column(Modifier.padding(vertical = 4.dp)) {
                    Text(p.label, style = MaterialTheme.typography.bodyLarge)
                    Text(p.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (profile == AppProfile.Custom) {
            SettingRow(
                title = "Show the Smart Lists tab",
                subtitle = "Needs an Emby administrator account.",
                checked = customSmartLists,
                onChange = container.settings::setCustomSmartLists,
            )
        }
    }
}

/** The Hall Monitor plugin's key: shown as "set" or "not set"; a new one can be entered or the key removed. */
@Composable
private fun WatchingEyeKeySection(container: AppContainer) {
    val key by container.pluginKey.collectAsStateWithLifecycle()
    var text by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    SettingsCard("Hall Monitor", Icons.Default.Timer, MaterialTheme.colorScheme.primary) {
        Text(
            if (key == null) "Not set. Paste the key from the plugin page (External API tab)." else "Key is set (stored encrypted on this phone).",
            style = MaterialTheme.typography.bodyMedium,
        )
        androidx.compose.material3.OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(if (key == null) "Hall Monitor key" else "Replace the key") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.Button(onClick = { container.setPluginKey(text); text = "" }, enabled = text.isNotBlank()) { Text("Save key") }
            if (key != null) OutlinedButton(onClick = { container.setPluginKey("") }) { Text("Remove key") }
        }

        val context = androidx.compose.ui.platform.LocalContext.current
        val alerts by container.settings.alertsEnabled.collectAsStateWithLifecycle()
        val hideNames by container.settings.widgetHideNames.collectAsStateWithLifecycle()
        var permissionDenied by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
        // Android 13 and later ask before an app may show notifications; alerts only turn on once that is granted.
        val askPermission = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
            permissionDenied = !granted
            container.setAlertsEnabled(granted)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        SettingRow(
            title = "Background alerts",
            subtitle = if (permissionDenied) "Notifications are blocked for this app, so alerts cannot be turned on."
            else "Keep a live connection open and notify when someone reaches their limit, with buttons to add time. Shows a small ongoing notification.",
            checked = alerts,
            onChange = { on ->
                if (on && !com.feerlessleadr.embidextrous.watching.AlertService.canNotify(context)) {
                    askPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    permissionDenied = false
                    container.setAlertsEnabled(on)
                }
            },
        )
        if (alerts) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Some phones stop background apps to save battery. If alerts stop arriving, allow Embidextrous to run without battery restrictions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                androidx.compose.material3.TextButton(onClick = {
                    try {
                        context.startActivity(android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (e: android.content.ActivityNotFoundException) {
                        // This phone has no such screen; the text above still tells what to look for.
                    }
                }) { Text("Open battery settings") }
            }
        }
        SettingRow(
            title = "Hide names on the widget",
            subtitle = "The home-screen widget shows \"User 1\", \"User 2\" instead of names, in case others can see your home screen.",
            checked = hideNames,
            onChange = container.settings::setWidgetHideNames,
        )
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
