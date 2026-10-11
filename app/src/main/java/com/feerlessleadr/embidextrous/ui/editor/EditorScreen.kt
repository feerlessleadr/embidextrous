package com.feerlessleadr.embidextrous.ui.editor

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feerlessleadr.embidextrous.data.BumperDraft
import com.feerlessleadr.embidextrous.data.COLLECTION_ONLY_MEDIA_TYPES
import com.feerlessleadr.embidextrous.data.Catalog
import com.feerlessleadr.embidextrous.data.ListDraft
import com.feerlessleadr.embidextrous.data.MEDIA_TYPE_LABELS
import com.feerlessleadr.embidextrous.data.RANDOM_GROUP_FIELDS
import com.feerlessleadr.embidextrous.data.ScheduleDraft
import com.feerlessleadr.embidextrous.data.Safety
import com.feerlessleadr.embidextrous.data.SortDraft
import com.feerlessleadr.embidextrous.data.Swatch
import com.feerlessleadr.embidextrous.ui.SwatchDot
import com.feerlessleadr.embidextrous.ui.dotColor
import kotlinx.coroutines.launch
import com.feerlessleadr.embidextrous.data.Templates
import com.feerlessleadr.embidextrous.data.fieldsFor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: EditorViewModel, onClose: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val draft = state.draft
    val catalog = state.catalog
    val snackbar = remember { SnackbarHostState() }
    var confirmDiscard by remember { mutableStateOf(false) }
    val dirty = state.dirty
    // Leaving with unsaved changes asks first; leaving a form nobody touched does not.
    val leave = { if (dirty) confirmDiscard = true else onClose() }
    BackHandler(enabled = dirty) { confirmDiscard = true }

    // The form is long: show a problem where it is seen wherever the person has scrolled to.
    LaunchedEffect(state.saveError) {
        state.saveError?.let { snackbar.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(if (draft == null || draft.isNew) "New list" else "Edit list") },
                navigationIcon = {
                    IconButton(onClick = leave) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (draft != null) {
                        TextButton(onClick = { vm.save(onClose) }, enabled = !state.saving) {
                            if (state.saving) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (dirty) Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                                    Text(if (dirty) "  Save" else "Save")
                                }
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.loadError != null -> Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.loadError.orEmpty(), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = vm::load) { Text("Try again") }
                }

                state.choosingTemplate -> TemplateChooser(onPick = vm::chooseTemplate, onImport = vm::importShared)
                draft != null && catalog != null -> EditorForm(vm, state, draft, catalog)
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard your changes?") },
            text = { Text("This list has changes that are not saved.") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onClose() }) { Text("Discard", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } },
        )
    }
    state.preview?.let { PreviewSheet(it, onClose = vm::closePreview) }
}

@Composable
private fun TemplateChooser(onPick: (String) -> Unit, onImport: (String) -> String?) {
    var importing by remember { mutableStateOf(false) }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Start from", style = MaterialTheme.typography.titleMedium)
        Templates.all.forEach { template ->
            Card(Modifier.fillMaxWidth().clickable { onPick(template.id) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))) {
                Column(Modifier.padding(16.dp)) {
                    Text(template.title, style = MaterialTheme.typography.titleMedium)
                    Text(template.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Card(Modifier.fillMaxWidth().clickable { importing = true }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))) {
            Column(Modifier.padding(16.dp)) {
                Text("Import a shared list", style = MaterialTheme.typography.titleMedium)
                Text("Paste or open a list someone shared with you (Share… in a list''s menu).", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (importing) ImportDialog(onImport = onImport, onDismiss = { importing = false })
}

/** Paste the JSON of a shared list, or open it from a file. */
@Composable
private fun ImportDialog(onImport: (String) -> String?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { Safety.readImport(it) }
                if (bytes != null) text = bytes.decodeToString() else error = "That file is too large to be a shared list."
            } catch (e: java.io.IOException) {
                error = "Could not read that file."
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import a shared list") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; error = null },
                    label = { Text("The shared list") },
                    minLines = 4,
                    maxLines = 8,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
                        text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString().orEmpty()
                        error = null
                    }) { Text("Paste") }
                    TextButton(onClick = { open.launch(arrayOf("*/*")) }) { Text("Open file…") }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = {
                val problem = if (text.length > Safety.MAX_IMPORT_BYTES) "That is too large to be a shared list." else onImport(text)
                if (problem == null) onDismiss() else error = problem
            }) { Text("Import") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** What the list would contain right now, from the server: the total and the first items. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreviewSheet(preview: PreviewUiState, onClose: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when {
                preview.loading -> {
                    Text("Working out what matches…", style = MaterialTheme.typography.titleMedium)
                    CircularProgressIndicator(Modifier.padding(vertical = 16.dp))
                }
                preview.error != null -> {
                    Text("No preview", style = MaterialTheme.typography.titleMedium)
                    Text(preview.error, color = MaterialTheme.colorScheme.error)
                }
                preview.result != null -> {
                    val r = preview.result
                    Text(
                        when (r.total) {
                            0 -> "Nothing matches right now"
                            1 -> "1 item"
                            else -> "${r.total} items"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Column(Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        r.items.forEach { item ->
                            Column {
                                Text(item.name, style = MaterialTheme.typography.bodyLarge)
                                val sub = listOfNotNull(item.type.takeIf { it.isNotBlank() }, item.detail).joinToString(" · ")
                                if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (r.total > r.items.size) {
                            Text("…and ${r.total - r.items.size} more", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
@Composable
private fun Section(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

/** A section that folds away: for the less common options. [summary] says what is inside while it is closed. */
@Composable
private fun CollapsibleSection(title: String, summary: String, startOpen: Boolean, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    var open by rememberSaveable(title) { mutableStateOf(startOpen) }
    Column(modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                if (!open) Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = if (open) "Collapse" else "Expand")
        }
        AnimatedVisibility(open, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditorForm(vm: EditorViewModel, state: EditorUiState, draft: ListDraft, catalog: Catalog) {
    val fields = remember(catalog, draft.mediaTypes) { fieldsFor(catalog, draft.mediaTypes) }
    val setDraft: ((ListDraft) -> ListDraft) -> Unit = { change -> vm.update(change) }

    // Where each section starts inside the scrolled content, to jump to it and to show which one is in view.
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val offsets = remember { mutableStateMapOf<String, Int>() }
    fun Modifier.track(id: String): Modifier = onGloballyPositioned { offsets[id] = it.positionInParent().y.toInt() }
    val active by remember {
        derivedStateOf {
            if (scroll.maxValue > 0 && scroll.value >= scroll.maxValue - 8) {
                SECTION_TABS.last().first
            } else {
                SECTION_TABS.lastOrNull { (id, _) -> (offsets[id] ?: Int.MAX_VALUE) <= scroll.value + 120 }?.first ?: SECTION_TABS.first().first
            }
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
    SectionTabs(active = active, onPick = { id -> scope.launch { scroll.animateScrollTo(offsets[id] ?: 0) } })
    Column(
        Modifier.weight(1f).verticalScroll(scroll).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        state.saveError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }

        // ----- basics -----
        Section("Basics", Modifier.track("basics")) {
            if (draft.isNew) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !draft.isCollection,
                        onClick = { setDraft { d -> d.copy(type = "Playlist", mediaTypes = d.mediaTypes - COLLECTION_ONLY_MEDIA_TYPES) } },
                        label = { Text("Playlist") },
                    )
                    FilterChip(selected = draft.isCollection, onClick = { setDraft { it.copy(type = "Collection") } }, label = { Text("Collection") })
                }
            } else {
                Text(draft.type, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedTextField(
                value = draft.name,
                onValueChange = { v -> setDraft { it.copy(name = v) } },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            if (draft.isCollection) {
                DropdownField(
                    label = "Watch state of",
                    selectedText = state.users.firstOrNull { it.id.equals(draft.ownerId, ignoreCase = true) }?.name ?: "Choose a user",
                    options = state.users.map { it.id to it.name },
                    onSelect = { id -> setDraft { it.copy(ownerId = id) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "The rules about watched, favorite and similar use this user's data.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                PlaylistUsers(draft, state, setDraft)
            }

            SwitchRow("Enabled", "A disabled list keeps its settings but has no Emby list", draft.enabled) { on -> setDraft { it.copy(enabled = on) } }

            Text("List colour", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NoColorDot(selected = state.listColor == null, onClick = { vm.setListColor(null) })
                Swatch.entries.forEach { swatch -> SwatchDot(swatch, selected = state.listColor == swatch, onClick = { vm.setListColor(swatch) }, size = 32.dp) }
            }
            Text("Marks the list in the overview. Kept on this phone only.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // ----- media types -----
        Section("Media types") {
            val offered = MEDIA_TYPE_LABELS.filter { (value, _) ->
                (catalog.mediaTypes.isEmpty() || value in catalog.mediaTypes) && (draft.isCollection || value !in COLLECTION_ONLY_MEDIA_TYPES)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                offered.forEach { (value, label) ->
                    FilterChip(
                        selected = value in draft.mediaTypes,
                        onClick = { setDraft { d -> d.copy(mediaTypes = if (value in d.mediaTypes) d.mediaTypes - value else d.mediaTypes + value) } },
                        label = { Text(label) },
                    )
                }
            }
            if (!draft.isCollection) {
                Text(
                    "Series, seasons, albums and the like can only go in a collection.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ----- rules -----
        Section("Rules", Modifier.track("rules")) {
            RuleGroupsEditor(
                groups = draft.groups,
                fields = fields,
                catalog = catalog,
                libraries = state.libraries,
                users = state.users,
                emptyHint = "No rules: everything of the chosen media types is included.",
                onGroups = { change -> setDraft { d -> d.copy(groups = change(d.groups)) } },
            )
            OutlinedButton(onClick = vm::preview, enabled = state.preview?.loading != true) {
                Icon(Icons.Default.Visibility, contentDescription = null, Modifier.size(18.dp))
                Text("  Preview what matches")
            }
        }

        // ----- sorting -----
        Section("Sorting", Modifier.track("sorting")) {
            draft.sorts.forEachIndexed { index, sort ->
                key(sort.key) {
                    SortRow(
                        sort = sort,
                        catalog = catalog,
                        canRemove = draft.sorts.size > 1,
                        onChange = { changed -> setDraft { d -> d.copy(sorts = d.sorts.map { if (it.key == sort.key) changed else it }) } },
                        onRemove = { setDraft { d -> d.copy(sorts = d.sorts.filterNot { it.key == sort.key }) } },
                        label = if (index == 0) "Sort by" else "Then by",
                    )
                }
            }
            if (draft.sorts.size < 3) {
                TextButton(onClick = { setDraft { d -> d.copy(sorts = d.sorts + SortDraft(sortBy = "Name")) } }) {
                    Icon(Icons.Default.Add, contentDescription = null, Modifier.size(18.dp))
                    Text("  Add a sort")
                }
            }
        }

        // ----- limits -----
        Section("Limits", Modifier.track("limits")) {
            NumberField("Maximum items", draft.maxItems, "Empty means no limit") { v -> setDraft { it.copy(maxItems = v) } }
            NumberField("Maximum play time (minutes)", draft.maxPlayTimeMinutes, "Empty means no limit") { v -> setDraft { it.copy(maxPlayTimeMinutes = v) } }
            NumberField("Minimum items", draft.minItems, "The Emby list is hidden while fewer items match") { v -> setDraft { it.copy(minItems = v) } }
        }

        // ----- refresh -----
        Section("Refresh", Modifier.track("refresh")) {
            DropdownField(
                label = "Update the list",
                selectedText = catalog.autoRefreshModes.firstOrNull { it.value == draft.autoRefresh }?.label ?: draft.autoRefresh,
                options = catalog.autoRefreshModes.map { it.value to it.label },
                onSelect = { v -> setDraft { it.copy(autoRefresh = v) } },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        CollapsibleSection(
            title = "Schedules",
            summary = scheduleSummary(draft),
            startOpen = draft.schedules.isNotEmpty() || draft.visibilitySchedules.isNotEmpty(),
            modifier = Modifier.track("more"),
        ) {
            Text("Refresh at set times", style = MaterialTheme.typography.titleSmall)
            draft.schedules.forEach { s ->
                key(s.key) {
                    ScheduleCard(
                        schedule = s,
                        visibility = false,
                        onChange = { changed -> setDraft { d -> d.copy(schedules = d.schedules.map { if (it.key == s.key) changed else it }) } },
                        onDelete = { setDraft { d -> d.copy(schedules = d.schedules.filterNot { it.key == s.key }) } },
                    )
                }
            }
            TextButton(onClick = { setDraft { d -> d.copy(schedules = d.schedules + ScheduleDraft()) } }) {
                Icon(Icons.Default.Add, contentDescription = null, Modifier.size(18.dp))
                Text("  Add a refresh schedule")
            }
            Text("Show or hide the list at set times", style = MaterialTheme.typography.titleSmall)
            draft.visibilitySchedules.forEach { s ->
                key(s.key) {
                    ScheduleCard(
                        schedule = s,
                        visibility = true,
                        onChange = { changed -> setDraft { d -> d.copy(visibilitySchedules = d.visibilitySchedules.map { if (it.key == s.key) changed else it }) } },
                        onDelete = { setDraft { d -> d.copy(visibilitySchedules = d.visibilitySchedules.filterNot { it.key == s.key }) } },
                    )
                }
            }
            TextButton(onClick = { setDraft { d -> d.copy(visibilitySchedules = d.visibilitySchedules + ScheduleDraft(action = "Enable")) } }) {
                Icon(Icons.Default.Add, contentDescription = null, Modifier.size(18.dp))
                Text("  Add a show/hide schedule")
            }
        }

        CollapsibleSection(
            title = "On the Emby list",
            summary = presentationSummary(draft),
            startOpen = draft.sortTitle.isNotBlank() || draft.overview.isNotBlank() || draft.tags != null || draft.favorite != null,
        ) {
            OutlinedTextField(
                value = draft.sortTitle,
                onValueChange = { v -> setDraft { it.copy(sortTitle = v) } },
                label = { Text("Sort title") },
                supportingText = { Text("Empty: Emby sorts by the name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = draft.overview,
                onValueChange = { v -> setDraft { it.copy(overview = v) } },
                label = { Text("Description") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            TagsEditor(draft.tags) { tags -> setDraft { it.copy(tags = tags) } }
            Text("Favorite", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = draft.favorite == null, onClick = { setDraft { it.copy(favorite = null) } }, label = { Text("Leave alone") })
                FilterChip(selected = draft.favorite == true, onClick = { setDraft { it.copy(favorite = true) } }, label = { Text("Favorite") })
                FilterChip(selected = draft.favorite == false, onClick = { setDraft { it.copy(favorite = false) } }, label = { Text("Not favorite") })
            }
        }

        CollapsibleSection(
            title = "Advanced",
            summary = advancedSummary(draft),
            startOpen = draft.includeExtras || draft.matchByMembers || draft.groupIntoCollections || draft.randomGroup.enabled,
        ) {
            SwitchRow("Include extras", "Behind the scenes, trailers, deleted scenes and the like", draft.includeExtras) { on -> setDraft { it.copy(includeExtras = on) } }
            if (draft.mediaTypes.any { it == "Collection" || it == "Playlist" }) {
                SwitchRow("Match collections and playlists by their items", "Include one when any of its items passes the rules", draft.matchByMembers) { on -> setDraft { it.copy(matchByMembers = on) } }
            }
            if (draft.isCollection) {
                SwitchRow("Replace items with the collection they are in", "Items in no collection stay as they are", draft.groupIntoCollections) { on -> setDraft { it.copy(groupIntoCollections = on) } }
            }
            SwitchRow("Pick one group at random", "Keep only the items of one randomly chosen group, before sorting and limits", draft.randomGroup.enabled) { on ->
                setDraft { it.copy(randomGroup = it.randomGroup.copy(enabled = on, groupBy = it.randomGroup.groupBy ?: "SeriesName")) }
            }
            if (draft.randomGroup.enabled) {
                DropdownField(
                    label = "Group by",
                    selectedText = RANDOM_GROUP_FIELDS.firstOrNull { it.first == draft.randomGroup.groupBy }?.second ?: draft.randomGroup.groupBy.orEmpty(),
                    options = RANDOM_GROUP_FIELDS,
                    onSelect = { g -> setDraft { it.copy(randomGroup = it.randomGroup.copy(groupBy = g)) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                NumberField("Smallest group allowed", draft.randomGroup.minimumItems, "Empty: any size") { v -> setDraft { it.copy(randomGroup = it.randomGroup.copy(minimumItems = v)) } }
            }
            if (draft.hasSimilarRule) {
                Text("Compare \"similar to\" by", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.similarityFields.forEach { option ->
                        val on = option.value in draft.similarityFields
                        FilterChip(
                            selected = on,
                            onClick = { setDraft { d -> d.copy(similarityFields = if (on) d.similarityFields - option.value else d.similarityFields + option.value) } },
                            label = { Text(option.label) },
                        )
                    }
                }
                Text("None ticked: genre and tags", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (!draft.isCollection) {
            CollapsibleSection(
                title = "Bumpers",
                summary = if (draft.bumpers == null) "Off" else "On",
                startOpen = draft.bumpers != null,
            ) {
                BumpersEditor(draft, state, catalog, setDraft)
            }
        }

        if (!draft.isNew) {
            CollapsibleSection(
                title = "Images",
                summary = if (state.images.isEmpty()) "None" else "${state.images.size} custom image(s)",
                startOpen = state.images.isNotEmpty(),
            ) {
                ImagesSection(state, vm)
            }
        } else {
            Text("Save the list first, then edit it to add your own images.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Box(Modifier.size(48.dp))
    }
    }
}

/** The editor's sections, for the strip of shortcuts above the form. */
private val SECTION_TABS = listOf("basics" to "Basics", "rules" to "Rules", "sorting" to "Sorting", "limits" to "Limits", "refresh" to "Refresh", "more" to "More")

@Composable
private fun SectionTabs(active: String, onPick: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SECTION_TABS.forEach { (id, label) -> FilterChip(selected = id == active, onClick = { onPick(id) }, label = { Text(label) }) }
    }
}

/** The "no colour" choice of the colour row: an empty ring. */
@Composable
private fun NoColorDot(selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.Close, contentDescription = "No colour", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(16.dp))
    }
}

private fun scheduleSummary(d: ListDraft): String {
    val parts = d.schedules.map { "refresh " + describeSchedule(it) } + d.visibilitySchedules.map { (if (it.action == "Disable") "disable " else "enable ") + describeSchedule(it) }
    return if (parts.isEmpty()) "None" else parts.joinToString("; ")
}

private fun presentationSummary(d: ListDraft): String {
    val parts = listOfNotNull(
        d.sortTitle.takeIf { it.isNotBlank() }?.let { "sort title" },
        d.overview.takeIf { it.isNotBlank() }?.let { "description" },
        d.tags?.let { "tags" },
        d.favorite?.let { if (it) "favorite" else "not favorite" },
    )
    return if (parts.isEmpty()) "Nothing set" else parts.joinToString(", ")
}

private fun advancedSummary(d: ListDraft): String {
    val parts = listOfNotNull(
        if (d.includeExtras) "extras" else null,
        if (d.matchByMembers) "match by items" else null,
        if (d.groupIntoCollections) "group into collections" else null,
        if (d.randomGroup.enabled) "random group" else null,
    )
    return if (parts.isEmpty()) "Default" else parts.joinToString(", ")
}

/** Who a playlist is for: chosen users, everyone, and whether a one-user playlist is public. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlaylistUsers(draft: ListDraft, state: EditorUiState, setDraft: ((ListDraft) -> ListDraft) -> Unit) {
    SwitchRow(
        "A playlist for every user",
        "Each user, including new ones, gets their own, built from their own watch state",
        draft.allUsers,
    ) { on -> setDraft { it.copy(allUsers = on) } }

    if (!draft.allUsers) {
        Text("For these users", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.users.forEach { user ->
                val on = draft.users.any { it.equals(user.id, ignoreCase = true) }
                FilterChip(
                    selected = on,
                    onClick = { setDraft { d -> d.copy(users = if (on) d.users.filterNot { it.equals(user.id, ignoreCase = true) } else d.users + user.id) } },
                    label = { Text(user.name) },
                )
            }
        }
        if (draft.users.size == 1) {
            SwitchRow("Public", "Everyone on the server can see this playlist", draft.isPublic) { on -> setDraft { it.copy(isPublic = on) } }
        }
        if (!draft.isNew && draft.users.size > 1) {
            Text(
                "Removing a user deletes their playlist; adding one creates it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The tags put on the Emby list. Off means the plugin leaves the tags alone. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsEditor(tags: List<String>?, onChange: (List<String>?) -> Unit) {
    var typed by remember { mutableStateOf("") }
    SwitchRow("Manage tags on the Emby list", "Off: the plugin never touches the list's tags", tags != null) { on -> onChange(if (on) emptyList() else null) }
    if (tags != null) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tags.forEach { tag ->
                InputChip(
                    selected = false,
                    onClick = { onChange(tags - tag) },
                    label = { Text(tag) },
                    trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Remove $tag", modifier = Modifier.size(16.dp)) },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it },
                label = { Text("New tag") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                enabled = typed.isNotBlank(),
                onClick = {
                    val tag = typed.trim()
                    if (tag.isNotEmpty() && tags.none { it.equals(tag, ignoreCase = true) }) onChange(tags + tag)
                    typed = ""
                },
            ) { Text("Add") }
        }
    }
}

/** Items woven between a playlist's items (idents, interstitials), picked by their own rules. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BumpersEditor(draft: ListDraft, state: EditorUiState, catalog: Catalog, setDraft: ((ListDraft) -> ListDraft) -> Unit) {
    val bumpers = draft.bumpers
    SwitchRow(
        "Insert bumpers",
        "Short items, picked by their own rules, between the list's items. They repeat when there are fewer than needed and do not count toward the limits.",
        bumpers != null,
    ) { on -> setDraft { it.copy(bumpers = if (on) BumperDraft(mediaTypes = setOf("Video")) else null) } }
    if (bumpers == null) return

    val update: ((BumperDraft) -> BumperDraft) -> Unit = { change -> setDraft { d -> d.copy(bumpers = d.bumpers?.let(change)) } }

    Text("Bumper media type", style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MEDIA_TYPE_LABELS.filter { it.first !in COLLECTION_ONLY_MEDIA_TYPES }.forEach { (value, label) ->
            FilterChip(selected = value in bumpers.mediaTypes, onClick = { update { it.copy(mediaTypes = setOf(value)) } }, label = { Text(label) })
        }
    }
    DropdownField(
        label = "Bumper order",
        selectedText = listOf("Random" to "Random (reshuffled each refresh)", "Name" to "By name", "ReleaseDate" to "By release date").firstOrNull { it.first == bumpers.order }?.second ?: bumpers.order,
        options = listOf("Random" to "Random (reshuffled each refresh)", "Name" to "By name", "ReleaseDate" to "By release date"),
        onSelect = { o -> update { it.copy(order = o) } },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = bumpers.interval,
        onValueChange = { v -> update { it.copy(interval = v.filter(Char::isDigit)) } },
        label = { Text("One bumper after every N items") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
    Text("Which items are bumpers", style = MaterialTheme.typography.titleSmall)
    RuleGroupsEditor(
        groups = bumpers.groups,
        fields = fieldsFor(catalog, bumpers.mediaTypes),
        catalog = catalog,
        libraries = state.libraries,
        users = state.users,
        emptyHint = "No rules: every item of that media type is a bumper.",
        onGroups = { change -> update { it.copy(groups = change(it.groups)) } },
    )
}

@Composable
private fun NumberField(label: String, value: String, hint: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter(Char::isDigit)) },
        label = { Text(label) },
        supportingText = { Text(hint) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SortRow(
    sort: SortDraft,
    catalog: Catalog,
    canRemove: Boolean,
    onChange: (SortDraft) -> Unit,
    onRemove: () -> Unit,
    label: String,
) {
    val info = catalog.sorts.firstOrNull { it.value == sort.sortBy }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PickerField(
                    label = label,
                    selectedText = (info?.label ?: sort.sortBy).substringBefore(" ("),
                    title = "Choose a sort",
                    items = catalog.sorts.map { PickerItem(it.value, it.label, it.group) },
                    selected = sort.sortBy,
                    onPick = { value ->
                        val picked = catalog.sorts.firstOrNull { it.value == value }
                        onChange(
                            sort.copy(
                                sortBy = value,
                                groupBy = if (picked?.roundRobin == true) sort.groupBy ?: catalog.roundRobinGroupByFields.firstOrNull()?.value else null,
                                within = if (picked?.roundRobin == true) sort.within else null,
                            ),
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
                if (canRemove) {
                    IconButton(onClick = onRemove) { Icon(Icons.Default.Delete, contentDescription = "Remove sort", tint = MaterialTheme.colorScheme.error) }
                }
            }
            if (info?.directionless != true) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !sort.descending, onClick = { onChange(sort.copy(descending = false)) }, label = { Text("Ascending") })
                    FilterChip(selected = sort.descending, onClick = { onChange(sort.copy(descending = true)) }, label = { Text("Descending") })
                }
            }
            if (info?.roundRobin == true) {
                DropdownField(
                    label = "Group by",
                    selectedText = catalog.roundRobinGroupByFields.firstOrNull { it.value == sort.groupBy }?.label ?: sort.groupBy.orEmpty(),
                    options = catalog.roundRobinGroupByFields.map { it.value to it.label },
                    onSelect = { onChange(sort.copy(groupBy = it)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                DropdownField(
                    label = "Order inside each group",
                    selectedText = (catalog.withinGroupOrders.firstOrNull { it.value == (sort.within ?: "Natural") }?.label ?: "Natural order").substringBefore(" ("),
                    options = catalog.withinGroupOrders.map { it.value to it.label },
                    onSelect = { onChange(sort.copy(within = if (it == "Natural") null else it)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
