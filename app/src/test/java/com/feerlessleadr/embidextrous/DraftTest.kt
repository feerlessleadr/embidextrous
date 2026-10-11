package com.feerlessleadr.embidextrous

import com.feerlessleadr.embidextrous.data.AppJson
import com.feerlessleadr.embidextrous.data.AppSettings
import com.feerlessleadr.embidextrous.data.Catalog
import com.feerlessleadr.embidextrous.data.ListDraft
import com.feerlessleadr.embidextrous.data.SmartListDoc
import com.feerlessleadr.embidextrous.data.Templates
import com.feerlessleadr.embidextrous.data.ThemeMode
import com.feerlessleadr.embidextrous.data.fieldsFor
import com.feerlessleadr.embidextrous.data.withGroupDuplicated
import com.feerlessleadr.embidextrous.data.withRuleDuplicated
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DraftTest {
    private val catalog: Catalog = AppJson.decodeFromString(
        Catalog.serializer(),
        """
        {"ApiVersion":1,
         "Fields":[
           {"Name":"NextUnwatched","Label":"Next Unwatched","Category":"RatingsPlayback","Type":"Boolean","Operators":["Equal"]},
           {"Name":"LibraryName","Label":"Library Name","Category":"Library","Type":"Text","Operators":["Equal","IsIn"]},
           {"Name":"Name","Label":"Name","Category":"Content","Type":"Text","Operators":["Equal"]},
           {"Name":"Resolution","Label":"Resolution","Category":"Video","Type":"Resolution","Operators":["Equal"]},
           {"Name":"AudioCodec","Label":"Audio Codec","Category":"Audio","Type":"Text","Operators":["Equal"]}],
         "Operators":[{"Value":"Equal","Label":"equals"},{"Value":"IsIn","Label":"is in"}],
         "Sorts":[{"Value":"Name","Label":"Name"},
                  {"Value":"Round Robin","Label":"Round Robin (Interleave)","Group":"Round Robin","RoundRobin":true}]}
        """.trimIndent(),
    )

    private val existing = SmartListDoc.parse(
        """{"Type":"Playlist","Id":"abc","Name":"Next Up","Enabled":true,"UserId":"u1","MediaTypes":["Episode"],
            "ExpressionSets":[{"Expressions":[{"MemberName":"NextUnwatched","Operator":"Equal","TargetValue":"true","IncludeUnwatchedSeries":false,"UserId":"u2","FutureRuleProp":7}],"MaxItems":5,"FutureSetProp":"x"}],
            "Order":{"Name":"keep me","SortOptions":[{"SortBy":"Name","SortOrder":"Descending","AirBlockWindowDays":4}]},
            "AutoRefresh":"OnAllChanges","MaxItems":50,"PlaylistId":"123","FutureListProp":{"a":1}}""",
    )

    @Test fun `a list round-trips and keeps properties the app does not show`() {
        val json = ListDraft.from(existing).toJson()

        assertEquals("Next Up", json["Name"]!!.jsonPrimitive.content)
        assertEquals("123", json["PlaylistId"]!!.jsonPrimitive.content)
        assertTrue(json.containsKey("FutureListProp"))
        val set = json["ExpressionSets"]!!.jsonArray[0].jsonObject
        assertEquals("x", set["FutureSetProp"]!!.jsonPrimitive.content)
        assertEquals("5", set["MaxItems"]!!.jsonPrimitive.content)
        val rule = set["Expressions"]!!.jsonArray[0].jsonObject
        assertEquals("u2", rule["UserId"]!!.jsonPrimitive.content)
        assertEquals("7", rule["FutureRuleProp"]!!.jsonPrimitive.content)
        assertEquals("false", rule["IncludeUnwatchedSeries"]!!.jsonPrimitive.content)
        val order = json["Order"]!!.jsonObject
        assertEquals("keep me", order["Name"]!!.jsonPrimitive.content)
        val sort = order["SortOptions"]!!.jsonArray[0].jsonObject
        assertEquals("Descending", sort["SortOrder"]!!.jsonPrimitive.content)
        assertEquals("4", sort["AirBlockWindowDays"]!!.jsonPrimitive.content)
    }

    @Test fun `edits are applied and the owner of an existing list is not sent again`() {
        val draft = ListDraft.from(existing).copy(name = "Renamed", maxItems = "", autoRefresh = "Never")
        val json = draft.toJson()

        assertEquals("Renamed", json["Name"]!!.jsonPrimitive.content)
        assertEquals(JsonNull, json["MaxItems"])
        assertEquals("Never", json["AutoRefresh"]!!.jsonPrimitive.content)
        assertEquals(listOf("u1"), json["UserPlaylists"]!!.jsonArray.map { it.jsonObject["UserId"]!!.jsonPrimitive.content })
        assertFalse(draft.isNew)
    }

    @Test fun `a new list carries its owner and has no id`() {
        val draft = Templates.draft("blank", "u9").copy(name = "Movies")
        val json = draft.toJson()

        assertTrue(draft.isNew)
        assertEquals(listOf("u9"), json["UserPlaylists"]!!.jsonArray.map { it.jsonObject["UserId"]!!.jsonPrimitive.content })
        assertFalse(json.containsKey("UserId"))
        assertFalse(json.containsKey("Id"))
        assertEquals(listOf("Movie"), json["MediaTypes"]!!.jsonArray.map { it.jsonPrimitive.content })
    }

    @Test fun `the Next Up template matches the plugin's template`() {
        val draft = Templates.draft("next-up", "u1")
        val rules = draft.groups.single().rules

        assertEquals(setOf("Episode"), draft.mediaTypes)
        assertEquals("NextUnwatched", rules[0].field)
        assertEquals(false, rules[0].extraBool("IncludeUnwatchedSeries"))
        assertEquals("LibraryName", rules[1].field)
        assertEquals("", rules[1].value)
        assertEquals("Most Recently Watched Round Robin", draft.sorts.single().sortBy)
        assertEquals("SeriesName", draft.sorts.single().groupBy)
        assertEquals("OnAllChanges", draft.autoRefresh)
    }

    @Test fun `validation asks for the name, media types, owner and empty rule values`() {
        val ok = Templates.draft("blank", "u1").copy(name = "Movies")
        assertNull(ok.validate(catalog))

        assertTrue(ok.copy(name = " ").validate(catalog)!!.contains("name"))
        assertTrue(ok.copy(mediaTypes = emptySet()).validate(catalog)!!.contains("media type"))
        assertTrue(ok.copy(users = emptyList()).validate(catalog)!!.contains("user"))

        val template = Templates.draft("next-up", "u1")
        assertTrue(template.validate(catalog)!!.contains("Library Name"))
        val filled = template.copy(groups = template.groups.map { g -> g.copy(rules = g.rules.map { if (it.field == "LibraryName") it.copy(value = "TV") else it }) })
        assertNull(filled.validate(catalog))
    }

    @Test fun `a playlist cannot hold collection-only media types, a collection can`() {
        val playlist = Templates.draft("blank", "u1").copy(name = "x", mediaTypes = setOf("Series"))
        assertTrue(playlist.validate(catalog)!!.contains("collection"))
        assertNull(playlist.copy(type = "Collection").validate(catalog))
    }

    @Test fun `a round robin sort needs a group by`() {
        val draft = Templates.draft("blank", "u1").copy(name = "x", sorts = listOf(com.feerlessleadr.embidextrous.data.SortDraft(sortBy = "Round Robin")))
        assertTrue(draft.validate(catalog)!!.contains("group"))
    }

    @Test fun `limits must be whole numbers`() {
        val draft = Templates.draft("blank", "u1").copy(name = "x", maxItems = "12x")
        assertTrue(draft.validate(catalog)!!.contains("Maximum items"))
    }

    @Test fun `empty rule groups are not sent`() {
        val draft = Templates.draft("blank", "u1").copy(name = "x", groups = listOf(com.feerlessleadr.embidextrous.data.GroupDraft(rules = emptyList())))
        assertEquals(JsonArray(emptyList()), draft.toJson()["ExpressionSets"])
    }

    @Test fun `video and audio fields are offered only for media that has them`() {
        fun names(types: Set<String>) = fieldsFor(catalog, types).map { it.name }.toSet()

        assertTrue("Resolution" in names(setOf("Movie")))
        assertFalse("Resolution" in names(setOf("Audio")))
        assertTrue("AudioCodec" in names(setOf("Audio")))
        assertFalse("AudioCodec" in names(setOf("Photo")))
        assertEquals(setOf("Name"), names(setOf("Collection")))
    }

    @Test fun `theme mode is read from its saved name and falls back to follow system`() {
        assertEquals(ThemeMode.Dark, ThemeMode.fromName("Dark"))
        assertEquals(ThemeMode.System, ThemeMode.fromName("nonsense"))
        assertEquals(ThemeMode.System, ThemeMode.fromName(null))
        assertEquals(listOf("Follow system", "Light", "Dark"), ThemeMode.entries.map { it.label })
        assertTrue(AppSettings::class.java.simpleName.isNotEmpty())
    }

    @Test fun `a shared playlist is recognised`() {
        val doc = SmartListDoc(
            JsonObject(
                mapOf(
                    "Type" to JsonPrimitive("Playlist"),
                    "Name" to JsonPrimitive("x"),
                    "AllUsers" to JsonPrimitive(true),
                ),
            ),
        )
        assertTrue(ListDraft.from(doc).allUsers)
    }

    // ----- the rest of the web page's options -----

    private val full = SmartListDoc.parse(
        """{"Type":"Playlist","Id":"p1","Name":"Full","Enabled":true,"MediaTypes":["Episode"],
            "UserPlaylists":[{"UserId":"u1","PlaylistId":"101"},{"UserId":"u2","PlaylistId":"102"}],"Public":false,"AllUsers":false,
            "ExpressionSets":[],"Order":{"SortOptions":[{"SortBy":"Name","SortOrder":"Ascending"}]},
            "Schedules":[{"Trigger":"Weekly","DayOfWeek":5,"Time":"16:45:00"},{"Trigger":"Interval","Interval":"02:00:00"}],
            "VisibilitySchedules":[{"Trigger":"Daily","Time":"06:00:00","Action":"Disable","FutureScheduleProp":1}],
            "SortTitle":"zz","Overview":"About","Tags":["a","b"],"Favorite":true,
            "IncludeExtras":true,"MatchByMembers":false,"GroupIntoCollections":false,
            "RandomGroupSelection":{"Enabled":true,"GroupBy":"Genres","MinimumItems":3},
            "SimilarityComparisonFields":["Genre"],
            "Bumpers":{"ExpressionSets":[{"Expressions":[{"MemberName":"Tags","Operator":"Contains","TargetValue":"bump"}]}],"MediaTypes":["Video"],"BumperOrder":"Name","Interval":4}}""",
    )

    @Test fun `schedules are read and written with only the fields their trigger needs`() {
        val draft = ListDraft.from(full)
        assertEquals(listOf("Weekly", "Interval"), draft.schedules.map { it.trigger })
        assertEquals("16:45", draft.schedules[0].time)
        assertEquals(5, draft.schedules[0].dayOfWeek)
        assertEquals("Disable", draft.visibilitySchedules.single().action)

        val json = draft.toJson()
        val weekly = json["Schedules"]!!.jsonArray[0].jsonObject
        assertEquals("16:45:00", weekly["Time"]!!.jsonPrimitive.content)
        assertEquals("5", weekly["DayOfWeek"]!!.jsonPrimitive.content)
        assertFalse(weekly.containsKey("Interval"))
        val interval = json["Schedules"]!!.jsonArray[1].jsonObject
        assertEquals("02:00:00", interval["Interval"]!!.jsonPrimitive.content)
        assertFalse(interval.containsKey("Time"))
        val visibility = json["VisibilitySchedules"]!!.jsonArray[0].jsonObject
        assertEquals("Disable", visibility["Action"]!!.jsonPrimitive.content)
        assertEquals("1", visibility["FutureScheduleProp"]!!.jsonPrimitive.content)
    }

    @Test fun `a schedule of each kind writes what the plugin expects`() {
        fun j(s: com.feerlessleadr.embidextrous.data.ScheduleDraft) = s.toJson().mapValues { it.value.jsonPrimitive.content }
        val base = com.feerlessleadr.embidextrous.data.ScheduleDraft(time = "03:15", dayOfWeek = 2, dayOfMonth = 9, month = 11, interval = "1.00:00:00")
        assertEquals(mapOf("Trigger" to "Daily", "Time" to "03:15:00"), j(base.copy(trigger = "Daily")))
        assertEquals(mapOf("Trigger" to "Monthly", "DayOfMonth" to "9", "Time" to "03:15:00"), j(base.copy(trigger = "Monthly")))
        assertEquals(mapOf("Trigger" to "Yearly", "Month" to "11", "DayOfMonth" to "9", "Time" to "03:15:00"), j(base.copy(trigger = "Yearly")))
        assertEquals(mapOf("Trigger" to "Interval", "Interval" to "1.00:00:00"), j(base.copy(trigger = "Interval")))
    }

    @Test fun `playlist users keep their emby playlist ids when others change`() {
        val draft = ListDraft.from(full)
        assertEquals(listOf("u1", "u2"), draft.users)

        val json = draft.copy(users = listOf("u2", "u3")).toJson()
        val mappings = json["UserPlaylists"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("u2", "u3"), mappings.map { it["UserId"]!!.jsonPrimitive.content })
        assertEquals("102", mappings[0]["PlaylistId"]!!.jsonPrimitive.content) // stays
        assertFalse(mappings[1].containsKey("PlaylistId")) // new
        assertEquals("false", json["Public"]!!.jsonPrimitive.content) // two users: never public
    }

    @Test fun `all users writes an empty mapping and public only applies to one user`() {
        val everyone = ListDraft.from(full).copy(allUsers = true, isPublic = true).toJson()
        assertEquals("true", everyone["AllUsers"]!!.jsonPrimitive.content)
        assertEquals(0, everyone["UserPlaylists"]!!.jsonArray.size)
        assertEquals("false", everyone["Public"]!!.jsonPrimitive.content)

        val single = ListDraft.from(full).copy(users = listOf("u1"), isPublic = true).toJson()
        assertEquals("true", single["Public"]!!.jsonPrimitive.content)
    }

    @Test fun `a collection keeps its owner as the user id and has no bumpers or user playlists`() {
        val json = ListDraft(type = "Collection", name = "c", ownerId = "u5", mediaTypes = setOf("Movie"), groupIntoCollections = true, bumpers = com.feerlessleadr.embidextrous.data.BumperDraft()).toJson()
        assertEquals("u5", json["UserId"]!!.jsonPrimitive.content)
        assertFalse(json.containsKey("Bumpers"))
        assertFalse(json.containsKey("UserPlaylists"))
        assertEquals("true", json["GroupIntoCollections"]!!.jsonPrimitive.content)
    }

    @Test fun `presentation settings round trip and unmanaged ones are left out`() {
        val draft = ListDraft.from(full)
        assertEquals("zz", draft.sortTitle)
        assertEquals(listOf("a", "b"), draft.tags)
        assertEquals(true, draft.favorite)

        val cleared = draft.copy(sortTitle = " ", overview = "", tags = null, favorite = null).toJson()
        assertFalse(cleared.containsKey("SortTitle"))
        assertFalse(cleared.containsKey("Overview"))
        assertFalse(cleared.containsKey("Tags"))
        assertFalse(cleared.containsKey("Favorite"))

        val emptyTags = draft.copy(tags = emptyList()).toJson()
        assertEquals(0, emptyTags["Tags"]!!.jsonArray.size) // an empty list means "clear the managed tags"
    }

    @Test fun `advanced options and the random group round trip`() {
        val draft = ListDraft.from(full)
        assertTrue(draft.includeExtras)
        assertTrue(draft.randomGroup.enabled)
        assertEquals("Genres", draft.randomGroup.groupBy)
        assertEquals("3", draft.randomGroup.minimumItems)

        val json = draft.toJson()
        val random = json["RandomGroupSelection"]!!.jsonObject
        assertEquals("Genres", random["GroupBy"]!!.jsonPrimitive.content)
        assertEquals("3", random["MinimumItems"]!!.jsonPrimitive.content)
        assertFalse(draft.copy(randomGroup = draft.randomGroup.copy(enabled = false)).toJson().containsKey("RandomGroupSelection"))
    }

    @Test fun `similarity fields are only sent while a similar to rule exists`() {
        val rule = com.feerlessleadr.embidextrous.data.RuleDraft(field = "SimilarTo", operator = "Equal", value = "Movie")
        val withRule = ListDraft.from(full).copy(groups = listOf(com.feerlessleadr.embidextrous.data.GroupDraft(rules = listOf(rule))))
        assertEquals(1, withRule.toJson()["SimilarityComparisonFields"]!!.jsonArray.size)
        assertEquals(0, ListDraft.from(full).toJson()["SimilarityComparisonFields"]!!.jsonArray.size)
    }

    @Test fun `bumpers round trip and are validated`() {
        val draft = ListDraft.from(full)
        val bumpers = draft.bumpers!!
        assertEquals(setOf("Video"), bumpers.mediaTypes)
        assertEquals("Name", bumpers.order)
        assertEquals("4", bumpers.interval)
        assertEquals("Tags", bumpers.groups.single().rules.single().field)

        val sent = draft.toJson()["Bumpers"]!!.jsonObject
        assertEquals("4", sent["Interval"]!!.jsonPrimitive.content)
        assertEquals("Name", sent["BumperOrder"]!!.jsonPrimitive.content)

        assertTrue(draft.copy(bumpers = bumpers.copy(mediaTypes = emptySet())).validate(catalog)!!.contains("Bumpers"))
        assertTrue(draft.copy(bumpers = bumpers.copy(interval = "0")).validate(catalog)!!.contains("Bumpers"))
        assertNull(draft.copy(bumpers = null).toJson()["Bumpers"])
    }

    @Test fun `rule extras can be set and cleared`() {
        val rule = com.feerlessleadr.embidextrous.data.RuleDraft(field = "Tags", operator = "Contains", value = "x")
            .withExtras(mapOf("IncludeParentTags" to JsonPrimitive(true), "OnlyParentTags" to JsonPrimitive(true)))
        assertEquals(true, rule.extraBool("OnlyParentTags"))
        val cleared = rule.withExtras(mapOf("IncludeParentTags" to null, "OnlyParentTags" to null))
        assertNull(cleared.extraBool("OnlyParentTags"))
        assertTrue(cleared.extras.isEmpty())
    }

    @Test fun `a random group needs something to group by`() {
        val draft = Templates.draft("blank", "u1").copy(name = "x", randomGroup = com.feerlessleadr.embidextrous.data.RandomGroupDraft(enabled = true, groupBy = null))
        assertTrue(draft.validate(catalog)!!.contains("group"))
    }

    // ----- duplicating -----

    private fun rule(field: String, value: String) = com.feerlessleadr.embidextrous.data.RuleDraft(field = field, operator = "Equal", value = value)

    @Test fun `a duplicated rule sits right after the original with its own key and the same settings`() {
        val a = rule("Name", "A").withExtra("UserId", JsonPrimitive("u1"))
        val b = rule("Name", "B")
        val group = com.feerlessleadr.embidextrous.data.GroupDraft(rules = listOf(a, b))

        val result = listOf(group).withRuleDuplicated(group.key, a.key).single().rules
        assertEquals(listOf("A", "A", "B"), result.map { it.value })
        assertEquals(3, result.map { it.key }.toSet().size)
        assertEquals("u1", result[1].extraString("UserId"))
        assertEquals(a.key, result[0].key)
    }

    @Test fun `duplicating a rule in one group leaves other groups alone`() {
        val g1 = com.feerlessleadr.embidextrous.data.GroupDraft(rules = listOf(rule("Name", "A")))
        val g2 = com.feerlessleadr.embidextrous.data.GroupDraft(rules = listOf(rule("Name", "B")))
        val result = listOf(g1, g2).withRuleDuplicated(g2.key, g2.rules[0].key)
        assertEquals(1, result[0].rules.size)
        assertEquals(2, result[1].rules.size)
    }

    @Test fun `a duplicated group is an extra alternative with the same rules and new keys`() {
        val g1 = com.feerlessleadr.embidextrous.data.GroupDraft(rules = listOf(rule("Name", "A"), rule("Tags", "x")))
        val result = listOf(g1).withGroupDuplicated(g1.key)
        assertEquals(2, result.size)
        assertEquals(result[0].rules.map { it.value }, result[1].rules.map { it.value })
        assertEquals(4, result.flatMap { g -> g.rules.map { it.key } }.toSet().size)
        assertTrue(result[0].key != result[1].key)
    }

    @Test fun `a list copy is a new list with the same settings and none of the old one's identity`() {
        val copy = ListDraft.from(full).asCopy()

        assertTrue(copy.isNew)
        assertEquals("Full (copy)", copy.name)
        assertEquals(listOf("Weekly", "Interval"), copy.schedules.map { it.trigger })
        assertEquals(listOf("u1", "u2"), copy.users)
        assertEquals("Genres", copy.randomGroup.groupBy)

        val json = copy.toJson()
        assertFalse(json.containsKey("Id"))
        assertFalse(json.containsKey("PlaylistId"))
        assertTrue(json["UserPlaylists"]!!.jsonArray.none { it.jsonObject.containsKey("PlaylistId") })
        assertEquals("Full (copy)", copy.asCopy().name) // copying a copy does not pile up suffixes
    }
}