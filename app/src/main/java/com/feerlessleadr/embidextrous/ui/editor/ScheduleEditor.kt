package com.feerlessleadr.embidextrous.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.feerlessleadr.embidextrous.data.ScheduleDraft

private val TRIGGERS = listOf(
    "Daily" to "Every day",
    "Weekly" to "Every week",
    "Monthly" to "Every month",
    "Yearly" to "Every year",
    "Interval" to "Every so often",
)

private val INTERVALS = listOf(
    "00:15:00" to "15 minutes",
    "00:30:00" to "30 minutes",
    "01:00:00" to "1 hour",
    "02:00:00" to "2 hours",
    "03:00:00" to "3 hours",
    "04:00:00" to "4 hours",
    "06:00:00" to "6 hours",
    "08:00:00" to "8 hours",
    "12:00:00" to "12 hours",
    "1.00:00:00" to "24 hours",
)

private val WEEKDAYS = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
private val MONTHS = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")

/** "1st", "2nd", "3rd", "11th" ... */
fun ordinal(n: Int): String {
    val suffix = if (n % 100 in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
    return "$n$suffix"
}

/** "15:30" as "3:30 PM". */
fun formatTime(hhmm: String): String {
    val h = hhmm.substringBefore(':').toIntOrNull() ?: return hhmm
    val m = hhmm.substringAfter(':', "00")
    val hour12 = if (h % 12 == 0) 12 else h % 12
    return "$hour12:$m ${if (h < 12) "AM" else "PM"}"
}

/** Every quarter hour of the day, grouped for the picker. */
private val TIME_ITEMS: List<PickerItem> = (0 until 24).flatMap { h ->
    (0 until 60 step 15).map { m ->
        val value = "%02d:%02d".format(h, m)
        PickerItem(value, formatTime(value), when (h) { in 5..11 -> "Morning"; in 12..16 -> "Afternoon"; in 17..21 -> "Evening"; else -> "Night" })
    }
}

/** A plain-words summary of a schedule, for the collapsed list. */
fun describeSchedule(s: ScheduleDraft): String = when (s.trigger) {
    "Daily" -> "every day at ${formatTime(s.time)}"
    "Weekly" -> "every ${WEEKDAYS.getOrElse(s.dayOfWeek) { "week" }} at ${formatTime(s.time)}"
    "Monthly" -> "on the ${ordinal(s.dayOfMonth)} of every month at ${formatTime(s.time)}"
    "Yearly" -> "every ${MONTHS.getOrElse(s.month - 1) { "year" }} ${ordinal(s.dayOfMonth)} at ${formatTime(s.time)}"
    "Interval" -> "every ${INTERVALS.firstOrNull { it.first == s.interval }?.second ?: s.interval}"
    else -> s.trigger
}

/** One schedule. A visibility schedule also chooses whether it enables or disables the list. */
@Composable
fun ScheduleCard(schedule: ScheduleDraft, visibility: Boolean, onChange: (ScheduleDraft) -> Unit, onDelete: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (visibility) {
                        DropdownField(
                            label = "Action",
                            selectedText = if (schedule.action == "Disable") "Disable the list" else "Enable the list",
                            options = listOf("Enable" to "Enable the list", "Disable" to "Disable the list"),
                            onSelect = { onChange(schedule.copy(action = it)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    DropdownField(
                        label = "When",
                        selectedText = TRIGGERS.firstOrNull { it.first == schedule.trigger }?.second ?: schedule.trigger,
                        options = TRIGGERS,
                        onSelect = { onChange(schedule.copy(trigger = it)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Remove schedule", tint = MaterialTheme.colorScheme.error) }
            }

            if (schedule.trigger == "Yearly") {
                DropdownField(
                    label = "Month",
                    selectedText = MONTHS.getOrElse(schedule.month - 1) { "January" },
                    options = MONTHS.mapIndexed { i, m -> (i + 1) to m },
                    onSelect = { onChange(schedule.copy(month = it)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (schedule.trigger == "Monthly" || schedule.trigger == "Yearly") {
                DropdownField(
                    label = "Day of the month",
                    selectedText = ordinal(schedule.dayOfMonth),
                    options = (1..31).map { it to ordinal(it) },
                    onSelect = { onChange(schedule.copy(dayOfMonth = it)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (schedule.trigger == "Weekly") {
                DropdownField(
                    label = "Day of the week",
                    selectedText = WEEKDAYS.getOrElse(schedule.dayOfWeek) { "Sunday" },
                    options = WEEKDAYS.mapIndexed { i, d -> i to d },
                    onSelect = { onChange(schedule.copy(dayOfWeek = it)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (schedule.trigger in setOf("Daily", "Weekly", "Monthly", "Yearly")) {
                PickerField(
                    label = "Time",
                    selectedText = formatTime(schedule.time),
                    title = "Choose a time",
                    items = TIME_ITEMS,
                    selected = schedule.time,
                    onPick = { onChange(schedule.copy(time = it)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (schedule.trigger == "Interval") {
                DropdownField(
                    label = "Every",
                    selectedText = INTERVALS.firstOrNull { it.first == schedule.interval }?.second ?: schedule.interval,
                    options = INTERVALS,
                    onSelect = { onChange(schedule.copy(interval = it)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
