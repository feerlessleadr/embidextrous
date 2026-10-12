package com.feerlessleadr.embidextrous.home

import com.feerlessleadr.embidextrous.data.RefreshStatus
import com.feerlessleadr.embidextrous.data.SmartListDoc
import java.time.Instant
import java.time.OffsetDateTime

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
)

/** Works out what the administrator cards show. No Android classes: unit tested. */
object AdminModel {
    fun lists(lists: List<SmartListDoc>, status: RefreshStatus): ListsSnapshot {
        val enabled = lists.filter { it.enabled }
        return ListsSnapshot(
            total = lists.size,
            enabled = enabled.size,
            refreshing = lists.count { it.id in status.running },
            failed = enabled.filter { status.last[it.id]?.success == false && it.id !in status.running }.map { it.name },
            lastRefreshed = lists.mapNotNull { parse(it.lastRefreshed) }.maxOrNull(),
        )
    }

    private fun parse(text: String?): Instant? {
        if (text.isNullOrBlank()) return null
        return runCatching { Instant.parse(text) }.getOrNull() ?: runCatching { OffsetDateTime.parse(text).toInstant() }.getOrNull()
    }
}
