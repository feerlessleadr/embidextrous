package com.feerlessleadr.embidextrous

import com.feerlessleadr.embidextrous.data.UrlRules
import com.feerlessleadr.embidextrous.data.UrlRules.Result
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlRulesTest {
    private fun ok(input: String, cleartext: Boolean = false) =
        (UrlRules.normalize(input, cleartext) as Result.Ok).serverUrl

    private fun rejected(input: String, cleartext: Boolean = false) =
        (UrlRules.normalize(input, cleartext) as Result.Rejected).reason

    @Test fun `adds https when no scheme is typed`() = assertEquals("https://emby.example.com", ok("emby.example.com"))

    @Test fun `keeps https, port and a sub path, and drops the trailing slash`() =
        assertEquals("https://emby.example.com:8920/media", ok("  https://emby.example.com:8920/media/ "))

    @Test fun `http is refused in release builds`() = assertTrue(rejected("http://192.168.1.5:8096").contains("HTTPS"))

    @Test fun `http is allowed in debug builds only`() = assertEquals("http://10.0.2.2:8096", ok("http://10.0.2.2:8096", cleartext = true))

    @Test fun `host name is kept as typed`() = assertEquals("https://Emby.Example.com", ok("https://Emby.Example.com"))

    @Test fun `empty input is rejected`() = assertTrue(rejected("   ").isNotBlank())

    @Test fun `other schemes are rejected`() = assertTrue(rejected("ftp://emby.example.com").isNotBlank())

    @Test fun `credentials in the address are rejected`() = assertTrue(rejected("https://user:pw@emby.example.com").contains("user name"))

    @Test fun `garbage is rejected`() = assertTrue(rejected("https://").isNotBlank())
}
