package com.feerlessleadr.embidextrous

import com.feerlessleadr.embidextrous.data.LimitStatus
import com.feerlessleadr.embidextrous.data.UserCard
import com.feerlessleadr.embidextrous.data.UserStatus
import com.feerlessleadr.embidextrous.notifications.NotifCatalog
import com.feerlessleadr.embidextrous.notifications.NotifService
import com.feerlessleadr.embidextrous.notifications.NotifRule
import com.feerlessleadr.embidextrous.notifications.NotifRules
import com.feerlessleadr.embidextrous.notifications.PercentRules
import com.feerlessleadr.embidextrous.notifications.percentUsed
import com.feerlessleadr.embidextrous.notifications.periodPercents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotifRulesTest {
    private fun pct(id: String, percent: Int, period: String = "any", users: List<String> = emptyList()) =
        NotifRule(id, NotifCatalog.Ids.HM_PERCENT, true, mapOf("percent" to percent.toString(), "period" to period), users)

    private val eighty = pct("p80", 80)

    /** A limited user with [allowed] seconds in the closest period and [remaining] left. */
    private fun status(id: String = "u1", name: String = "Alex", allowed: Double = 3600.0, remaining: Double = 3600.0, enabled: Boolean? = true) =
        UserStatus(
            userId = id,
            username = name,
            isEnabled = enabled,
            limitingAllowedSeconds = allowed,
            secondsRemaining = remaining,
            limitingPeriod = "Daily",
        )

    private fun cards(vararg users: UserStatus) = users.map { UserCard(it, 0L) }

    // ---- percent used --------------------------------------------------------------------------------------

    @Test
    fun percentIsUsedShareOfTheClosestLimit() {
        assertEquals(0, percentUsed(status(remaining = 3600.0)))
        assertEquals(50, percentUsed(status(remaining = 1800.0)))
        assertEquals(100, percentUsed(status(remaining = 0.0)))
    }

    @Test
    fun percentIsNullWithoutALimitOrWhenSwitchedOff() {
        assertNull(percentUsed(status(remaining = -1.0)))
        assertNull(percentUsed(status(allowed = 0.0, remaining = 0.0)))
        assertNull(percentUsed(status(enabled = false, remaining = 1800.0)))
    }

    @Test
    fun percentUsesTheCreditAlreadyInTheAllowance() {
        // The plugin reports the allowance with extra time added, so 60 minutes plus 30 of credit and 45 used is 50%.
        assertEquals(50, percentUsed(status(allowed = 5400.0, remaining = 2700.0)))
    }

    // ---- percent rule firing ------------------------------------------------------------------------------

    @Test
    fun firesOnceWhenTheThresholdIsReachedAndNotAgainThatDay() {
        val first = PercentRules.evaluate(listOf(eighty), cards(status(remaining = 600.0)), armed(eighty), "2026-10-13", firstLook = false)
        assertEquals(1, first.fires.size)
        assertEquals(83, first.fires.single().percent)

        val again = PercentRules.evaluate(listOf(eighty), cards(status(remaining = 500.0)), first.state, "2026-10-13", firstLook = false)
        assertTrue(again.fires.isEmpty())
    }

    @Test
    fun rearmsWhenTimeIsAddedAndFiresAgainLater() {
        val first = PercentRules.evaluate(listOf(eighty), cards(status(remaining = 600.0)), armed(eighty), "2026-10-13", firstLook = false)
        // 30 more minutes added: 50% used, below the 80% rule, so it rearms.
        val lowered = PercentRules.evaluate(listOf(eighty), cards(status(allowed = 5400.0, remaining = 2700.0)), first.state, "2026-10-13", firstLook = false)
        assertTrue(lowered.fires.isEmpty())
        assertFalse(lowered.state.containsKey("p80|u1|daily"))
        val later = PercentRules.evaluate(listOf(eighty), cards(status(allowed = 5400.0, remaining = 600.0)), lowered.state, "2026-10-13", firstLook = false)
        assertEquals(1, later.fires.size)
    }

    @Test
    fun staysQuietAcrossMidnightWhileStillOverTheRule() {
        // Hall Monitor's day starts at the reset time (3 a.m.), so the same limit can still be 83% after midnight.
        val day1 = PercentRules.evaluate(listOf(eighty), cards(status(remaining = 600.0)), armed(eighty), "2026-10-13", firstLook = false)
        val day2 = PercentRules.evaluate(listOf(eighty), cards(status(remaining = 600.0)), day1.state, "2026-10-14", firstLook = false)
        assertTrue(day2.fires.isEmpty())
    }

    @Test
    fun firesAgainAfterTheLimitResetsAndIsReachedAgain() {
        val first = PercentRules.evaluate(listOf(eighty), cards(status(remaining = 600.0)), armed(eighty), "2026-10-13", firstLook = false)
        val reset = PercentRules.evaluate(listOf(eighty), cards(status(remaining = 3600.0)), first.state, "2026-10-14", firstLook = false)
        val again = PercentRules.evaluate(listOf(eighty), cards(status(remaining = 600.0)), reset.state, "2026-10-14", firstLook = false)
        assertEquals(1, again.fires.size)
    }

    @Test
    fun firstLookRecordsWithoutFiring() {
        val first = PercentRules.evaluate(listOf(eighty), cards(status(remaining = 600.0)), armed(eighty), "2026-10-13", firstLook = true)
        assertTrue(first.fires.isEmpty())
        // The record stops a later update from firing for the same day.
        val next = PercentRules.evaluate(listOf(eighty), cards(status(remaining = 600.0)), first.state, "2026-10-13", firstLook = false)
        assertTrue(next.fires.isEmpty())
    }

    @Test
    fun onlyEnabledPercentRulesAndEnabledUsersFire() {
        val off = eighty.copy(enabled = false)
        val allTime = NotifRule("all", NotifCatalog.Ids.HM_ALL_USED)
        val result = PercentRules.evaluate(
            listOf(off, allTime),
            cards(status(id = "u1", remaining = 0.0), status(id = "u2", remaining = 0.0, enabled = false)),
            armed(eighty),
            "2026-10-13",
            firstLook = false,
        )
        assertTrue(result.fires.isEmpty())
    }

    @Test
    fun eachUserFiresSeparately() {
        val result = PercentRules.evaluate(
            listOf(eighty),
            cards(status(id = "u1", name = "Alex", remaining = 0.0), status(id = "u2", name = "Sam", remaining = 3000.0)),
            armed(eighty),
            "2026-10-13",
            firstLook = false,
        )
        assertEquals(listOf("Alex"), result.fires.map { it.user.username })
    }

    // ---- storage and migration -----------------------------------------------------------------------------

    @Test
    fun the09RulesAreReadAsNewRulesWithTheirIdsKept() {
        val old = """[{"id":"limit-reached","kind":"AllTimeUsed","enabled":true},{"id":"p80","kind":"PercentUsed","percent":75,"enabled":false}]"""
        val rules = NotifRules.migrateLegacy(old)!!
        assertEquals(listOf("limit-reached", "p80"), rules.map { it.id })
        assertEquals(NotifCatalog.Ids.HM_ALL_USED, rules[0].event)
        assertTrue(rules[0].enabled)
        assertEquals(NotifCatalog.Ids.HM_PERCENT, rules[1].event)
        assertEquals(75, rules[1].int("percent", 0))
        assertFalse(rules[1].enabled)
        assertNull(NotifRules.migrateLegacy("not json"))
    }

    @Test
    fun everyAlertTypeGetsItsDefaultRuleOnceAndADeletedOneDoesNotComeBack() {
        val (first, seeded) = NotifRules.seed(emptyList(), NotifCatalog.builtIn, emptySet())
        assertEquals(NotifCatalog.builtIn.size, first.size)
        assertTrue(first.single { it.event == NotifCatalog.Ids.LM_NEEDS_REVIEW }.enabled)
        assertFalse(first.single { it.event == NotifCatalog.Ids.LM_AUTO_APPLIED }.enabled)
        val trimmed = first.filterNot { it.event == NotifCatalog.Ids.LM_ALBUM_FAILED }
        val (again, _) = NotifRules.seed(trimmed, NotifCatalog.builtIn, seeded)
        assertEquals(trimmed, again)
    }

    @Test
    fun aRuleCarriedOverFrom09CountsAsItsAlertTypesRule() {
        val old = listOf(NotifRule("limit-reached", NotifCatalog.Ids.HM_ALL_USED, enabled = true), pct("p80", 80))
        val (seeded, _) = NotifRules.seed(old, NotifCatalog.builtIn, emptySet())
        assertEquals(1, seeded.count { it.event == NotifCatalog.Ids.HM_ALL_USED })
        assertEquals(1, seeded.count { it.event == NotifCatalog.Ids.HM_PERCENT })
        assertTrue(seeded.single { it.event == NotifCatalog.Ids.HM_ALL_USED }.enabled)
    }

    @Test
    fun rulesSurviveTheStoreRoundTrip() {
        val rules = listOf(NotifRule("limit-reached", NotifCatalog.Ids.HM_ALL_USED), eighty.copy(enabled = false, users = listOf("u1")))
        assertEquals(rules, NotifRules.decode(NotifRules.encode(rules)))
    }

    @Test
    fun brokenStoredTextFallsBackToNull() {
        assertNull(NotifRules.decode(null))
        assertNull(NotifRules.decode("not json"))
    }

    @Test
    fun aServiceIsInUseOnlyWhileARuleOfItIsOnAndTheServiceIsAvailable() {
        val services = setOf(NotifService.HallMonitor)
        assertEquals(setOf(NotifService.HallMonitor), NotifRules.servicesInUse(listOf(eighty), services))
        assertTrue(NotifRules.servicesInUse(listOf(eighty.copy(enabled = false)), services).isEmpty())
        // A Label Maker rule is ignored when that service is not available to this phone (a Parent phone, or a regular account).
        val lm = NotifRule("lm", NotifCatalog.Ids.LM_NEEDS_REVIEW)
        assertTrue(NotifRules.active(listOf(lm), services).isEmpty())
        assertEquals(1, NotifRules.active(listOf(lm), services + NotifService.LabelMaker).size)
    }

    // ---- several limits (plugin 2.2) -----------------------------------------------------------------------

    private fun limit(period: String, percent: Int) =
        LimitStatus(period = period, allowedSeconds = 100.0, watchedSeconds = percent.toDouble(), remainingSeconds = (100 - percent).toDouble(), percentUsed = percent)

    private fun multi(id: String = "u1", name: String = "Alex", vararg limits: LimitStatus) =
        UserStatus(userId = id, username = name, isEnabled = true, limits = limits.toList(), apiLevel = 4)

    @Test
    fun aWeeklyLimitAtNinetyFiresWhileTheDailyLimitIsAtFifty() {
        val user = multi(limits = arrayOf(limit("Daily", 50), limit("Weekly", 90)))
        val result = PercentRules.evaluate(listOf(eighty), cards(user), armed(eighty), "2026-10-14", firstLook = false)
        assertEquals(listOf("weekly"), result.fires.map { it.period })
        assertEquals(90, result.fires.single().percent)
    }

    @Test
    fun eachLimitFiresAndRearmsOnItsOwn() {
        val both = multi(limits = arrayOf(limit("Daily", 85), limit("Weekly", 90)))
        val first = PercentRules.evaluate(listOf(eighty), cards(both), armed(eighty), "2026-10-14", firstLook = false)
        assertEquals(setOf("daily", "weekly"), first.fires.map { it.period }.toSet())

        // The daily limit resets (back to 10%) while the weekly one stays high: only the daily one rearms.
        val dailyReset = multi(limits = arrayOf(limit("Daily", 10), limit("Weekly", 92)))
        val second = PercentRules.evaluate(listOf(eighty), cards(dailyReset), first.state, "2026-10-15", firstLook = false)
        assertTrue(second.fires.isEmpty())
        assertFalse(second.state.containsKey("p80|u1|daily"))
        assertTrue(second.state.containsKey("p80|u1|weekly"))

        val dailyAgain = multi(limits = arrayOf(limit("Daily", 82), limit("Weekly", 92)))
        val third = PercentRules.evaluate(listOf(eighty), cards(dailyAgain), second.state, "2026-10-15", firstLook = false)
        assertEquals(listOf("daily"), third.fires.map { it.period })
    }

    @Test
    fun periodPercentsUsesTheReportedLimitsOrFallsBackToTheClosestOne() {
        val reported = multi(limits = arrayOf(limit("Monthly", 40), limit("Yearly", 5)))
        assertEquals(listOf("monthly" to 40, "yearly" to 5), periodPercents(reported).map { it.period to it.percent })

        // An older plugin has no list: the closest limit (here the daily one at 50%) is used.
        assertEquals(listOf("daily" to 50), periodPercents(status(remaining = 1800.0)).map { it.period to it.percent })

        // The newer plugin with no enabled limit sends an empty list and no closest limit: nothing to watch.
        val none = UserStatus(userId = "u9", isEnabled = true, limits = emptyList(), apiLevel = 4, secondsRemaining = -1.0)
        assertTrue(periodPercents(none).isEmpty())
        assertTrue(periodPercents(status(enabled = false, remaining = 100.0)).isEmpty())
    }

    // ---- a rule that was just added or switched on (baseline) -------------------------------------------------

    /** The stored state of rules that were already switched on and looked at once, with nothing fired yet. */
    private fun armed(vararg rules: NotifRule): Map<String, String> = rules.associate { PercentRules.seenKey(it) to "2026-10-13" }

    @Test
    fun aNewRuleDoesNotAlertForALimitThatIsAlreadyUsedUp() {
        // Used up hours ago, still counted until the limit resets: adding the rule must not announce it now.
        val used = status(remaining = 0.0)
        val first = PercentRules.evaluate(listOf(eighty), cards(used), emptyMap(), "2026-10-14", firstLook = false)
        assertTrue(first.fires.isEmpty())
        val again = PercentRules.evaluate(listOf(eighty), cards(used), first.state, "2026-10-14", firstLook = false)
        assertTrue(again.fires.isEmpty())

        // After the limit resets and is used again, the rule works as usual.
        val reset = PercentRules.evaluate(listOf(eighty), cards(status(remaining = 3600.0)), again.state, "2026-10-15", firstLook = false)
        val usedAgain = PercentRules.evaluate(listOf(eighty), cards(status(remaining = 600.0)), reset.state, "2026-10-15", firstLook = false)
        assertEquals(1, usedAgain.fires.size)
    }

    @Test
    fun switchingARuleOffAndOnAgainStartsFromAFreshBaseline() {
        val over = cards(status(remaining = 600.0))
        val on = PercentRules.evaluate(listOf(eighty), over, armed(eighty), "2026-10-14", firstLook = false)
        assertEquals(1, on.fires.size)

        val off = PercentRules.evaluate(listOf(eighty.copy(enabled = false)), over, on.state, "2026-10-14", firstLook = false)
        assertFalse(off.state.containsKey(PercentRules.seenKey(eighty)))

        val onAgain = PercentRules.evaluate(listOf(eighty), over, off.state, "2026-10-14", firstLook = false)
        assertTrue(onAgain.fires.isEmpty())
    }

    @Test
    fun aRuleAddedLaterGetsItsOwnBaselineWhileTheOthersKeepWorking() {
        val fifty = pct("p50", 50)
        val user = cards(status(remaining = 540.0)) // 85% used
        val result = PercentRules.evaluate(listOf(fifty, eighty), user, armed(fifty), "2026-10-14", firstLook = false)
        assertEquals(listOf("p50"), result.fires.map { it.rule.id })
    }
}
