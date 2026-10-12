package com.feerlessleadr.embidextrous

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.material.icons.filled.Home
import com.feerlessleadr.embidextrous.data.AppProfile
import com.feerlessleadr.embidextrous.data.Profiles
import com.feerlessleadr.embidextrous.data.Session
import com.feerlessleadr.embidextrous.home.AdminHome
import com.feerlessleadr.embidextrous.home.AdminHomeViewModel
import com.feerlessleadr.embidextrous.home.CustomiseHomeScreen
import com.feerlessleadr.embidextrous.home.HomeScreen
import com.feerlessleadr.embidextrous.server.ActivityScreen
import com.feerlessleadr.embidextrous.server.DevicesScreen
import com.feerlessleadr.embidextrous.server.LibrariesScreen
import com.feerlessleadr.embidextrous.server.LiveScreen
import com.feerlessleadr.embidextrous.server.PluginsTab
import com.feerlessleadr.embidextrous.server.ServerHeader
import com.feerlessleadr.embidextrous.server.ServerToolsViewModel
import com.feerlessleadr.embidextrous.server.TasksScreen
import com.feerlessleadr.embidextrous.ui.ComingSoon
import com.feerlessleadr.embidextrous.ui.GroupTabs
import com.feerlessleadr.embidextrous.ui.SubTab
import com.feerlessleadr.embidextrous.ui.ProfileChoiceScreen
import com.feerlessleadr.embidextrous.ui.BackupsScreen
import com.feerlessleadr.embidextrous.ui.EmbidextrousTheme
import com.feerlessleadr.embidextrous.ui.DarkBackdrops
import com.feerlessleadr.embidextrous.ui.ListsScreen
import com.feerlessleadr.embidextrous.ui.ListsViewModel
import com.feerlessleadr.embidextrous.ui.SettingsScreen
import com.feerlessleadr.embidextrous.ui.SignInScreen
import com.feerlessleadr.embidextrous.ui.SignInViewModel
import com.feerlessleadr.embidextrous.ui.editor.EditorScreen
import com.feerlessleadr.embidextrous.ui.editor.EditorViewModel
import com.feerlessleadr.embidextrous.ui.useDarkTheme
import com.feerlessleadr.embidextrous.watching.AlertService
import com.feerlessleadr.embidextrous.watching.WatchingEyeScreen
import com.feerlessleadr.embidextrous.watching.WatchingEyeTheme
import com.feerlessleadr.embidextrous.watching.WatchingEyeViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as App).container
        // Start (or stop) the optional background alerts to match the setting; the app is in front here, so Android allows it.
        AlertService.sync(this)
        setContent {
            val mode by container.settings.themeMode.collectAsStateWithLifecycle()
            val accent by container.settings.accent.collectAsStateWithLifecycle()
            val dynamic by container.settings.dynamicColor.collectAsStateWithLifecycle()
            val dark = useDarkTheme(mode)
            // Keep the status and navigation bar icons readable when the chosen theme differs from the phone's.
            val onDarkScreen = DarkBackdrops.count > 0
            LaunchedEffect(dark, onDarkScreen) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark || onDarkScreen }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            EmbidextrousTheme(mode, accent, dynamic) {
                Root(container)
            }
        }
    }
}

@Composable
private fun Root(container: AppContainer) {
    val session by container.session.collectAsStateWithLifecycle()
    val current = session
    if (current == null) {
        val vm: SignInViewModel = viewModel(factory = viewModelFactory { initializer { SignInViewModel(container) } })
        SignInScreen(vm)
    } else {
        val profile by container.settings.profile.collectAsStateWithLifecycle()
        if (profile == null && current.isAdministrator) {
            // An administrator account is asked once how this phone uses the app (the partner's phone may share the same account).
            ProfileChoiceScreen(onChosen = { chosen, smartLists ->
                container.settings.setCustomSmartLists(smartLists)
                container.settings.setProfile(chosen)
            })
        } else {
            // A regular account can only use Hall Monitor, so there is nothing to ask.
            if (profile == null) LaunchedEffect(Unit) { container.settings.setProfile(AppProfile.Parent) }
            SignedIn(container, current)
        }
    }
}

/** One tab of the bottom bar. */
private data class Tab(val route: String, val label: String, val icon: ImageVector)

private const val ROUTE_HOME = "home"
private const val ROUTE_WATCHING = "watching"
private const val ROUTE_LIBRARY = "library"
private const val ROUTE_SERVER = "server"
private const val ROUTE_SETTINGS = "settings"

@Composable
private fun SignedIn(container: AppContainer, session: Session) {
    // A new set of view models per signed-in session, so signing in as someone else never shows the previous data.
    val token = session.token
    val nav = rememberNavController()
    val listsVm: ListsViewModel = viewModel(key = "lists-$token", factory = viewModelFactory { initializer { ListsViewModel(container) } })
    val watchVm: WatchingEyeViewModel = viewModel(key = "watching-$token", factory = viewModelFactory { initializer { WatchingEyeViewModel(container) } })
    val adminVm: AdminHomeViewModel = viewModel(key = "admin-$token", factory = viewModelFactory { initializer { AdminHomeViewModel(container) } })
    val toolsVm: ServerToolsViewModel = viewModel(key = "tools-$token", factory = viewModelFactory { initializer { ServerToolsViewModel(container) } })

    // The tabs follow the phone's profile; Smart Lists also needs an administrator account (the SmartLists plugin demands one).
    val profile by container.settings.profile.collectAsStateWithLifecycle()
    val customSmartLists by container.settings.customSmartLists.collectAsStateWithLifecycle()
    val smartLists = Profiles.showsSmartLists(profile, session.isAdministrator, customSmartLists)
    val adminCards = Profiles.showsAdminCards(profile, session.isAdministrator, customSmartLists)
    val homeLayout by container.settings.homeLayout.collectAsStateWithLifecycle()
    val tabs = buildList {
        add(Tab(ROUTE_HOME, "Home", Icons.Filled.Home))
        add(Tab(ROUTE_WATCHING, "Watching", Icons.Filled.Visibility))
        if (smartLists) add(Tab(ROUTE_LIBRARY, "Library", Icons.Filled.VideoLibrary))
        if (adminCards) add(Tab(ROUTE_SERVER, "Server", Icons.Filled.Dns))
        add(Tab(ROUTE_SETTINGS, "Settings", Icons.Filled.Settings))
    }
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = tabs.any { it.route == route }
    val goTo: (String) -> Unit = { target ->
        nav.navigate(target) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        bottomBar = {
            if (showBar) {
                // On the Hall Monitor tab the bar matches the calm look of the cards; elsewhere it keeps the accent.
                val bar: @Composable () -> Unit = {
                    NavigationBar {
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                selected = route == tab.route,
                                onClick = { goTo(tab.route) },
                                icon = { Icon(tab.icon, contentDescription = null) },
                                label = { Text(tab.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                ),
                            )
                        }
                    }
                }
                if (route == ROUTE_WATCHING || route == ROUTE_HOME) WatchingEyeTheme(bar) else bar()
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = ROUTE_HOME,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable(ROUTE_HOME) {
                HomeScreen(
                    vm = watchVm,
                    admin = if (adminCards) AdminHome(listsVm, adminVm, container.smartListsProblem, onOpenLists = { goTo(ROUTE_LIBRARY) }, onOpenServer = { goTo(ROUTE_SERVER) }) else null,
                    layout = homeLayout,
                    onCustomise = { nav.navigate("customise-home") },
                    onOpenWatching = { goTo(ROUTE_WATCHING) },
                    onOpenSettings = { goTo(ROUTE_SETTINGS) },
                )
            }
            composable("customise-home") {
                CustomiseHomeScreen(container, administrator = adminCards, onBack = { nav.popBackStack() })
            }
            composable(ROUTE_WATCHING) {
                var section by rememberSaveable { mutableIntStateOf(0) }
                GroupTabs(
                    tabs = buildList {
                        add(SubTab("Hall Monitor") { WatchingEyeScreen(watchVm) })
                        if (BuildConfig.DEBUG) add(SubTab("Activity") { ComingSoon(Icons.Filled.Timeline, "Activity from Tracearr", "History, trends and violations for each child, from your Tracearr server.") })
                    },
                    selected = section,
                    onSelect = { section = it },
                )
            }
            composable(ROUTE_LIBRARY) {
                var section by rememberSaveable { mutableIntStateOf(0) }
                if (!smartLists) {
                    NotForRegularAccounts()
                } else {
                    GroupTabs(
                        tabs = buildList {
                            add(
                                SubTab("Smart lists") {
                                    SmartListsTab(container) {
                                        ListsScreen(
                                            vm = listsVm,
                                            container = container,
                                            onNew = { nav.navigate("editor/new") },
                                            onEdit = { id -> nav.navigate("editor/$id") },
                                            onDuplicate = { id -> nav.navigate("editor/copy-$id") },
                                        )
                                    }
                                },
                            )
                            add(SubTab("Libraries") { LibrariesScreen(toolsVm) })
                            if (BuildConfig.DEBUG) add(SubTab("Tags") { ComingSoon(Icons.Filled.Sell, "Bulk tag editing", "Edit tags for many items at once, including music.") })
                        },
                        selected = section,
                        onSelect = { section = it },
                    )
                }
            }
            composable(ROUTE_SERVER) {
                var section by rememberSaveable { mutableIntStateOf(0) }
                GroupTabs(
                    tabs = buildList {
                        add(SubTab("Live") { LiveScreen(toolsVm) })
                        add(SubTab("Tasks") { TasksScreen(toolsVm) })
                        add(SubTab("Activity") { ActivityScreen(toolsVm) })
                        add(SubTab("Devices") { DevicesScreen(toolsVm, thisDeviceId = container.deviceId()) })
                        add(SubTab("Plugins") { PluginsTab(adminVm) })
                    },
                    selected = section,
                    onSelect = { section = it },
                    header = { ServerHeader(adminVm, toolsVm) },
                )
            }
            composable("editor/{id}") { backEntry ->
                val raw = backEntry.arguments?.getString("id").orEmpty()
                // "copy-<id>" starts a new list from an existing one; "new" starts from a template; anything else edits that list.
                val copyOf = raw.removePrefix("copy-").takeIf { raw.startsWith("copy-") }
                val id = raw.takeIf { it != "new" && copyOf == null }
                val vm: EditorViewModel = viewModel(
                    key = "editor-$token-$raw",
                    factory = viewModelFactory { initializer { EditorViewModel(container, id, copyOf) } },
                )
                EditorScreen(vm, onClose = { nav.popBackStack() })
            }
            composable(ROUTE_SETTINGS) {
                SettingsScreen(container, onBackups = { nav.navigate("backups") })
            }
            composable("backups") {
                BackupsScreen(container, onBack = { nav.popBackStack() })
            }
        }
    }
}

/** Shows [content] when the SmartLists plugin is usable, and otherwise says what is wrong, with a way to look again. */
@Composable
private fun SmartListsTab(container: AppContainer, content: @Composable () -> Unit) {
    val problem by container.smartListsProblem.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val message = problem
    if (message == null) {
        content()
    } else {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Smart Lists is not available", style = MaterialTheme.typography.titleLarge)
            Text(message, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = { scope.launch { container.checkSmartLists() } }) { Text("Check again") }
        }
    }
}

@Composable
private fun NotForRegularAccounts() {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text("Smart lists need an Emby administrator account.", style = MaterialTheme.typography.bodyLarge)
    }
}
