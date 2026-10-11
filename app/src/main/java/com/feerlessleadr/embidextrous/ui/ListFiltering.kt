package com.feerlessleadr.embidextrous.ui

import com.feerlessleadr.embidextrous.data.RefreshStatus
import com.feerlessleadr.embidextrous.data.SmartListDoc

/** The chips above the list overview. */
enum class ListFilter(val label: String) {
    All("All"),
    Playlists("Playlists"),
    Collections("Collections"),
    Disabled("Disabled"),
    Failed("Failed"),
}

/** True when the list's last refresh failed (the plugin's history says so). */
fun SmartListDoc.refreshFailed(status: RefreshStatus): Boolean = status.last[id]?.success == false

/** Whether [doc] belongs under [filter]. */
fun matchesFilter(doc: SmartListDoc, filter: ListFilter, status: RefreshStatus): Boolean = when (filter) {
    ListFilter.All -> true
    ListFilter.Playlists -> !doc.isCollection
    ListFilter.Collections -> doc.isCollection
    ListFilter.Disabled -> !doc.enabled
    ListFilter.Failed -> doc.refreshFailed(status)
}

/** The lists to show: not pending deletion, under the chosen chip, and matching the typed words (name, any case). */
fun visibleLists(lists: List<SmartListDoc>, query: String, filter: ListFilter, status: RefreshStatus, hidden: Set<String>): List<SmartListDoc> {
    val words = query.trim().lowercase().split(' ').filter { it.isNotEmpty() }
    return lists.filter { doc ->
        doc.id !in hidden &&
            matchesFilter(doc, filter, status) &&
            words.all { doc.name.lowercase().contains(it) }
    }
}