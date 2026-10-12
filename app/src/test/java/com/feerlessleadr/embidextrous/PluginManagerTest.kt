package com.feerlessleadr.embidextrous

import com.feerlessleadr.embidextrous.data.GhOrder
import com.feerlessleadr.embidextrous.data.GhStatus
import com.feerlessleadr.embidextrous.data.GhrpiGate
import com.feerlessleadr.embidextrous.data.GhrpiParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The plugin's answers, written out as its integration guide shows them. */
class PluginManagerTest {
    private val info = """{"PluginVersion":"2026.10.11.1","ApiVersion":1,"MinSupportedApiVersion":1,"ApiEnabled":true,"AllowManage":false,"RunInProgress":false,"RestartPending":false}"""

    @Test fun theGateFollowsTheInfo() {
        assertTrue(GhrpiGate.of(GhrpiParser.info(info)) is GhrpiGate.Ready)
        assertTrue(GhrpiGate.of(GhrpiParser.info(info.replace("\"ApiEnabled\":true", "\"ApiEnabled\":false"))) is GhrpiGate.Off)
        assertTrue(GhrpiGate.of(GhrpiParser.info(info.replace("\"ApiVersion\":1", "\"ApiVersion\":0"))) is GhrpiGate.PluginTooOld)
        assertTrue(GhrpiGate.of(GhrpiParser.info(info.replace("\"MinSupportedApiVersion\":1", "\"MinSupportedApiVersion\":2").replace("\"ApiVersion\":1", "\"ApiVersion\":2"))) is GhrpiGate.AppTooOld)
        // The real server's answer while the API is off
        val off = GhrpiParser.info("""{"PluginVersion":"1.0.0.0","ApiVersion":1,"MinSupportedApiVersion":1,"ApiEnabled":false,"AllowManage":false,"RunInProgress":false,"RestartPending":false}""")
        assertTrue(GhrpiGate.of(off) is GhrpiGate.Off)
    }

    @Test fun manageNeedsBothSwitches() {
        val gate = GhrpiGate.of(GhrpiParser.info(info.replace("\"AllowManage\":false", "\"AllowManage\":true"))) as GhrpiGate.Ready
        assertTrue(gate.info.allowManage)
    }

    @Test fun parsesThePluginList() {
        val text = """{"Items":[{"Id":"5eb6","Name":"Emby.GitHubRepoPluginInstall","Owner":"feerlessleadr","Url":"https://github.com/feerlessleadr/Emby.GitHubRepoPluginInstall",
            "Status":"UpdateWaiting","StatusText":"Update waiting","AutoUpdate":false,"AllowPreRelease":false,"AssetNameFilter":"","InstalledVersion":"2026.10.11",
            "LatestVersion":"2026.10.11.1","LatestPublishedUtc":"2026-10-11T22:12:53.0000000Z","ReleaseNotes":"notes","AssetNames":["a.dll","b.zip"],"RestartPending":false,"SomethingNew":1}]}"""
        val p = GhrpiParser.pluginList(text).single()
        assertEquals(GhStatus.UpdateWaiting, p.status)
        assertTrue(p.status.hasUpdate)
        assertEquals("2026.10.11", p.installedVersion)
        assertEquals(listOf("a.dll", "b.zip"), p.assetNames)
        assertNull(p.backups)
        assertNotNull(p.latestPublishedMs)
        assertNull(p.lastError)
    }

    @Test fun anUnknownStatusWordIsHandled() {
        assertEquals(GhStatus.Unknown, GhrpiParser.pluginList("""[{"Id":"x","Status":"Brand New"}]""").single().status)
        assertEquals(GhStatus.NotChecked, GhStatus.from("NotChecked"))
        assertEquals(GhStatus.Unknown, GhStatus.from(null))
        // a plugin with no id cannot be addressed, so it is skipped
        assertTrue(GhrpiParser.pluginList("""{"Items":[{"Name":"no id"}]}""").isEmpty())
    }

    @Test fun theDetailCarriesBackups() {
        val p = GhrpiParser.singlePlugin("""{"Id":"x","Name":"P","Backups":[{"Version":"1.0","CreatedUtc":"2026-10-10T00:00:00Z","SizeBytes":123}]}""")!!
        assertEquals(1, p.backups!!.size)
        assertEquals("1.0", p.backups!![0].version)
        assertEquals(123L, p.backups!![0].sizeBytes)
    }

    @Test fun parsesTheStatus() {
        val text = """{"Run":{"IsRunning":true,"Total":3,"Done":1,"Current":"Foo","Trigger":"Manual"},
            "LastRun":{"Id":"r1","StartedUtc":"2026-10-11T23:02:11Z","DurationSeconds":1.02,"Trigger":"Scheduled","Updated":0,"UpToDate":1,"Failed":1,"Waiting":1,
              "Results":[{"PluginId":"p1","Name":"Foo","Outcome":"WaitingManual","ToVersion":"2","Message":"Auto-update is off."}]},
            "RestartPending":true,"PendingRestart":[{"Name":"Foo","FromVersion":"1","ToVersion":"2"}],"CanSelfRestart":true,
            "Counts":{"Total":3,"NotChecked":0,"NoRelease":1,"UpToDate":0,"UpdateAvailable":0,"UpdateWaiting":1,"Failed":1},"UpdatesReady":1}"""
        val s = GhrpiParser.status(text)
        assertTrue(s.run.isRunning)
        assertEquals(1, s.run.done)
        assertEquals("Foo", s.run.current)
        assertTrue(s.restartPending)
        assertEquals("Foo", s.pendingRestart.single().name)
        assertEquals(1, s.updatesReady)
        assertEquals(3, s.counts.total)
        assertEquals("WaitingManual", s.lastRun!!.results.single().outcome)
        // no last run yet, nothing else: defaults, not a crash
        val empty = GhrpiParser.status("""{"Run":{"IsRunning":false,"Total":0,"Done":0}}""")
        assertNull(empty.lastRun)
        assertFalse(empty.restartPending)
    }

    @Test fun parsesActionResultsRegistriesAndSettings() {
        val a = GhrpiParser.action("""{"Message":"Restored 1.0.","RestartNeeded":true,"Plugin":{"Id":"x","Name":"P"}}""")
        assertTrue(a.restartNeeded)
        assertEquals("P", a.plugin!!.name)
        assertEquals(listOf("embedded-default"), GhrpiParser.registries("""{"Items":[{"Id":"embedded-default","Name":"Built-in","RawUrl":"embedded://default","Enabled":true,"BuiltIn":true}]}""").map { it.id })
        val s = GhrpiParser.settings("""{"TokenSet":true,"RestartServerAfterInstall":false,"ApiEnabled":true,"AllowManage":false}""")
        assertTrue(s.tokenSet)
        assertFalse(s.restartServerAfterInstall)
        assertEquals(true to "ok", GhrpiParser.tokenTest("""{"Ok":true,"Message":"ok"}"""))
    }

    @Test fun parsesAvailablePluginsAndHistory() {
        val av = GhrpiParser.available("""{"Items":[{"Name":"A","Description":"d","Url":"https://github.com/o/a","RegistrySource":"Built-in","AlreadyAdded":true},{"Name":"no url"}]}""")
        assertEquals(1, av.size)
        assertTrue(av[0].alreadyAdded)
        assertEquals(1, GhrpiParser.history("""{"Items":[{"Id":"r","Trigger":"Manual","Updated":1}]}""").size)
    }

    @Test fun thePluginsOwnErrorsAreRecognisedAndEmbysAreNot() {
        val e = GhrpiParser.error("""{"Title":"ManageDisabled","Status":403,"Detail":"Adding plugins is switched off."}""")!!
        assertEquals("ManageDisabled", e.title)
        assertEquals(403, e.status)
        assertEquals("Adding plugins is switched off.", e.detail)
        assertNull(GhrpiParser.error("Forbidden"))
        assertNull(GhrpiParser.error("<html></html>"))
        assertNull(GhrpiParser.error(""))
        // the message shown to the person comes from Detail (capitalised, unlike the SmartLists plugin's)
        assertEquals("Adding plugins is switched off.", com.feerlessleadr.embidextrous.data.ApiClient.describeError(403, """{"Title":"ManageDisabled","Status":403,"Detail":"Adding plugins is switched off."}"""))
    }

    @Test fun addressesAreCheckedBeforeSending() {
        assertNull(GhrpiParser.checkRepoAddress("feerlessleadr/hall-monitor-emby"))
        assertNull(GhrpiParser.checkRepoAddress("https://github.com/feerlessleadr/hall-monitor-emby"))
        assertNotNull(GhrpiParser.checkRepoAddress(""))
        assertNotNull(GhrpiParser.checkRepoAddress("http://github.com/a/b"))
        assertNotNull(GhrpiParser.checkRepoAddress("justaword"))
        assertNotNull(GhrpiParser.checkRepoAddress("a/" + "b".repeat(400)))
    }

    @Test fun problemsComeFirstThenUpdatesThenTheRest() {
        fun p(name: String, s: String) = GhrpiParser.pluginList("""[{"Id":"$name","Name":"$name","Status":"$s"}]""").single()
        val sorted = GhOrder.sorted(listOf(p("d", "UpToDate"), p("b", "UpdateAvailable"), p("a", "Failed"), p("c", "NoRelease"), p("e", "NotChecked")))
        assertEquals(listOf("a", "b", "e", "d", "c"), sorted.map { it.name })
    }
}
