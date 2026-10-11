package com.feerlessleadr.embidextrous.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Starting points for a new list, the same ideas as the plugin's templates. */
data class Template(val id: String, val title: String, val description: String)

object Templates {
    val all = listOf(
        Template("next-up", "Next Up", "The next unwatched episode of each show you are part-way through, most recently watched first."),
        Template("continue-watching", "Continue Watching", "The next unwatched episode of every show, including shows you have not started, the one you touched longest ago first."),
        Template("blank", "Blank list", "Start from nothing."),
    )

    /** The draft for a template, owned by [ownerId]. Library rules start empty: the person picks the libraries. */
    fun draft(id: String, ownerId: String?): ListDraft = when (id) {
        "next-up" -> ListDraft(
            name = "Next Up",
            ownerId = ownerId,
            users = listOfNotNull(ownerId),
            mediaTypes = setOf("Episode"),
            groups = listOf(
                GroupDraft(
                    rules = listOf(
                        RuleDraft(
                            field = "NextUnwatched",
                            operator = "Equal",
                            value = "true",
                            extras = JsonObject(mapOf("IncludeUnwatchedSeries" to JsonPrimitive(false))),
                        ),
                        RuleDraft(field = "LibraryName", operator = "IsIn", value = ""),
                    ),
                ),
            ),
            sorts = listOf(SortDraft(sortBy = "Most Recently Watched Round Robin", groupBy = "SeriesName")),
            autoRefresh = "OnAllChanges",
        )

        "continue-watching" -> ListDraft(
            name = "Continue Watching",
            ownerId = ownerId,
            users = listOfNotNull(ownerId),
            mediaTypes = setOf("Episode"),
            groups = listOf(
                GroupDraft(
                    rules = listOf(
                        RuleDraft(field = "NextUnwatched", operator = "Equal", value = "true"),
                        RuleDraft(field = "LibraryName", operator = "IsIn", value = ""),
                    ),
                ),
            ),
            sorts = listOf(SortDraft(sortBy = "Least Recently Watched Round Robin", groupBy = "SeriesName")),
            autoRefresh = "OnAllChanges",
        )

        else -> ListDraft(
            ownerId = ownerId,
            users = listOfNotNull(ownerId),
            mediaTypes = setOf("Movie"),
            sorts = listOf(SortDraft(sortBy = "Name")),
        )
    }
}
