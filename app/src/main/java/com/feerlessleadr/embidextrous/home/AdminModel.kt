package com.feerlessleadr.embidextrous.home

import com.feerlessleadr.embidextrous.data.RefreshStatus
import com.feerlessleadr.embidextrous.data.SmartListDoc
import java.time.Instant
import java.time.OffsetDateTime

/** How one smart list is doing. */
enum class ListState { Refreshing, Failed, Ok, Off }

/** One line of the smart-lists card. */
data class ListRow(val name: String, val itemCount: Int?, val lastRefreshed: Instant?, val state: ListState)

/** The smart-lists card: how the lists are doing. */
data class ListsSnapshot(
    val total: Int,
    val enabled: Int,
    /** Lists being refreshed on the server right now. */
    val refreshing: Int,
    /** Names of enabled lists whose last refresh failed. */
    val failed: List<String>,
    /** The most recent refresh of any list. */
    val lastRefreshed: Instant?,
    /** Items across the enabled lists (those that report a count). */
    val totalItems: Int,
    /** The lists worth showing first: trouble, then work in progress, then the rest by name; at most [PREVIEW_ROWS]. */
    val rows: List<ListRow>,
)

/** Works out what the administrator cards show. No Android classes: unit tested. */
object AdminModel {
    const val PREVIEW_ROWS = 3

    fun lists(lists: List<SmartListDoc>, status: RefreshStatus): ListsSnapshot {
        val enabled = lists.filter { it.enabled }
        val rows = lists.map { doc ->
            val state = when {
                !doc.enabled -> ListState.Off
                doc.id in status.running -> ListState.Refreshing
                status.last[doc.id]?.success == false -> ListState.Failed
                else -> ListState.Ok
            }
            ListRow(doc.name, doc.itemCount, parse(doc.lastRefreshed), state)
        }
        val order = compareBy<ListRow>({ it.state != ListState.Failed }, { it.state != ListState.Refreshing }, { it.state == ListState.Off }, { it.name.lowercase() })
        return ListsSnapshot(
            total = lists.size,
            enabled = enabled.size,
            refreshing = lists.count { it.id in status.running },
            failed = enabled.filter { status.last[it.id]?.success == false && it.id !in status.running }.map { it.name },
            lastRefreshed = lists.mapNotNull { parse(it.lastRefreshed) }.maxOrNull(),
            totalItems = enabled.sumOf { it.itemCount ?: 0 },
            rows = rows.sortedWith(order).take(PREVIEW_ROWS),
        )
    }

    private fun parse(text: String?): Instant? {
        if (text.isNullOrBlank()) return null
        return runCatching { Instant.parse(text) }.getOrNull() ?: runCatching { OffsetDateTime.parse(text).toInstant() }.getOrNull()
    }
}
