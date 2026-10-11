package com.feerlessleadr.embidextrous

import com.feerlessleadr.embidextrous.data.ApiClient
import com.feerlessleadr.embidextrous.data.ApiException
import com.feerlessleadr.embidextrous.data.ListDraft
import com.feerlessleadr.embidextrous.data.Safety
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

// The fixes from the security review: redirects, ids in URLs, logout, size limits.
class SecurityTest {
    private lateinit var server: MockWebServer
    private lateinit var elsewhere: MockWebServer

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        elsewhere = MockWebServer().apply { start() }
    }

    @After fun tearDown() {
        server.shutdown()
        elsewhere.shutdown()
    }

    private fun client(token: String? = "secret-token") = ApiClient(server.url("/").toString(), token)

    // ----- redirects -----

    @Test fun `a redirect is an error and the token is never sent to the other host`() {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", elsewhere.url("/steal").toString()))
        val e = assertThrows(ApiException::class.java) { runBlocking { client().lists() } }
        assertEquals(302, e.status)
        assertTrue(e.message!!.contains("redirect"))
        assertEquals("the other host must not be contacted", 0, elsewhere.requestCount)
    }

    @Test fun `a 307 on sign in does not forward the password`() {
        server.enqueue(MockResponse().setResponseCode(307).setHeader("Location", elsewhere.url("/collect").toString()))
        assertThrows(ApiException::class.java) { runBlocking { client(token = null).signIn("kevin", "hunter2") } }
        assertEquals(0, elsewhere.requestCount)
    }

    @Test fun `image and cover and backup downloads do not follow redirects either`() {
        repeat(3) { server.enqueue(MockResponse().setResponseCode(301).setHeader("Location", elsewhere.url("/x").toString())) }
        assertThrows(ApiException::class.java) { runBlocking { client().imageBytes("p1", "Primary") } }
        assertThrows(ApiException::class.java) { runBlocking { client().embyCover("123") } }
        assertThrows(ApiException::class.java) { runBlocking { client().downloadBackupTo("smartlists_backup_1.zip", ByteArrayOutputStream()) } }
        assertEquals(0, elsewhere.requestCount)
    }

    // ----- ids from the server -----

    @Test fun `an id that could change the path is refused before any request is made`() {
        for (bad in listOf("x/../../Users", "..", ".", "a?b=c", "a#b", "a b", "", "id/images")) {
            assertThrows("id '$bad'", ApiException::class.java) { runBlocking { client().list(bad) } }
            assertThrows("id '$bad'", ApiException::class.java) { runBlocking { client().refresh(bad) } }
        }
        assertThrows(ApiException::class.java) { runBlocking { client().embyCover("1/../2") } }
        assertThrows(ApiException::class.java) { runBlocking { client().deleteImage("p1", "../x") } }
        assertThrows(ApiException::class.java) { runBlocking { client().restoreBackup("../../x.zip", false) } }
        assertThrows(ApiException::class.java) { runBlocking { client().deleteBackup("..") } }
        assertEquals(0, server.requestCount)
    }

    @Test fun `ordinary ids still work`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"Id":"187e8098-a040-4bf1-a422-00bcf6f9d29f","Name":"x"}"""))
        client().list("187e8098-a040-4bf1-a422-00bcf6f9d29f")
        assertEquals("/emby/Plugins/SmartLists/187e8098-a040-4bf1-a422-00bcf6f9d29f", server.takeRequest().path)

        server.enqueue(MockResponse().setResponseCode(404))
        client().embyCover("336")
        assertEquals("/emby/Items/336/Images/Primary?maxWidth=200&quality=80", server.takeRequest().path)

        server.enqueue(MockResponse().setBody("""{"restored":1,"overwritten":0,"skipped":0,"errors":0}"""))
        client().restoreBackup("smartlists_backup_20261010_014417.zip", false)
        assertEquals("/emby/Plugins/SmartLists/backups/smartlists_backup_20261010_014417.zip/restore?overwrite=false", server.takeRequest().path)
    }

    // ----- logout -----

    @Test fun `logout asks the server to end the session with the token in the header`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        client().logout()
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/emby/Sessions/Logout", request.path)
        assertEquals("secret-token", request.getHeader("X-Emby-Token"))
    }

    // ----- sizes -----

    @Test fun `a cover bigger than the limit is refused`() {
        server.enqueue(MockResponse().setBody(okio.Buffer().write(ByteArray((Safety.MAX_COVER_BYTES + 1024).toInt()))))
        assertThrows(ApiException::class.java) { runBlocking { client().embyCover("1") } }
    }

    @Test fun `a cover within the limit is returned`() = runBlocking {
        server.enqueue(MockResponse().setBody(okio.Buffer().write(ByteArray(2048))))
        assertEquals(2048, client().embyCover("1")!!.size)
    }

    @Test fun `a backup is copied straight into the output`() = runBlocking {
        val data = ByteArray(300_000) { (it % 251).toByte() }
        server.enqueue(MockResponse().setBody(okio.Buffer().write(data)))
        val out = ByteArrayOutputStream()
        client().downloadBackupTo("smartlists_backup_1.zip", out)
        assertTrue(data.contentEquals(out.toByteArray()))
        assertEquals("secret-token", server.takeRequest().getHeader("X-Emby-Token"))
    }

    @Test fun `JSON nested too deeply is recognised, brackets in strings are not`() {
        assertTrue(Safety.jsonTooDeep("[".repeat(200)))
        assertTrue(Safety.jsonTooDeep("""{"a":""" .repeat(100) + "1" + "}".repeat(100)))
        assertFalse(Safety.jsonTooDeep("""{"Name":"[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[","x":["\"[[[["]}"""))
        assertFalse(Safety.jsonTooDeep("""{"ExpressionSets":[{"Expressions":[{"MemberName":"Name"}]}]}"""))
    }

    @Test fun `an import over the limit is refused without being read in full`() {
        assertNull(Safety.readImport(ByteArrayInputStream(ByteArray(Safety.MAX_IMPORT_BYTES + 1))))
        assertEquals(1000, Safety.readImport(ByteArrayInputStream(ByteArray(1000)))!!.size)
    }

    @Test fun `hostile shared text is refused`() {
        assertNull(ListDraft.fromShared("[".repeat(100_000), "me"))
        assertNull(ListDraft.fromShared("{\"Name\":\"" + "a".repeat(Safety.MAX_IMPORT_BYTES) + "\",\"MediaTypes\":[]}", "me"))
        assertNotNull(ListDraft.fromShared("""{"Name":"ok","MediaTypes":["Movie"]}""", "me"))
    }

    @Test fun `pictures are shrunk to fit`() {
        assertEquals(1, Safety.sampleSize(300, 200, 400))
        assertEquals(2, Safety.sampleSize(800, 600, 400))
        assertEquals(16, Safety.sampleSize(6400, 4800, 400))
    }
}
