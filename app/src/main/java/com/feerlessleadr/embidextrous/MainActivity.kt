package com.feerlessleadr.embidextrous

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.feerlessleadr.embidextrous.ui.BackupsScreen
import com.feerlessleadr.embidextrous.ui.EmbidextrousTheme
import com.feerlessleadr.embidextrous.ui.ListsScreen
import com.feerlessleadr.embidextrous.ui.ListsViewModel
import com.feerlessleadr.embidextrous.ui.SettingsScreen
import com.feerlessleadr.embidextrous.ui.SignInScreen
import com.feerlessleadr.embidextrous.ui.SignInViewModel
import com.feerlessleadr.embidextrous.ui.editor.EditorScreen
import com.feerlessleadr.embidextrous.ui.editor.EditorViewModel
import com.feerlessleadr.embidextrous.ui.useDarkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as App).container
        setContent {
            val mode by container.settings.themeMode.collectAsStateWithLifecycle()
            val accent by container.settings.accent.collectAsStateWithLifecycle()
            val dynamic by container.settings.dynamicColor.collectAsStateWithLifecycle()
            val dark = useDarkTheme(mode)
            // Keep the status and navigation bar icons readable when the chosen theme differs from the phone's.
            LaunchedEffect(dark) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
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
    if (session == null) {
        val vm: SignInViewModel = viewModel(factory = viewModelFactory { initializer { SignInViewModel(container) } })
        SignInScreen(vm)
    } else {
        SignedIn(container, session?.token.orEmpty())
    }
}

@Composable
private fun SignedIn(container: AppContainer, token: String) {
    // A new set of view models per signed-in session, so signing in as someone else never shows the previous lists.
    val nav = rememberNavController()
    val listsVm: ListsViewModel = viewModel(key = "lists-$token", factory = viewModelFactory { initializer { ListsViewModel(container) } })

    NavHost(navController = nav, startDestination = "lists") {
        composable("lists") {
            ListsScreen(
                vm = listsVm,
                container = container,
                onNew = { nav.navigate("editor/new") },
                onEdit = { id -> nav.navigate("editor/$id") },
                onDuplicate = { id -> nav.navigate("editor/copy-$id") },
                onSettings = { nav.navigate("settings") },
            )
        }
        composable("editor/{id}") { entry ->
            val raw = entry.arguments?.getString("id").orEmpty()
            // "copy-<id>" starts a new list from an existing one; "new" starts from a template; anything else edits that list.
            val copyOf = raw.removePrefix("copy-").takeIf { raw.startsWith("copy-") }
            val id = raw.takeIf { it != "new" && copyOf == null }
            val vm: EditorViewModel = viewModel(
                key = "editor-$token-$raw",
                factory = viewModelFactory { initializer { EditorViewModel(container, id, copyOf) } },
            )
            EditorScreen(vm, onClose = { nav.popBackStack() })
        }
        composable("settings") {
            SettingsScreen(container, onBack = { nav.popBackStack() }, onBackups = { nav.navigate("backups") })
        }
        composable("backups") {
            BackupsScreen(container, onBack = { nav.popBackStack() })
        }
    }
}
