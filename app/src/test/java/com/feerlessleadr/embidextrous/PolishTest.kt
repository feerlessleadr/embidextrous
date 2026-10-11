package com.feerlessleadr.embidextrous

import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.feerlessleadr.embidextrous.data.ApiClient
import com.feerlessleadr.embidextrous.data.AppSettings
import com.feerlessleadr.embidextrous.data.Density
import com.feerlessleadr.embidextrous.data.ListDraft
import com.feerlessleadr.embidextrous.data.RefreshOutcome
import com.feerlessleadr.embidextrous.data.RefreshStatus
import com.feerlessleadr.embidextrous.data.SmartListDoc
import com.feerlessleadr.embidextrous.data.Swatch
import com.feerlessleadr.embidextrous.ui.ListFilter
import com.feerlessleadr.embidextrous.ui.colorSchemeFor
import com.feerlessleadr.embidextrous.ui.visibleLists
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The quality-of-life batch (v0.4.0): filtering, sharing, status, preview, backups, colours. */
class PolishTest {
    private fun doc(id: String, name: String, type: String = "Playlist", enabled: Boolean = true, extra: String = "") =
        SmartListDoc.parse("""{"Id":"$id","Name":"$name","Type":"$type","Enabled":$enabled$extra}""")

    private val lists = listOf(
        doc("1", "Next Up"),
        doc("2", "Recent Movies"),
        doc("3", "Kids Collection", type = "Collection"),
        doc("4", "Old Stuff", enabled = false),
    )
    private val status = RefreshStatus(emptyMap(), mapOf("2" to RefreshOutcome(false, "boom", null), "1" to RefreshOutcome(true, null, null)))

    // ----- filtering -----

    @Test fun `the filter chips pick lists by type, enabled state and last refresh`() {
        fun names(f: ListFilter) = visibleLists(lists, "", f, status, emptySet()).map { it.name }
        assertEquals(4, names(ListFilter.All).size)
        assertEquals(listOf("Next Up", "Recent Movies", "Old Stuff"), names(ListFilter.Playlists))
        assertEquals(listOf("Kids Collection"), names(ListFilter.Collections))
        assertEquals(listOf("Old Stuff"), names(ListFilter.Disabled))
        assertEquals(listOf("Recent Movies"), names(ListFilter.Failed))
    }

    @Test fun `search matches every typed word in any order and any case`() {
        assertEquals(listOf("Recent Movies"), visibleLists(lists, "movies RECENT", ListFilter.All, status, emptySet()).map { it.name })
        assertEquals(emptyList<String>(), visibleLists(lists, "zebra", ListFilter.All, status, emptySet()).map { it.name })
        assertEquals(4, visibleLists(lists, "   ", ListFilter.All, status, emptySet()).size)
    }

    @Test fun `lists waiting to be deleted are hidden`() {
        assertEquals(listOf("Next Up", "Kids Collection", "Old Stuff"), visibleLists(lists, "", ListFilter.All, status, setOf("2")).map { it.name })
    }

    // ----- cover item -----

    @Test fun `the cover comes from the first playlist of a playlist or the collection id`() {
        val playlist = doc("p", "P", extra = ""","UserPlaylists":[{"UserId":"u","PlaylistId":""},{"UserId":"v","PlaylistId":"331"}]""")
        assertEquals("331", playlist.embyItemId)
        assertEquals("336", doc("c", "C", type = "Collection", extra = ""","CollectionId":"336"""").embyItemId)
        assertNull(doc("n", "N").embyItemId)
    }

    // ----- sharing -----

    private val shared = SmartListDoc.parse(
        """{"Type":"Playlist","Id":"p1","Name":"Weekend","Enabled":true,"MediaTypes":["Movie"],
            "UserPlaylists":[{"UserId":"u1","PlaylistId":"101"}],"AllUsers":false,"Public":true,"PlaylistId":"101","LastRefreshed":"2026-01-01T00:00:00Z",
            "ExpressionSets":[{"Expressions":[{"MemberName":"Genres","Operator":"Contains","TargetValue":"Comedy"}]}],
            "Order":{"SortOptions":[{"SortBy":"Name","SortOrder":"Ascending"}]}}""",
    )

    @Test fun `a shared list carries the rules and settings but no ids and no users`() {
        val json = ListDraft.from(shared).forSharing().toJson()
        assertEquals("Weekend", json["Name"]!!.jsonPrimitive.content)
        assertEquals("Comedy", json["ExpressionSets"]!!.jsonArray[0].jsonObject["Expressions"]!!.jsonArray[0].jsonObject["TargetValue"]!!.jsonPrimitive.content)
        assertFalse(json.containsKey("Id"))
        assertFalse(json.containsKey("PlaylistId"))
        assertFalse(json.containsKey("LastRefreshed"))
        assertEquals(0, json["UserPlaylists"]!!.jsonArray.size)
    }

    @Test fun `importing a shared list makes a new list for the chosen owner`() {
        val text = ListDraft.from(shared).forSharing().toJson().toString()
        val draft = ListDraft.fromShared(text, "me")!!
        assertTrue(draft.isNew)
        assertEquals("Weekend", draft.name)
        assertEquals(listOf("me"), draft.users)
        assertEquals("me", draft.ownerId)
        assertEquals(1, draft.groups.single().rules.size)
        assertNull(draft.toJson()["Id"])
    }

    @Test fun `text that is not a list is refused`() {
        assertNull(ListDraft.fromShared("hello", "me"))
        assertNull(ListDraft.fromShared("""{"foo":1}""", "me"))
        assertNull(ListDraft.fromShared("[1,2]", "me"))
    }

    // ----- unsaved changes -----

    @Test fun `an untouched draft equals its baseline and an edit does not`() {
        val draft = ListDraft.from(shared)
        val baseline = draft.toJson()
        assertEquals(baseline, draft.toJson())
        assertTrue(draft.copy(name = "Other").toJson() != baseline)
    }

    // ----- settings -----

    private class FakePrefs : SharedPreferences {
        val map = HashMap<String, Any?>()
        override fun getAll(): MutableMap<String, *> = map
        override fun getString(key: String?, defValue: String?) = map[key] as String? ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?) = defValues
        override fun getInt(key: String?, defValue: Int) = map[key] as Int? ?: defValue
        override fun getLong(key: String?, defValue: Long) = map[key] as Long? ?: defValue
        override fun getFloat(key: String?, defValue: Float) = map[key] as Float? ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean) = map[key] as Boolean? ?: defValue
        override fun contains(key: String?) = map.containsKey(key)
        override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            val pending = HashMap<String, Any?>()
            override fun putString(key: String, value: String?) = apply { pending[key] = value }
            override fun putStringSet(key: String, values: MutableSet<String>?) = apply { }
            override fun putInt(key: String, value: Int) = apply { pending[key] = value }
            override fun putLong(key: String, value: Long) = apply { pending[key] = value }
            override fun putFloat(key: String, value: Float) = apply { pending[key] = value }
            override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }
            override fun remove(key: String) = apply { pending[key] = null }
            override fun clear() = apply { map.clear() }
            override fun commit(): Boolean { apply(); return true }
            override fun apply() { map.putAll(pending) }
        }
    }

    @Test fun `accent, density and list colours survive a restart`() {
        val prefs = FakePrefs()
        val first = AppSettings(prefs)
        assertEquals(Swatch.EmbyGreen, first.accent.value)
        assertEquals(Density.Comfortable, first.density.value)
        first.setAccent(Swatch.Violet)
        first.setDensity(Density.Compact)
        first.setDynamicColor(true)
        first.setListColor("a", Swatch.Rose)
        first.setListColor("b", Swatch.Amber)
        first.setListColor("b", null)

        val second = AppSettings(prefs)
        assertEquals(Swatch.Violet, second.accent.value)
        assertEquals(Density.Compact, second.density.value)
        assertTrue(second.dynamicColor.value)
        assertEquals(mapOf("a" to Swatch.Rose), second.listColors.value)
    }

    @Test fun `every accent gives readable text on its main surfaces in light and dark`() {
        fun contrast(a: Color, b: Color): Double {
            val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
            return (hi + 0.05) / (lo + 0.05)
        }
        for (swatch in Swatch.entries) {
            for (dark in listOf(false, true)) {
                val s = colorSchemeFor(swatch, dark)
                val where = "${swatch.name} dark=$dark"
                assertTrue("$where primary", contrast(s.primary, s.onPrimary) >= 4.5)
                assertTrue("$where container", contrast(s.primaryContainer, s.onPrimaryContainer) >= 4.5)
                assertTrue("$where surface", contrast(s.surface, s.onSurface) >= 7.0)
                assertTrue("$where variant", contrast(s.surfaceVariant, s.onSurfaceVariant) >= 4.5)
                assertTrue("$where on surface primary", contrast(s.surface, s.primary) >= 3.0)
                assertTrue("$where snackbar text", contrast(s.inverseSurface, s.inverseOnSurface) >= 7.0)
                assertTrue("$where snackbar action", contrast(s.inverseSurface, s.inversePrimary) >= 3.0)
            }
        }
    }

    // ----- API: status, preview, backups -----

    private lateinit var server: MockWebServer

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After fun tearDown() = server.shutdown()

    private fun client() = ApiClient(server.url("/").toString(), "tok")

    @Test fun `refresh status reports running lists and the newest outcome of each list`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"ongoingOperations":[{"listId":"a","processedItems":40,"totalItems":100}],
                    "history":[{"listId":"b","startTime":"2026-10-10T10:00:00Z","endTime":"2026-10-10T10:00:05Z","success":false,"errorMessage":"No user"},
                               {"listId":"b","startTime":"2026-10-10T09:00:00Z","endTime":"2026-10-10T09:00:05Z","success":true},
                               {"listId":"c","startTime":"2026-10-10T09:00:00Z","endTime":"2026-10-10T09:00:05Z","success":true}]}""",
            ),
        )
        val status = client().refreshStatus()
        assertEquals("/emby/Plugins/SmartLists/Status", server.takeRequest().path)
        assertEquals(40, status.running["a"]!!.processed)
        assertEquals(100, status.running["a"]!!.total)
        assertFalse(status.last["b"]!!.success)
        assertEquals("No user", status.last["b"]!!.error)
        assertTrue(status.last["c"]!!.success)
    }

    @Test fun `preview posts the list and reads the total and items`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"Total":9,"Items":[{"Id":15,"Name":"Bulk Movie 001","Type":"Movie","Detail":"1901"}]}"""))
        val result = client().preview(JsonObject(emptyMap()), limit = 5)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/emby/Plugins/SmartLists/preview?limit=5", request.path)
        assertEquals("tok", request.getHeader("X-Emby-Token"))
        assertEquals(9, result.total)
        assertEquals("Bulk Movie 001", result.items.single().name)
        assertEquals("1901", result.items.single().detail)
    }

    @Test fun `a server without the preview call answers not found`() {
        server.enqueue(MockResponse().setResponseCode(404))
        val e = org.junit.Assert.assertThrows(com.feerlessleadr.embidextrous.data.ApiException::class.java) { runBlocking { client().preview(JsonObject(emptyMap())) } }
        assertEquals(404, e.status)
    }

    @Test fun `backups are listed newest first and a restore reports its counts`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"backups":[{"filename":"old.zip","createdAt":"2026-10-01T00:00:00Z","sizeBytes":1200,"listCount":2},
                               {"filename":"new.zip","createdAt":"2026-10-09T00:00:00Z","sizeBytes":3400,"listCount":5}]}""",
            ),
        )
        assertEquals(listOf("new.zip", "old.zip"), client().backups().map { it.filename })
        server.takeRequest()

        server.enqueue(MockResponse().setBody("""{"restored":3,"overwritten":1,"skipped":2,"errors":0}"""))
        val r = client().restoreBackup("new.zip", overwrite = true)
        assertEquals("/emby/Plugins/SmartLists/backups/new.zip/restore?overwrite=true", server.takeRequest().path)
        assertEquals(3, r.restored)
        assertEquals(1, r.overwritten)
        assertEquals(2, r.skipped)
    }

    @Test fun `a cover that does not exist is null, not an error`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        assertNull(client().embyCover("123"))
        val request = server.takeRequest()
        assertTrue(request.path!!.startsWith("/emby/Items/123/Images/Primary"))
        assertEquals("tok", request.getHeader("X-Emby-Token"))
    }
}