package com.feerlessleadr.embidextrous

import com.feerlessleadr.embidextrous.data.AppProfile
import com.feerlessleadr.embidextrous.data.HomeCard
import com.feerlessleadr.embidextrous.data.HomeLayout
import com.feerlessleadr.embidextrous.data.PluginChecks
import com.feerlessleadr.embidextrous.data.Profiles
import com.feerlessleadr.embidextrous.data.RefreshOutcome
import com.feerlessleadr.embidextrous.data.RefreshStatus
import com.feerlessleadr.embidextrous.data.RunningRefresh
import com.feerlessleadr.embidextrous.data.ServerStatusParser
import com.feerlessleadr.embidextrous.data.SmartListDoc
import com.feerlessleadr.embidextrous.data.InstalledPlugin
import com.feerlessleadr.embidextrous.data.UserStatus
import com.feerlessleadr.embidextrous.home.AdminModel
import com.feerlessleadr.embidextrous.watching.WidgetText
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminHomeTest {
    private val now = Instant.parse("2026-10-10T12:00:00Z")

    // ---- layout ---------------------------------------------------------------------------------------------

    @Test fun defaultLayoutShowsEveryAvailableCardInOrder() {
        val all = HomeCard.availableFor(administrator = true)
        assertEquals(HomeCard.entries, HomeLayout().visible(all))
    }

    @Test fun aPhoneWithoutAdministratorCardsNeverSeesThem() {
        val parent = HomeCard.availableFor(administrator = false)
        assertEquals(listOf(HomeCard.WatchingNow, HomeCard.Kids, HomeCard.Bedtime), HomeLayout().visible(parent))
    }

    @Test fun hidingAndMovingWork() {
        val all = HomeCard.availableFor(true)
        val layout = HomeLayout().toggled(HomeCard.Kids).moved(HomeCard.Bedtime, -1, all)
        assertFalse(HomeCard.Kids in layout.visible(all))
        val shown = layout.visible(all)
        assertTrue(shown.indexOf(HomeCard.Bedtime) < shown.indexOf(HomeCard.WatchingNow) + 2)
        assertEquals(HomeCard.Bedtime, layout.configurable(all)[1])
        assertEquals(layout, layout.toggled(HomeCard.Kids).toggled(HomeCard.Kids))
    }

    @Test fun movingPastTheEndsChangesNothing() {
        val all = HomeCard.availableFor(true)
        val layout = HomeLayout()
        assertEquals(layout, layout.moved(HomeCard.WatchingNow, -1, all))
        assertEquals(layout, layout.moved(HomeCard.Plugins, 1, all))
    }

    @Test fun movingSkipsCardsThisPhoneCannotUse() {
        val parent = HomeCard.availableFor(false)
        // Kids is last but one for a parent; moving it down swaps with Bedtime even though administrator cards follow in the full order.
        val moved = HomeLayout().moved(HomeCard.Bedtime, -1, parent)
        assertEquals(listOf(HomeCard.WatchingNow, HomeCard.Bedtime, HomeCard.Kids), moved.visible(parent))
    }

    @Test fun layoutSurvivesSaving() {
        val all = HomeCard.availableFor(true)
        val layout = HomeLayout().moved(HomeCard.Plugins, -1, all).toggled(HomeCard.Server)
        assertEquals(layout, HomeLayout.decode(layout.encode()))
    }

    @Test fun decodingIsForgiving() {
        assertEquals(HomeLayout(), HomeLayout.decode(null))
        assertEquals(HomeLayout(), HomeLayout.decode(""))
        assertEquals(HomeLayout(), HomeLayout.decode("garbage;;;nonsense"))
        // A card the saved text does not know yet (a newer app added it) is appended; an unknown name is dropped.
        val decoded = HomeLayout.decode("Kids,Gone,WatchingNow;Gone")
        assertEquals(listOf(HomeCard.Kids, HomeCard.WatchingNow), decoded.order.take(2))
        assertEquals(HomeCard.entries.size, decoded.order.size)
        assertTrue(decoded.hidden.isEmpty())
    }

    @Test fun adminCardsFollowTheSmartListsRule() {
        assertTrue(Profiles.showsAdminCards(AppProfile.Administrator, true, false))
        assertFalse(Profiles.showsAdminCards(AppProfile.Administrator, false, false))
        assertFalse(Profiles.showsAdminCards(AppProfile.Parent, true, true))
        assertTrue(Profiles.showsAdminCards(AppProfile.Custom, true, true))
        assertFalse(Profiles.showsAdminCards(AppProfile.Custom, true, false))
    }

    // ---- server state ---------------------------------------------------------------------------------------

    @Test fun parsesSystemInfo() {
        val info = ServerStatusParser.info("""{"ServerName":"Home","Version":"4.10.1.0","HasPendingRestart":true,"HasUpdateAvailable":false,"CanSelfRestart":true,"Extra":1}""")
        assertEquals("Home", info.name)
        assertEquals("4.10.1.0", info.version)
        assertTrue(info.restartPending)
        assertFalse(info.updateAvailable)
        assertTrue(info.canSelfRestart)
        // Missing fields give defaults instead of an error.
        assertEquals("", ServerStatusParser.info("{}").version)
    }

    @Test fun parsesPluginsSortedAndSkipsNamelessOnes() {
        val plugins = ServerStatusParser.plugins("""[{"Name":"b","Version":"1","Id":"x"},{"Version":"9"},{"Name":"A","Version":"2"}]""")
        assertEquals(listOf("A", "b"), plugins.map { it.name })
    }

    @Test fun onlyFailedTasksCount() {
        val text = """[
            {"Name":"Ok","LastExecutionResult":{"Status":"Completed"}},
            {"Name":"Bad","LastExecutionResult":{"Status":"Failed","EndTimeUtc":"2026-10-10T01:00:00Z"}},
            {"Name":"Stopped","LastExecutionResult":{"Status":"Aborted"}},
            {"Name":"Never run"}
        ]"""
        assertEquals(listOf("Bad"), ServerStatusParser.failedTasks(text).map { it.name })
    }

    @Test fun countsConnectedPlayingAndTranscoding() {
        val text = """[
            {"LastActivityDate":"2026-10-10T11:58:00Z"},
            {"LastActivityDate":"2026-10-10T08:00:00Z"},
            {"LastActivityDate":"2026-10-10T11:59:00Z","NowPlayingItem":{"Name":"A"}},
            {"LastActivityDate":"2026-10-10T11:59:30Z","NowPlayingItem":{"Name":"B"},"TranscodingInfo":{"IsVideoDirect":false}}
        ]"""
        val counts = ServerStatusParser.sessions(text, now.toEpochMilli())
        assertEquals(3, counts.active) // the one idle for hours is not counted
        assertEquals(2, counts.playing)
        assertEquals(1, counts.transcoding)
    }

    @Test fun packageUpdatesUseAnyNameCasing() {
        assertEquals(listOf("A", "B", "A plugin"), ServerStatusParser.packageUpdates("""[{"name":"A"},{"Name":"B"},{}]"""))
        assertTrue(ServerStatusParser.packageUpdates("[]").isEmpty())
    }

    // ---- plugin versions ------------------------------------------------------------------------------------

    private fun plugin(name: String, version: String) = InstalledPlugin("id", name, version)

    @Test fun hallMonitorVersionVerdicts() {
        assertEquals(PluginChecks.Verdict.Missing, PluginChecks.hallMonitor(emptyList()))
        assertEquals(PluginChecks.Verdict.TooOld("1.8.0.0"), PluginChecks.hallMonitor(listOf(plugin("Watching Eye", "1.8.0.0"))))
        assertEquals(PluginChecks.Verdict.Fine("1.9.0.0"), PluginChecks.hallMonitor(listOf(plugin("Watching Eye", "1.9.0.0"))))
        assertEquals(PluginChecks.Verdict.Fine("1.10.0.0"), PluginChecks.hallMonitor(listOf(plugin("Hall Monitor", "1.10.0.0"))))
    }

    @Test fun versionsCompareNumerically() {
        assertTrue(PluginChecks.compareVersions("1.10.0.0", "1.9") > 0) // 10 is more than 9, not text order
        assertEquals(0, PluginChecks.compareVersions("1.9.0.0", "1.9"))
        assertTrue(PluginChecks.compareVersions("0.1010.1629.0", "0.1.9") > 0)
        assertTrue(PluginChecks.compareVersions("abc", "1") < 0)
    }

    // ---- smart-lists snapshot -------------------------------------------------------------------------------

    private fun list(id: String, name: String, enabled: Boolean = true, refreshed: String? = null): SmartListDoc {
        val refreshedField = if (refreshed == null) "" else ""","LastRefreshed":"$refreshed""""
        return SmartListDoc(Json.parseToJsonElement("""{"Id":"$id","Name":"$name","Enabled":$enabled$refreshedField}""") as JsonObject)
    }

    @Test fun snapshotCountsEnabledRefreshingAndFailed() {
        val lists = listOf(
            list("a", "A", refreshed = "2026-10-10T10:00:00.0000000Z"),
            list("b", "B", refreshed = "2026-10-10T11:00:00Z"),
            list("c", "C", enabled = false),
            list("d", "D"),
        )
        val status = RefreshStatus(
            running = mapOf("d" to RunningRefresh(1, 10)),
            last = mapOf(
                "a" to RefreshOutcome(true, null, null),
                "b" to RefreshOutcome(false, "boom", null),
                "c" to RefreshOutcome(false, "off", null), // switched off: not a problem
                "d" to RefreshOutcome(false, "old failure", null), // running again: not shown as failed
            ),
        )
        val snapshot = AdminModel.lists(lists, status)
        assertEquals(4, snapshot.total)
        assertEquals(3, snapshot.enabled)
        assertEquals(1, snapshot.refreshing)
        assertEquals(listOf("B"), snapshot.failed)
        assertEquals(Instant.parse("2026-10-10T11:00:00Z"), snapshot.lastRefreshed)
    }

    @Test fun emptySnapshotHasNoLastRefresh() {
        val snapshot = AdminModel.lists(emptyList(), RefreshStatus.Empty)
        assertEquals(0, snapshot.total)
        assertNull(snapshot.lastRefreshed)
    }

    // ---- widget headline ------------------------------------------------------------------------------------

    private fun user(name: String, reason: String? = "Allowed", playing: Boolean = false, enabled: Boolean = true) = UserStatus(
        userId = name,
        username = name,
        blockReason = reason,
        isEnabled = enabled,
        nowPlaying = if (playing) com.feerlessleadr.embidextrous.data.NowPlaying(itemId = "1", itemName = "Show") else null,
    )

    @Test fun widgetHeadlineSaysAllGoodOrWhoIsAtTheLimit() {
        assertEquals("All good", WidgetText.summary(listOf(user("Sam")), now).text)
        assertEquals("All good · 1 watching", WidgetText.summary(listOf(user("Sam", playing = true)), now).text)
        val one = WidgetText.summary(listOf(user("Sam", "TimeLimitExceeded"), user("Kim")), now)
        assertEquals("Sam at the limit", one.text)
        assertTrue(one.needsAttention)
        assertEquals("Sam and Kim at the limit", WidgetText.summary(listOf(user("Sam", "TimeLimitExceeded"), user("Kim", "TimeLimitExceeded")), now).text)
        assertEquals("3 people at the limit", WidgetText.summary(listOf("A", "B", "C").map { user(it, "TimeLimitExceeded") }, now).text)
    }

    @Test fun widgetHeadlineRespectsHiddenNamesAndSwitchedOffLimits() {
        assertEquals("User 2 at the limit", WidgetText.summary(listOf(user("Sam"), user("Kim", "TimeLimitExceeded")), now, hideNames = true).text)
        assertFalse(WidgetText.summary(listOf(user("Sam", "TimeLimitExceeded", enabled = false)), now).needsAttention)
    }
}

class GreetingTest {
    @Test fun greetingFollowsTheHour() {
        assertEquals("Good evening", com.feerlessleadr.embidextrous.home.Greeting.forHour(0))
        assertEquals("Good evening", com.feerlessleadr.embidextrous.home.Greeting.forHour(4))
        assertEquals("Good morning", com.feerlessleadr.embidextrous.home.Greeting.forHour(5))
        assertEquals("Good morning", com.feerlessleadr.embidextrous.home.Greeting.forHour(11))
        assertEquals("Good afternoon", com.feerlessleadr.embidextrous.home.Greeting.forHour(12))
        assertEquals("Good afternoon", com.feerlessleadr.embidextrous.home.Greeting.forHour(17))
        assertEquals("Good evening", com.feerlessleadr.embidextrous.home.Greeting.forHour(18))
        assertEquals("Good evening", com.feerlessleadr.embidextrous.home.Greeting.forHour(23))
    }
}

class AdminCardsModelTest {
    private fun doc(id: String, name: String, items: Int?, enabled: Boolean = true): com.feerlessleadr.embidextrous.data.SmartListDoc {
        val count = if (items == null) "" else ""","ItemCount":$items"""
        return com.feerlessleadr.embidextrous.data.SmartListDoc(
            kotlinx.serialization.json.Json.parseToJsonElement("""{"Id":"$id","Name":"$name","Enabled":$enabled$count}""") as kotlinx.serialization.json.JsonObject,
        )
    }

    @Test fun rowsPutTroubleFirstAndOffLast() {
        val lists = listOf(doc("a", "Alpha", 10), doc("b", "Bravo", 20, enabled = false), doc("c", "Charlie", 5), doc("d", "Delta", 7), doc("e", "Echo", null))
        val status = com.feerlessleadr.embidextrous.data.RefreshStatus(
            running = mapOf("d" to com.feerlessleadr.embidextrous.data.RunningRefresh(1, 4)),
            last = mapOf("c" to com.feerlessleadr.embidextrous.data.RefreshOutcome(false, "x", null)),
        )
        val snapshot = com.feerlessleadr.embidextrous.home.AdminModel.lists(lists, status)
        assertEquals(listOf("Charlie", "Delta", "Alpha"), snapshot.rows.map { it.name })
        assertEquals(com.feerlessleadr.embidextrous.home.ListState.Failed, snapshot.rows[0].state)
        assertEquals(com.feerlessleadr.embidextrous.home.ListState.Refreshing, snapshot.rows[1].state)
        assertEquals(10 + 5 + 7, snapshot.totalItems) // the disabled list does not count; a list with no count adds nothing
        assertEquals(5, snapshot.total)
    }

    @Test fun taskSummaryCountsAllAndFailed() {
        val text = """[{"Name":"A","LastExecutionResult":{"Status":"Completed"}},{"Name":"B","LastExecutionResult":{"Status":"Failed"}},{"Name":"C"}]"""
        val summary = ServerStatusParser.tasks(text)
        assertEquals(3, summary.total)
        assertEquals(listOf("B"), summary.failed.map { it.name })
    }
}
