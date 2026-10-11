package com.feerlessleadr.embidextrous.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.feerlessleadr.embidextrous.data.Catalog
import com.feerlessleadr.embidextrous.data.EmbyUser
import com.feerlessleadr.embidextrous.data.FieldInfo
import com.feerlessleadr.embidextrous.data.GroupDraft
import com.feerlessleadr.embidextrous.data.ValueOption
import com.feerlessleadr.embidextrous.data.duplicated
import com.feerlessleadr.embidextrous.data.withGroupDuplicated
import com.feerlessleadr.embidextrous.data.withRuleDuplicated

/**
 * The rule groups of a list (or of its bumpers): groups are alternatives, the rules inside a group all apply.
 * [onGroups] receives a function that turns the current groups into the new ones.
 */
@Composable
fun RuleGroupsEditor(
    groups: List<GroupDraft>,
    fields: List<FieldInfo>,
    catalog: Catalog,
    libraries: List<ValueOption>,
    users: List<EmbyUser>,
    emptyHint: String,
    onGroups: ((List<GroupDraft>) -> List<GroupDraft>) -> Unit,
) {
    var addRuleTo by remember { mutableStateOf<Long?>(null) }
    // A rule is open when it was opened by hand, or while it still needs a value (and was not folded by hand).
    var opened by rememberSaveable { mutableStateOf<List<Long>>(emptyList()) }
    var folded by rememberSaveable { mutableStateOf<List<Long>>(emptyList()) }
    fun isOpen(rule: com.feerlessleadr.embidextrous.data.RuleDraft) = rule.key in opened || (rule.key !in folded && rule.value.isBlank() && catalog.field(rule.field)?.type != "Boolean")

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            if (groups.isEmpty()) emptyHint else "An item is included when it matches every rule in at least one group.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        groups.forEachIndexed { index, group ->
            if (index > 0) Text("or", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    group.rules.forEach { rule ->
                        key(rule.key) {
                            RuleCard(
                                rule = rule,
                                catalog = catalog,
                                fields = fields,
                                libraries = libraries,
                                users = users,
                                expanded = isOpen(rule),
                                onToggle = {
                                    if (isOpen(rule)) {
                                        opened = opened - rule.key
                                        folded = folded + rule.key
                                    } else {
                                        folded = folded - rule.key
                                        opened = opened + rule.key
                                    }
                                },
                                onChange = { changed ->
                                    onGroups { gs -> gs.map { g -> if (g.key == group.key) g.copy(rules = g.rules.map { if (it.key == rule.key) changed else it }) else g } }
                                },
                                onDuplicate = {
                                    val copy = rule.duplicated()
                                    opened = opened + copy.key
                                    onGroups { gs -> gs.withRuleDuplicated(group.key, rule.key, copy) }
                                },
                                onDelete = {
                                    onGroups { gs -> gs.map { g -> if (g.key == group.key) g.copy(rules = g.rules.filterNot { it.key == rule.key }) else g } }
                                },
                            )
                        }
                    }
                    Row {
                        TextButton(onClick = { addRuleTo = group.key }) {
                            Icon(Icons.Default.Add, contentDescription = null, Modifier.size(18.dp))
                            Text("  Add rule")
                        }
                        if (group.rules.isNotEmpty()) {
                            TextButton(onClick = { onGroups { gs -> gs.withGroupDuplicated(group.key) } }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, Modifier.size(16.dp))
                                Text("  Duplicate")
                            }
                        }
                        TextButton(onClick = { onGroups { gs -> gs.filterNot { it.key == group.key } } }) {
                            Text("Remove group", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
        TextButton(onClick = {
            val g = GroupDraft(rules = emptyList())
            onGroups { gs -> gs + g }
            addRuleTo = g.key
        }) {
            Icon(Icons.Default.Add, contentDescription = null, Modifier.size(18.dp))
            Text(if (groups.isEmpty()) "  Add a rule" else "  Add another group (or)")
        }
    }

    addRuleTo?.let { groupKey ->
        GroupedPickerDialog(
            title = "Choose a field",
            items = fieldPickerItems(fields),
            selected = null,
            onPick = { name ->
                addRuleTo = null
                catalog.field(name)?.let { f ->
                    val rule = newRule(f)
                    opened = opened + rule.key
                    onGroups { gs -> gs.map { g -> if (g.key == groupKey) g.copy(rules = g.rules + rule) else g } }
                }
            },
            onDismiss = {
                // A group created just now stays empty if nothing was picked: drop it.
                onGroups { gs -> gs.filterNot { it.key == groupKey && it.rules.isEmpty() } }
                addRuleTo = null
            },
        )
    }
}
