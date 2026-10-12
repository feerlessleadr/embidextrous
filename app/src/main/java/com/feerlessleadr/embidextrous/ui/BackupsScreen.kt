package com.feerlessleadr.embidextrous.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.feerlessleadr.embidextrous.AppContainer
import com.feerlessleadr.embidextrous.data.ApiException
import com.feerlessleadr.embidextrous.data.BackupInfo
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BackupsUiState(
    val backups: List<BackupInfo> = emptyList(),
    val loading: Boolean = true,
    val busy: Boolean = false,
    val error: String? = null,
)

class BackupsViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(BackupsUiState())
    val state: StateFlow<BackupsUiState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val api = container.api() ?: return@launch
            _state.update { it.copy(error = null) }
            try {
                val backups = api.backups()
                _state.update { it.copy(backups = backups, loading = false) }
            } catch (e: ApiException) {
                failed(e) { msg -> _state.update { it.copy(loading = false, error = msg) } }
            }
        }
    }

    fun backUpNow() = run("Backup created") { createBackup() }

    fun delete(backup: BackupInfo) = run("Backup deleted") { deleteBackup(backup.filename) }

    fun restore(backup: BackupInfo, overwrite: Boolean) {
        viewModelScope.launch {
            val api = container.api() ?: return@launch
            _state.update { it.copy(busy = true) }
            try {
                val r = api.restoreBackup(backup.filename, overwrite)
                container.notifyListsChanged()
                _messages.emit("Restored ${r.restored}, overwrote ${r.overwritten}, skipped ${r.skipped}" + if (r.errors > 0) ", ${r.errors} with errors" else "")
            } catch (e: ApiException) {
                failed(e) { _messages.tryEmit(it) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    // Copies the backup file straight into the place the person chose. False when it failed (a message was shown).
    suspend fun download(backup: BackupInfo, out: java.io.OutputStream): Boolean {
        val api = container.api() ?: return false
        return try {
            api.downloadBackupTo(backup.filename, out)
            true
        } catch (e: ApiException) {
            failed(e) { _messages.tryEmit(it) }
            false
        }
    }

    fun say(message: String) {
        _messages.tryEmit(message)
    }

    private fun run(success: String, call: suspend com.feerlessleadr.embidextrous.data.ApiClient.() -> Unit) {
        viewModelScope.launch {
            val api = container.api() ?: return@launch
            _state.update { it.copy(busy = true) }
            try {
                api.call()
                _messages.emit(success)
                _state.update { it.copy(backups = api.backups()) }
            } catch (e: ApiException) {
                failed(e) { _messages.tryEmit(it) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    private fun failed(e: ApiException, show: (String) -> Unit) {
        if (e.isUnauthorized) container.signOut() else show(e.message ?: "Something went wrong.")
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_000_000 -> "%.1f MB".format(java.util.Locale.US, bytes / 1_000_000.0)
    bytes >= 1_000 -> "${bytes / 1_000} kB"
    else -> "$bytes B"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupsScreen(container: AppContainer, onBack: () -> Unit) {
    val vm: BackupsViewModel = viewModel(factory = viewModelFactory { initializer { BackupsViewModel(container) } })
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var restoring by remember { mutableStateOf<BackupInfo?>(null) }
    var deleting by remember { mutableStateOf<BackupInfo?>(null) }
    var saving by remember { mutableStateOf<BackupInfo?>(null) }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri: Uri? ->
        val backup = saving
        saving = null
        if (uri != null && backup != null) {
            scope.launch {
                try {
                    val saved = context.contentResolver.openOutputStream(uri)?.use { vm.download(backup, it) } ?: false
                    if (saved) vm.say("Saved ${backup.filename}")
                } catch (e: java.io.IOException) {
                    vm.say("Could not save the file: ${e.message}")
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Smart list backups") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { if (!state.busy) vm.backUpNow() },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Back up now") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.error != null -> Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = vm::load) { Text("Try again") }
                }
                state.backups.isEmpty() -> Text(
                    "No backups yet. Tap \"Back up now\" to save all your smart lists (and their images) on the server.",
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.backups, key = { it.filename }) { backup ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))) {
                            Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(Describe.dateTime(context, backup.createdAt), style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "${backup.listCount} ${if (backup.listCount == 1) "list" else "lists"} · ${formatSize(backup.sizeBytes)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = { restoring = backup }, enabled = !state.busy) { Icon(Icons.Default.Restore, contentDescription = "Restore") }
                                IconButton(onClick = { saving = backup; saveAs.launch(backup.filename) }, enabled = !state.busy) { Icon(Icons.Default.Download, contentDescription = "Save a copy to this phone") }
                                IconButton(onClick = { deleting = backup }, enabled = !state.busy) { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }
            }
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }

    restoring?.let { backup ->
        var overwrite by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { restoring = null },
            title = { Text("Restore this backup?") },
            text = {
                Column {
                    Text("Lists in the backup that are missing on the server come back. Lists that still exist are left as they are, unless you choose to overwrite them.")
                    Row(Modifier.fillMaxWidth().clickable { overwrite = !overwrite }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = overwrite, onCheckedChange = { overwrite = it })
                        Text("Overwrite lists that exist")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { restoring = null; vm.restore(backup, overwrite) }) { Text("Restore") } },
            dismissButton = { TextButton(onClick = { restoring = null }) { Text("Cancel") } },
        )
    }
    deleting?.let { backup ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete this backup?") },
            text = { Text("The backup file is removed from the server. Your lists are not touched.") },
            confirmButton = { TextButton(onClick = { deleting = null; vm.delete(backup) }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}