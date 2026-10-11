package com.feerlessleadr.embidextrous.ui.editor

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.feerlessleadr.embidextrous.data.Catalog
import com.feerlessleadr.embidextrous.data.EmbyUser
import com.feerlessleadr.embidextrous.data.FieldInfo
import com.feerlessleadr.embidextrous.data.RuleDraft
import com.feerlessleadr.embidextrous.data.toJson
import com.feerlessleadr.embidextrous.ui.Describe
import com.feerlessleadr.embidextrous.data.ValueOption
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private val CATEGORY_LABELS = mapOf(
    "Content" to "Content",
    "Video" to "Video",
    "Audio" to "Audio",
    "RatingsPlayback" to "Ratings & playback",
    "File" to "File",
    "Library" to "Library",
    "People" to "People",
    "PeopleSubFields" to "People",
    "Collection" to "Collections",
)

fun fieldPickerItems(fields: List<FieldInfo>): List<PickerItem> =
    fields.map { PickerItem(it.name, it.label, CATEGORY_LABELS[it.category] ?: it.category) }
        .sortedWith(compareBy({ CATEGORY_LABELS.values.distinct().indexOf(it.group) }, { it.label.lowercase() }))

/** A new rule for [field] with sensible starting values. */
fun newRule(field: FieldInfo): RuleDraft {
    val operator = field.operators.firstOrNull { it == "Equal" } ?: field.operators.firstOrNull().orEmpty()
    return RuleDraft(field = field.name, operator = operator, value = if (field.type == "Boolean") "true" else "")
}

private enum class OperatorKind { Multi, RelativeDate, Weekday, Plain }

private fun kindOf(operator: String) = when (operator) {
    "IsIn", "IsNotIn" -> OperatorKind.Multi
    "NewerThan", "OlderThan" -> OperatorKind.RelativeDate
    "Weekday" -> OperatorKind.Weekday
    else -> OperatorKind.Plain
}

/**
 * One rule: field, operator, a value input that fits them, and the field-specific switches. Folded, it is one line
 * in words ("Date Added newer than 30 days"); [expanded] opens the inputs.
 */
@Composable
fun RuleCard(
    rule: RuleDraft,
    catalog: Catalog,
    fields: List<FieldInfo>,
    libraries: List<ValueOption>,
    users: List<EmbyUser>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onChange: (RuleDraft) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    val info = catalog.field(rule.field)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth().animateContentSize()) {
        if (!expanded) {
            val incomplete = rule.value.isBlank() && info?.type != "Boolean"
            Row(Modifier.clickable(onClick = onToggle).padding(start = 14.dp, top = 4.dp, bottom = 4.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (incomplete) "${catalog.fieldLabel(rule.field)} (needs a value)" else Describe.rule(rule.toJson(), catalog),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (incomplete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                )
                Icon(Icons.Default.ExpandMore, contentDescription = "Edit rule")
                IconButton(onClick = onDuplicate) { Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate rule") }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Remove rule", tint = MaterialTheme.colorScheme.error) }
            }
            return@Card
        }
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PickerField(
                    label = "Field",
                    selectedText = catalog.fieldLabel(rule.field),
                    title = "Choose a field",
                    items = fieldPickerItems(fields.ifEmpty { catalog.fields }),
                    selected = rule.field,
                    onPick = { name ->
                        if (name != rule.field) catalog.field(name)?.let { f -> onChange(newRule(f).copy(key = rule.key)) }
                    },
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onToggle) { Icon(Icons.Default.ExpandLess, contentDescription = "Fold rule") }
                IconButton(onClick = onDuplicate) { Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate rule") }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Remove rule", tint = MaterialTheme.colorScheme.error) }
            }

            if (info == null) {
                Text("This field is not known to the plugin version on your server; it is kept as it is.", style = MaterialTheme.typography.bodySmall)
                return@Column
            }

            DropdownField(
                label = "Condition",
                selectedText = catalog.operatorLabel(rule.operator),
                options = info.operators.map { it to catalog.operatorLabel(it) },
                onSelect = { op ->
                    val reset = kindOf(op) != kindOf(rule.operator)
                    onChange(rule.copy(operator = op, value = if (reset) "" else rule.value))
                },
                modifier = Modifier.fillMaxWidth(),
            )

            RuleValueInput(rule, info, catalog, libraries, onChange)

            RuleExtras(rule, info, users, onChange)

            if (rule.field == "NextUnwatched") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Include shows I have not started", style = MaterialTheme.typography.bodyMedium)
                        Text("Off: only shows you are part-way through", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = rule.extraBool("IncludeUnwatchedSeries") != false,
                        onCheckedChange = { include -> onChange(rule.withExtra("IncludeUnwatchedSeries", if (include) null else JsonPrimitive(false))) },
                    )
                }
            }
        }
    }
}
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun RuleValueInput(
    rule: RuleDraft,
    info: FieldInfo,
    catalog: Catalog,
    libraries: List<ValueOption>,
    onChange: (RuleDraft) -> Unit,
) {
    val kind = kindOf(rule.operator)
    val set: (String) -> Unit = { onChange(rule.copy(value = it)) }
    val selectedValues = rule.value.split(';').map { it.trim() }.filter { it.isNotEmpty() }

    // Choices to tick: library names for LibraryName, the fixed values of a field otherwise.
    val choices: List<ValueOption>? = when {
        info.name == "LibraryName" && libraries.isNotEmpty() -> libraries
        info.values != null -> info.values
        else -> null
    }

    when {
        choices != null && kind == OperatorKind.Multi -> {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                choices.forEach { choice ->
                    val on = selectedValues.any { it.equals(choice.value, ignoreCase = true) }
                    FilterChip(
                        selected = on,
                        onClick = {
                            val next = if (on) selectedValues.filterNot { it.equals(choice.value, ignoreCase = true) } else selectedValues + choice.value
                            set(next.joinToString("; "))
                        },
                        label = { Text(choice.label.substringBefore(" (")) },
                    )
                }
            }
        }

        choices != null -> DropdownField(
            label = "Value",
            selectedText = choices.firstOrNull { it.value.equals(rule.value, ignoreCase = true) }?.label ?: rule.value,
            options = choices.map { it.value to it.label },
            onSelect = set,
            modifier = Modifier.fillMaxWidth(),
        )

        info.type == "Boolean" -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = rule.value.equals("true", true), onClick = { set("true") }, label = { Text("Yes") })
            FilterChip(selected = rule.value.equals("false", true), onClick = { set("false") }, label = { Text("No") })
        }

        info.type == "Date" && kind == OperatorKind.RelativeDate -> {
            val number = rule.value.substringBefore(':', "")
            val unit = rule.value.substringAfter(':', "").ifEmpty { "days" }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                OutlinedTextField(
                    value = number,
                    onValueChange = { n -> set(if (n.isEmpty()) "" else "${n.filter(Char::isDigit)}:$unit") },
                    label = { Text("Number") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                DropdownField(
                    label = "Unit",
                    selectedText = catalog.relativeDateUnits.firstOrNull { it.value == unit }?.label ?: unit,
                    options = catalog.relativeDateUnits.map { it.value to it.label },
                    onSelect = { u -> set("${number.ifEmpty { "1" }}:$u") },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        info.type == "Date" && kind == OperatorKind.Weekday -> DropdownField(
            label = "Day",
            selectedText = catalog.weekdays.firstOrNull { it.value == rule.value }?.label ?: "",
            options = catalog.weekdays.map { it.value to it.label },
            onSelect = set,
            modifier = Modifier.fillMaxWidth(),
        )

        info.type == "Date" -> DateField(rule.value, set)

        info.type == "Numeric" || info.type == "Framerate" -> OutlinedTextField(
            value = rule.value,
            onValueChange = { v -> set(v.filter { it.isDigit() || it == '.' || it == '-' }) },
            label = { Text("Value") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )

        else -> OutlinedTextField(
            value = rule.value,
            onValueChange = set,
            label = { Text(if (rule.operator == "MatchRegex") "Regular expression" else "Value") },
            supportingText = when {
                kind == OperatorKind.Multi -> {
                    { Text("Separate several values with ;") }
                }

                info.type == "AspectRatio" -> {
                    { Text("For example 16:9") }
                }

                else -> null
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(value: String, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("Date") },
            placeholder = { Text("yyyy-mm-dd") },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.matchParentSize().clickable { open = true })
    }
    if (open) {
        val initial = runCatching { LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()
        val state = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }
}

/** Options that only some fields have: which user, unknown dates, units, parent items, collection depth. */
@Composable
private fun RuleExtras(rule: RuleDraft, info: FieldInfo, users: List<EmbyUser>, onChange: (RuleDraft) -> Unit) {
    val name = rule.field

    if (info.userSpecific && users.isNotEmpty()) {
        val chosen = rule.extraString("UserId")
        DropdownField(
            label = "Check this for",
            selectedText = users.firstOrNull { it.id.equals(chosen, ignoreCase = true) }?.name ?: "The list's user",
            options = listOf<Pair<String?, String>>(null to "The list's user") + users.map { it.id to it.name },
            onSelect = { id -> onChange(rule.withExtra("UserId", id?.let { JsonPrimitive(it) })) },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (name == "ReleaseDate" || name == "LastEpisodeAirDate") {
        SwitchRow("Include items with no date", "Off: items without this date never match", rule.extraBool("IncludeUnknownDates") == true) { on ->
            onChange(rule.withExtra("IncludeUnknownDates", if (on) JsonPrimitive(true) else null))
        }
    }

    if (name == "RuntimeMinutes") {
        DropdownField(
            label = "Runtime unit",
            selectedText = if (rule.extraString("RuntimeUnit").equals("seconds", ignoreCase = true)) "Seconds" else "Minutes",
            options = listOf("minutes" to "Minutes", "seconds" to "Seconds"),
            onSelect = { unit -> onChange(rule.withExtra("RuntimeUnit", if (unit == "seconds") JsonPrimitive("seconds") else null)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (name == "AudioLanguages") {
        SwitchRow("Only the default audio track", "Off: any audio track counts", rule.extraBool("OnlyDefaultAudioLanguage") == true) { on ->
            onChange(rule.withExtra("OnlyDefaultAudioLanguage", if (on) JsonPrimitive(true) else null))
        }
    }

    // Tags, studios, genres and favorite can look at the item, the item and its parent (series or album), or only the parent.
    val parentKey = when (name) {
        "Tags" -> "Tags"
        "Studios" -> "Studios"
        "Genres" -> "Genres"
        "IsFavorite" -> "Favorite"
        else -> null
    }
    if (parentKey != null) {
        val only = rule.extraBool("OnlyParent$parentKey") == true
        val include = rule.extraBool("IncludeParent$parentKey") == true
        DropdownField(
            label = "Look at",
            selectedText = when {
                only -> "Only the parent (series or album)"
                include -> "The item and its parent"
                else -> "The item itself"
            },
            options = listOf("item" to "The item itself", "both" to "The item and its parent", "only" to "Only the parent (series or album)"),
            onSelect = { mode ->
                onChange(
                    rule.withExtras(
                        mapOf(
                            "IncludeParent$parentKey" to if (mode == "item") null else JsonPrimitive(true),
                            "OnlyParent$parentKey" to if (mode == "only") JsonPrimitive(true) else null,
                        ),
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (name == "Collections") {
        SwitchRow("Also match episodes through their series", "Episodes count as members of the collections their series is in", rule.extraBool("IncludeEpisodesWithinSeries") == true) { on ->
            onChange(rule.withExtra("IncludeEpisodesWithinSeries", if (on) JsonPrimitive(true) else null))
        }
        OutlinedTextField(
            value = rule.extraInt("CollectionSearchDepth")?.toString().orEmpty(),
            onValueChange = { v ->
                val depth = v.filter(Char::isDigit).toIntOrNull()?.coerceIn(0, 10)
                onChange(rule.withExtra("CollectionSearchDepth", if (depth == null || depth == 0) null else JsonPrimitive(depth)))
            },
            label = { Text("Search depth (1 to 10)") },
            supportingText = { Text("Empty: only the collections the item is directly in") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** A labelled on/off row. */
@Composable
fun SwitchRow(title: String, description: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (description != null) Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}