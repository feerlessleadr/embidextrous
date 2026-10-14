package com.feerlessleadr.embidextrous

import com.feerlessleadr.embidextrous.data.AppJson
import com.feerlessleadr.embidextrous.data.AppProfile
import com.feerlessleadr.embidextrous.data.TrViolation
import com.feerlessleadr.embidextrous.notifications.NotifAvailability
import com.feerlessleadr.embidextrous.notifications.NotifCatalog
import com.feerlessleadr.embidextrous.notifications.NotifRule
import com.feerlessleadr.embidextrous.notifications.NotifService
import com.feerlessleadr.embidextrous.notifications.PluginEventEngine
import com.feerlessleadr.embidextrous.notifications.QuietHours
import com.feerlessleadr.embidextrous.notifications.SeqTracker
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginEventEngineTest {
    /** Label Maker's own sample payloads (copied from its docs/reference/notification-samples.json). */
    private val samples: JsonObject = AppJson.parseToJsonElement(
        javaClass.getResourceAsStream("/label-maker-notification-samples.json")!!.bufferedReader().readText(),
    ).jsonObject

    private fun pushed(name: String) = samples["pushed"]!!.jsonObject[name]!!
    private fun rule(event: String, vararg params: Pair<String, String>) = NotifRule("r-$event", event, true, mapOf(*params))
    private fun on(vararg events: String) = events.map { rule(it) }
    private fun obj(text: String) = AppJson.parseToJsonElement(text)

    // ---- Label Maker ---------------------------------------------------------------------------------------

    @Test
    fun needsReviewNamesTheAlbumsAndRespectsTheMinimum() {
        val rules = listOf(rule(NotifCatalog.Ids.LM_NEEDS_REVIEW, "MinCount" to "5"))
        val d = PluginEventEngine.handle("LabelMaker.NeedsReview", pushed("LabelMaker.NeedsReview"), rules).deliveries.single()
        assertEquals("7 albums need review", d.title)
        assertTrue(d.text.contains("Example Band: Example Album"))
        assertEquals("Music needs review. Unlock to see which.", d.lockScreenText)
        assertEquals(NotifService.LabelMaker, d.service)
        // Seven albums wait, so a rule that wants ten stays quiet.
        val quiet = PluginEventEngine.handle("LabelMaker.NeedsReview", pushed("LabelMaker.NeedsReview"), listOf(rule(NotifCatalog.Ids.LM_NEEDS_REVIEW, "MinCount" to "10")))
        assertTrue(quiet.deliveries.isEmpty())
    }

    @Test
    fun aRuleThatIsOffOrMissingSendsNothing() {
        assertTrue(PluginEventEngine.handle("LabelMaker.AlbumFailed", pushed("LabelMaker.AlbumFailed"), emptyList()).deliveries.isEmpty())
        val off = listOf(rule(NotifCatalog.Ids.LM_ALBUM_FAILED).copy(enabled = false))
        assertTrue(PluginEventEngine.handle("LabelMaker.AlbumFailed", pushed("LabelMaker.AlbumFailed"), off).deliveries.isEmpty())
    }

    @Test
    fun autoAppliedSaysWhenItWasOnlyADryRun() {
        val rules = on(NotifCatalog.Ids.LM_AUTO_APPLIED)
        val dry = PluginEventEngine.handle("LabelMaker.AutoApplied", pushed("LabelMaker.AutoApplied.dryRun"), rules).deliveries.single()
        assertTrue(dry.title.contains("would have been tagged"))
        val real = PluginEventEngine.handle("LabelMaker.AutoApplied", pushed("LabelMaker.AutoApplied"), rules).deliveries.single()
        assertTrue(real.title.contains("12"))
    }

    @Test
    fun aLaneProblemIsClearedByItsRecovery() {
        val rules = on(NotifCatalog.Ids.LM_LANE_PROBLEM)
        val problem = PluginEventEngine.handle("LabelMaker.LaneProblem", pushed("LabelMaker.LaneProblem"), rules).deliveries.single()
        assertEquals("Kids has a problem", problem.title)
        val recovered = PluginEventEngine.handle("LabelMaker.LaneRecovered", pushed("LabelMaker.LaneRecovered"), rules)
        assertEquals(listOf(problem.key), recovered.clears)
    }

    @Test
    fun theMusicLookupAlertClearsWhenItRecovers() {
        val rules = on(NotifCatalog.Ids.LM_SERVICE_TROUBLE)
        val trouble = PluginEventEngine.handle("LabelMaker.ServiceTrouble", pushed("LabelMaker.ServiceTrouble"), rules).deliveries.single()
        assertTrue(trouble.text.contains("9"))
        assertEquals(listOf(trouble.key), PluginEventEngine.handle("LabelMaker.ServiceRecovered", pushed("LabelMaker.ServiceRecovered"), rules).clears)
    }

    @Test
    fun passFinishedAndUnknownEventsAreIgnored() {
        val rules = on(NotifCatalog.Ids.LM_NEEDS_REVIEW, NotifCatalog.Ids.LM_ALBUM_FAILED)
        assertTrue(PluginEventEngine.handle("LabelMaker.PassFinished", pushed("LabelMaker.PassFinished"), rules).deliveries.isEmpty())
        assertTrue(PluginEventEngine.handle("LabelMaker.SomethingNew", pushed("LabelMaker.PassFinished"), rules).deliveries.isEmpty())
        assertTrue(PluginEventEngine.handle("Sessions", null, rules).deliveries.isEmpty())
    }

    @Test
    fun theCatalogOfLabelMakerIsReadIntoAlertTypes() {
        val specs = NotifCatalog.parse(NotifService.LabelMaker, samples["catalog"].toString())!!
        assertEquals(5, specs.size)
        val review = specs.single { it.id == NotifCatalog.Ids.LM_NEEDS_REVIEW }
        assertEquals("MinCount", review.params.single().name)
        assertEquals("1", review.params.single().default)
        assertFalse(specs.single { it.id == NotifCatalog.Ids.LM_AUTO_APPLIED }.defaultOn)
        assertEquals("library/labelmaker", review.opensTab)
        assertNull(NotifCatalog.parse(NotifService.LabelMaker, "not json"))
    }

    @Test
    fun theCatchUpAnswerIsReadOldestFirstAndTruncationIsNoticed() {
        val events = samples["events"].toString()
        val list = PluginEventEngine.parseCaughtUp(events)!!
        assertEquals("LabelMaker.ServiceRecovered", list.single().first)
        assertFalse(PluginEventEngine.truncated(events))
        assertTrue(PluginEventEngine.truncated("""{"Truncated":true,"Events":[]}"""))
    }

    // ---- event numbers -------------------------------------------------------------------------------------

    @Test
    fun anEventNumberIsAcceptedOnceAndOlderOnesAreDropped() {
        val t = SeqTracker()
        assertTrue(t.accept(NotifService.LabelMaker, 41))
        assertFalse(t.accept(NotifService.LabelMaker, 41))
        assertFalse(t.accept(NotifService.LabelMaker, 40))
        assertTrue(t.accept(NotifService.LabelMaker, 42))
        // Another service counts on its own, and a message without a number always counts.
        assertTrue(t.accept(NotifService.HallPass, 1))
        assertTrue(t.accept(NotifService.PluginManager, null))
        assertEquals(42L, t.lastSeen(NotifService.LabelMaker))
        assertEquals(mapOf("LabelMaker" to 42L, "HallPass" to 1L), t.snapshot())
    }

    // ---- Hall Pass, Smart lists, plugin manager, server ----------------------------------------------------

    @Test
    fun hallPassEventsBecomeSentences() {
        val rules = on(NotifCatalog.Ids.HP_SCAN_FINISHED, NotifCatalog.Ids.HP_APPLY_FAILED)
        val scan = PluginEventEngine.handle("HallPass.ScanFinished", obj("""{"Seq":3,"Scanned":120,"NeedsReview":9,"Failed":0}"""), rules).deliveries.single()
        assertEquals("120 track(s) checked. 9 need review.", scan.text)
        val fail = PluginEventEngine.handle("HallPass.ApplyFailed", obj("""{"Kind":"Undo","Message":"Nothing was written."}"""), rules).deliveries.single()
        assertEquals("Undo did not finish", fail.title)
        val queue = listOf(rule(NotifCatalog.Ids.HP_REVIEW_QUEUE, "MinCount" to "50"))
        assertTrue(PluginEventEngine.handle("HallPass.ReviewQueue", obj("""{"Count":12}"""), queue).deliveries.isEmpty())
        assertEquals("60 tracks wait for review", PluginEventEngine.handle("HallPass.ReviewQueue", obj("""{"Count":60}"""), queue).deliveries.single().title)
    }

    @Test
    fun smartListsFailuresAreBatched() {
        val rules = on(NotifCatalog.Ids.SL_REFRESH_FAILED)
        val one = PluginEventEngine.handle("SmartLists.RefreshFailed", obj("""{"Count":1,"Lists":[{"ListName":"Weekend Movies","Error":"Library not found"}]}"""), rules).deliveries.single()
        assertEquals("Weekend Movies failed to refresh", one.title)
        val many = PluginEventEngine.handle("SmartLists.RefreshFailed", obj("""{"Count":3,"Lists":[{"ListName":"A"},{"ListName":"B"},{"ListName":"C"}]}"""), rules).deliveries.single()
        assertEquals("3 smart lists failed to refresh", many.title)
    }

    @Test
    fun pluginManagerRunsReportFailuresWaitingUpdatesAndRestarts() {
        val rules = on(NotifCatalog.Ids.PM_UPDATE_FAILED, NotifCatalog.Ids.PM_UPDATES_READY, NotifCatalog.Ids.PM_RESTART)
        val run = PluginEventEngine.handle("GitHubRepoPluginInstall.RunCompleted", obj("""{"Failed":1,"Waiting":2,"Updated":0,"FailedPlugins":["Hall Pass"]}"""), rules)
        assertEquals(setOf("A plugin update failed", "2 plugin updates are ready"), run.deliveries.map { it.title }.toSet())
        assertEquals("Hall Pass", run.deliveries.first { it.title == "A plugin update failed" }.text)
        val restart = PluginEventEngine.handle("GitHubRepoPluginInstall.RestartPending", obj("""{"Updated":1,"UpdatedPlugins":["Hall Monitor"]}"""), rules).deliveries.single()
        assertEquals("Emby needs a restart", restart.title)
        // A clean run says nothing unless the "run finished" rule is on.
        assertTrue(PluginEventEngine.handle("GitHubRepoPluginInstall.RunCompleted", obj("""{"Failed":0,"Waiting":0,"Updated":1}"""), rules).deliveries.isEmpty())
    }

    @Test
    fun failedScheduledTasksAreAnnouncedOnce() {
        val rules = on(NotifCatalog.Ids.SV_TASK_FAILED)
        val tasks = obj("""[{"Id":"t1","Name":"Scan media library","LastExecutionResult":{"Status":"Failed","EndTimeUtc":"2026-10-14T01:00:00Z"}},{"Id":"t2","Name":"Ok","LastExecutionResult":{"Status":"Completed","EndTimeUtc":"2026-10-14T01:00:00Z"}}]""")
        val first = PluginEventEngine.tasks(tasks, rules, emptyMap(), firstLook = true)
        assertTrue(first.first.deliveries.isEmpty())
        assertEquals(mapOf("t1" to "2026-10-14T01:00:00Z"), first.second)
        // The same failure is not announced again; a new failure is.
        assertTrue(PluginEventEngine.tasks(tasks, rules, first.second, firstLook = false).first.deliveries.isEmpty())
        val later = obj("""[{"Id":"t1","Name":"Scan media library","LastExecutionResult":{"Status":"Failed","EndTimeUtc":"2026-10-15T01:00:00Z"}}]""")
        assertEquals("Scan media library failed", PluginEventEngine.tasks(later, rules, first.second, firstLook = false).first.deliveries.single().title)
    }

    // ---- the plugin subscription message ---------------------------------------------------------------------

    @Test
    fun aPluginEventsMessageIsReadIntoItsEvents() {
        val data = obj("""{"ApiLevel":1,"LatestSeq":7,"Truncated":false,"Events":[{"Seq":6,"Name":"HallPass.ScanFinished","AtUtc":"x","Data":{"Seq":6,"Scanned":9}},{"Seq":7,"Name":"HallPass.ScanFailed","AtUtc":"x","Data":{"Seq":7}}]}""")
        val env = PluginEventEngine.parseEnvelope("HallPassEvents", data)!!
        assertEquals(NotifService.HallPass, env.service)
        assertEquals(listOf("HallPass.ScanFinished", "HallPass.ScanFailed"), env.events.map { it.first })
        assertEquals(7L, env.latestSeq)
        assertNull(env.error)
        // A heartbeat has no events; an error is carried; other messages are not ours.
        assertTrue(PluginEventEngine.parseEnvelope("HallPassEvents", obj("""{"LatestSeq":7,"Events":[]}"""))!!.events.isEmpty())
        assertEquals("Administrator login required.", PluginEventEngine.parseEnvelope("SmartListsEvents", obj("""{"Error":"Administrator login required."}"""))!!.error)
        assertEquals(NotifService.PluginManager, PluginEventEngine.parseEnvelope("GitHubRepoPluginInstallEvents", data)!!.service)
        assertNull(PluginEventEngine.parseEnvelope("Sessions", data))
    }

    @Test
    fun theStartMessageOfAPluginSubscriptionCarriesTheLastNumberSeenAndTheToken() {
        val hp = com.feerlessleadr.embidextrous.push.EmbyTopic.HallPassEvents
        val start = com.feerlessleadr.embidextrous.push.EmbyMessages
        assertEquals("""{"MessageType":"HallPassEventsStart","Data":"0,30000,41,tok"}""", start.start(hp, 41, "tok"))
        // Nothing seen yet is an empty value (a 0 would replay the whole log).
        assertEquals("""{"MessageType":"HallPassEventsStart","Data":"0,30000,,tok"}""", start.start(hp, null, "tok"))
        // Other topics never carry a token.
        assertEquals("""{"MessageType":"SessionsStart","Data":"0,1500"}""", start.start(com.feerlessleadr.embidextrous.push.EmbyTopic.Sessions, 41, "tok"))
    }

    // ---- Tracearr ------------------------------------------------------------------------------------------

    private fun violation(id: String, severity: String) = TrViolation(id, severity, 0L, false, "Too many streams", "Attic", "u", "Alex", "A Movie", null)

    @Test
    fun newViolationsAreAnnouncedOnceAndTheFirstLookOnlyRecords() {
        val rules = listOf(rule(NotifCatalog.Ids.TR_VIOLATION, "severity" to "warning"))
        val list = listOf(violation("a", "high"), violation("b", "low"))
        val first = PluginEventEngine.violations(list, rules, emptySet(), firstLook = true)
        assertTrue(first.first.deliveries.isEmpty())
        assertEquals(setOf("a", "b"), first.second)
        val next = PluginEventEngine.violations(listOf(violation("c", "high"), violation("d", "low")) + list, rules, first.second, firstLook = false)
        // "d" is below the chosen severity; "c" is announced.
        assertEquals(listOf("Too many streams was broken"), next.first.deliveries.map { it.title })
        assertEquals("Alex · A Movie · Attic", next.first.deliveries.single().text)
    }

    @Test
    fun aServerGoingDownIsClearedWhenItIsUp() {
        val rules = on(NotifCatalog.Ids.TR_SERVER_DOWN)
        val down = PluginEventEngine.serverHealth("s1", "Attic", "down", rules).deliveries.single()
        assertEquals("Attic is down", down.title)
        assertEquals(listOf(down.key), PluginEventEngine.serverHealth("s1", "Attic", "up", rules).clears)
    }

    // ---- who can configure what, and quiet hours ------------------------------------------------------------

    @Test
    fun hallMonitorIsForEveryoneAndTheRestFollowTheTabsOfTheAccountAndTheSetup() {
        val onlyHm = listOf(NotifService.HallMonitor)
        // A regular account never gets the rest, whatever its setup says.
        assertEquals(onlyHm, NotifAvailability.services(AppProfile.Administrator, isAdministrator = false, customSmartLists = true))
        assertEquals(onlyHm, NotifAvailability.services(AppProfile.Parent, isAdministrator = true, customSmartLists = false))
        assertEquals(onlyHm, NotifAvailability.services(AppProfile.Custom, isAdministrator = true, customSmartLists = false))
        assertEquals(NotifService.entries.toList(), NotifAvailability.services(AppProfile.Administrator, isAdministrator = true, customSmartLists = false))
        assertEquals(NotifService.entries.toList(), NotifAvailability.services(AppProfile.Custom, isAdministrator = true, customSmartLists = true))
    }

    @Test
    fun quietHoursMayCrossMidnight() {
        val night = QuietHours(true, 22 * 60, 7 * 60)
        assertTrue(night.isQuiet(23 * 60))
        assertTrue(night.isQuiet(2 * 60))
        assertFalse(night.isQuiet(12 * 60))
        assertFalse(night.isQuiet(7 * 60))
        val daytime = QuietHours(true, 13 * 60, 15 * 60)
        assertTrue(daytime.isQuiet(14 * 60))
        assertFalse(daytime.isQuiet(16 * 60))
        assertFalse(QuietHours(false, 22 * 60, 7 * 60).isQuiet(23 * 60))
    }
}
