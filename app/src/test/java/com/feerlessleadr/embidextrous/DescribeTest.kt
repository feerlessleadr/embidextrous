package com.feerlessleadr.embidextrous

import com.feerlessleadr.embidextrous.data.AppJson
import com.feerlessleadr.embidextrous.data.Catalog
import com.feerlessleadr.embidextrous.data.SmartListDoc
import com.feerlessleadr.embidextrous.ui.Describe
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DescribeTest {
    private val catalog: Catalog = AppJson.decodeFromString(
        Catalog.serializer(),
        """
        {"ApiVersion":1,
         "Fields":[
           {"Name":"NextUnwatched","Label":"Next Unwatched","Type":"Boolean","Operators":["Equal"],"UserSpecific":true},
           {"Name":"LibraryName","Label":"Library Name","Type":"Text","Operators":["IsIn"]},
           {"Name":"PlaybackStatus","Label":"Playback Status","Type":"UserData","Operators":["Equal"],
            "Values":[{"Value":"Played","Label":"Played (Fully Played)"},{"Value":"Unplayed","Label":"Unplayed (Not Started)"}]},
           {"Name":"DateCreated","Label":"Date Added to Library","Type":"Date","Operators":["NewerThan","Weekday"]}],
         "Operators":[{"Value":"Equal","Label":"equals"},{"Value":"IsIn","Label":"is in"},{"Value":"NewerThan","Label":"newer than"},{"Value":"Weekday","Label":"weekday"}],
         "Sorts":[{"Value":"Name","Label":"Name","Group":"General"},
                  {"Value":"Most Recently Watched Round Robin","Label":"Most Recently Watched Round Robin (Interleave)","Group":"Round Robin","Directionless":true,"RoundRobin":true}],
         "RoundRobinGroupByFields":[{"Value":"SeriesName","Label":"Series Name"}],
         "Weekdays":[{"Value":"1","Label":"Monday"}],
         "AutoRefreshModes":[{"Value":"OnAllChanges","Label":"On any change (including playback)"}]}
        """.trimIndent(),
    )

    private fun rule(json: String): JsonObject = AppJson.parseToJsonElement(json).jsonObject

    @Test fun `a boolean rule reads as yes or no, with the started-shows note`() {
        val text = Describe.rule(rule("""{"MemberName":"NextUnwatched","Operator":"Equal","TargetValue":"true","IncludeUnwatchedSeries":false}"""), catalog)
        assertEquals("Next Unwatched equals yes (only shows you have started)", text)
    }

    @Test fun `a multi value rule lists its values`() =
        assertEquals("Library Name is in TV, Anime", Describe.rule(rule("""{"MemberName":"LibraryName","Operator":"IsIn","TargetValue":"TV; Anime"}"""), catalog))

    @Test fun `fixed values show their label without the explanation`() =
        assertEquals("Playback Status equals Unplayed", Describe.rule(rule("""{"MemberName":"PlaybackStatus","Operator":"Equal","TargetValue":"Unplayed"}"""), catalog))

    @Test fun `relative dates and weekdays read naturally`() {
        assertEquals("Date Added to Library newer than 30 days", Describe.rule(rule("""{"MemberName":"DateCreated","Operator":"NewerThan","TargetValue":"30:days"}"""), catalog))
        assertEquals("Date Added to Library weekday Monday", Describe.rule(rule("""{"MemberName":"DateCreated","Operator":"Weekday","TargetValue":"1"}"""), catalog))
    }

    @Test fun `an unknown field falls back to its raw name`() =
        assertEquals("Mystery foo bar", Describe.rule(rule("""{"MemberName":"Mystery","Operator":"foo","TargetValue":"bar"}"""), catalog))

    @Test fun `sorts mention direction and grouping`() {
        assertEquals("Name, descending", Describe.sort(rule("""{"SortBy":"Name","SortOrder":"Descending"}"""), catalog))
        assertEquals(
            "Most Recently Watched Round Robin, grouped by Series Name",
            Describe.sort(rule("""{"SortBy":"Most Recently Watched Round Robin","SortOrder":"Ascending","GroupByField":"SeriesName"}"""), catalog),
        )
    }

    @Test fun `groups are listed one per line, rules joined with and`() {
        val doc = SmartListDoc.parse(
            """{"Id":"a","Name":"x","ExpressionSets":[
               {"Expressions":[{"MemberName":"LibraryName","Operator":"IsIn","TargetValue":"TV"},{"MemberName":"PlaybackStatus","Operator":"Equal","TargetValue":"Unplayed"}]},
               {"Expressions":[]},
               {"Expressions":[{"MemberName":"LibraryName","Operator":"IsIn","TargetValue":"Anime"}]}]}""",
        )
        assertEquals(
            listOf("Library Name is in TV  and  Playback Status equals Unplayed", "Library Name is in Anime"),
            Describe.ruleGroups(doc, catalog),
        )
    }

    @Test fun `runtime is shown in the largest sensible units`() {
        assertNull(Describe.runtime(0.0))
        assertEquals("45m", Describe.runtime(45.0))
        assertEquals("5h 30m", Describe.runtime(330.0))
        assertEquals("2d 11h", Describe.runtime(3_540.0))
    }

    @Test fun `a list document exposes its properties and keeps unknown ones when edited`() {
        val doc = SmartListDoc.parse(
            """{"Type":"Playlist","Id":"abc","Name":"Next Up","Enabled":false,"ItemCount":7,"MaxItems":0,"MediaTypes":["Episode"],"AutoRefresh":"OnAllChanges","FutureProperty":{"x":1}}""",
        )
        assertEquals("Next Up", doc.name)
        assertEquals(false, doc.enabled)
        assertEquals(7, doc.itemCount)
        assertNull(doc.maxItems)
        assertEquals(listOf("Episode"), doc.mediaTypes)

        val edited = doc.withValue("Name", kotlinx.serialization.json.JsonPrimitive("Renamed"))
        assertEquals("Renamed", edited.name)
        assertTrue(edited.json.containsKey("FutureProperty"))
    }
}
