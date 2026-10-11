package com.feerlessleadr.embidextrous

import com.feerlessleadr.embidextrous.data.ApiClient
import com.feerlessleadr.embidextrous.data.ApiException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ApiClientTest {
    private lateinit var server: MockWebServer

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After fun tearDown() = server.shutdown()

    private fun client(token: String? = "tok") = ApiClient(server.url("/").toString(), token, appVersion = "1.2.3", deviceId = "dev-1", deviceName = "Pixel")

    @Test fun `every plugin call sends the token in the header and never in the url`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"Name":"SmartLists","PluginVersion":"0.1.9.0","ApiVersion":1}"""))
        val info = client().info()

        val request = server.takeRequest()
        assertEquals("/emby/Plugins/SmartLists/info", request.path)
        assertEquals("tok", request.getHeader("X-Emby-Token"))
        assertFalse(request.path!!.contains("tok"))
        assertEquals(1, info.apiVersion)
        assertEquals("0.1.9.0", info.pluginVersion)
    }

    @Test fun `sign in sends the Emby authorization header and parses the token and admin flag`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"AccessToken":"abc123","User":{"Id":"u1","Name":"kevin","Policy":{"IsAdministrator":true}}}"""))
        val result = client(token = null).signIn("kevin", "secret")

        val request = server.takeRequest()
        assertEquals("/emby/Users/AuthenticateByName", request.path)
        assertNull(request.getHeader("X-Emby-Token"))
        val auth = request.getHeader("X-Emby-Authorization")!!
        assertTrue(auth.startsWith("MediaBrowser "))
        assertTrue(auth.contains("Client=\"Embidextrous\""))
        assertTrue(auth.contains("DeviceId=\"dev-1\""))
        assertTrue(auth.contains("Version=\"1.2.3\""))
        assertTrue(request.body.readUtf8().contains("\"Pw\":\"secret\""))
        assertEquals("abc123", result.token)
        assertEquals("u1", result.userId)
        assertTrue(result.isAdministrator)
    }

    @Test fun `a non administrator is reported as such`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"AccessToken":"t","User":{"Id":"u2","Name":"test1","Policy":{"IsAdministrator":false}}}"""))
        assertFalse(client(token = null).signIn("test1", "pw").isAdministrator)
    }

    @Test fun `plugin errors show the detail message`() {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"title":"Validation Error","status":400,"detail":"List name cannot be empty"}"""))
        val e = assertThrows(ApiException::class.java) { runBlocking { client().list("x") } }
        assertEquals(400, e.status)
        assertEquals("List name cannot be empty", e.message)
    }

    @Test fun `401 is flagged so the app can sign out`() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("Access token is invalid or expired."))
        val e = assertThrows(ApiException::class.java) { runBlocking { client().lists() } }
        assertTrue(e.isUnauthorized)
    }

    @Test fun `403 says the account is not an administrator`() {
        server.enqueue(MockResponse().setResponseCode(403))
        val e = assertThrows(ApiException::class.java) { runBlocking { client().lists() } }
        assertTrue(e.message!!.contains("administrator"))
    }

    @Test fun `404 with no body points at the plugin`() {
        server.enqueue(MockResponse().setResponseCode(404))
        val e = assertThrows(ApiException::class.java) { runBlocking { client().info() } }
        assertTrue(e.message!!.contains("plugin"))
    }

    @Test fun `an unreachable server is reported with status 0`() {
        val dead = ApiClient("http://127.0.0.1:1", "tok")
        val e = assertThrows(ApiException::class.java) { runBlocking { dead.lists() } }
        assertEquals(0, e.status)
        assertTrue(e.message!!.startsWith("Could not reach the server"))
    }

    @Test fun `a signed out client does not call the server`() {
        val e = assertThrows(ApiException::class.java) { runBlocking { client(token = null).lists() } }
        assertTrue(e.isUnauthorized)
        assertEquals(0, server.requestCount)
    }

    @Test fun `create, update, delete and refresh use the documented verbs and paths`() = runBlocking {
        val body = buildJsonObject { put("Name", "Next Up") }
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"Id":"11111111-1111-1111-1111-111111111111","Name":"Next Up"}"""))
        server.enqueue(MockResponse().setBody("""{"Id":"11111111-1111-1111-1111-111111111111","Name":"Next Up"}"""))
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(MockResponse().setBody(""))
        server.enqueue(MockResponse().setBody(""))
        server.enqueue(MockResponse().setBody(""))

        val api = client()
        val created = api.create(body)
        api.update(created.id, body)
        api.delete(created.id, deleteEmbyList = false)
        api.refresh(created.id)
        api.disable(created.id)
        api.enable(created.id)

        val sent = (1..6).map { server.takeRequest() }.map { "${it.method} ${it.path}" }
        assertEquals(
            listOf(
                "POST /emby/Plugins/SmartLists/?skipRefresh=false",
                "PUT /emby/Plugins/SmartLists/11111111-1111-1111-1111-111111111111?skipRefresh=false",
                "DELETE /emby/Plugins/SmartLists/11111111-1111-1111-1111-111111111111?deleteEmbyList=false",
                "POST /emby/Plugins/SmartLists/11111111-1111-1111-1111-111111111111/refresh",
                "POST /emby/Plugins/SmartLists/11111111-1111-1111-1111-111111111111/disable",
                "POST /emby/Plugins/SmartLists/11111111-1111-1111-1111-111111111111/enable",
            ),
            sent,
        )
    }

    @Test fun `an image upload is a multipart form with the type and the file, token in the header`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"ImageType":"Primary","FileName":"primary.png","ContentType":"image/png","FileSize":4}"""))
        val info = client().uploadImage("11111111-1111-1111-1111-111111111111", "Primary", "image.png", "image/png", byteArrayOf(1, 2, 3, 4))

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/emby/Plugins/SmartLists/11111111-1111-1111-1111-111111111111/images", request.path)
        assertEquals("tok", request.getHeader("X-Emby-Token"))
        assertTrue(request.getHeader("Content-Type")!!.startsWith("multipart/form-data"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("name=\"imageType\""))
        assertTrue(body.contains("Primary"))
        assertTrue(body.contains("name=\"file\"; filename=\"image.png\""))
        assertEquals("Primary", info.type)
        assertEquals(4L, info.size)
    }

    @Test fun `images are listed, fetched with the token and deleted`() = runBlocking {
        server.enqueue(MockResponse().setBody("""[{"ImageType":"Primary","FileName":"primary.jpg","ContentType":"image/jpeg","FileSize":10},{"ImageType":"Backdrop","FileName":"backdrop.png","ContentType":"image/png","FileSize":20}]"""))
        server.enqueue(MockResponse().setBody("PIXELS"))
        server.enqueue(MockResponse().setResponseCode(204))

        val api = client()
        val images = api.images("abc")
        assertEquals(listOf("Primary", "Backdrop"), images.map { it.type })
        assertEquals("PIXELS", String(api.imageBytes("abc", "Primary")))
        api.deleteImage("abc", "Backdrop")

        val paths = (1..3).map { server.takeRequest() }.map { "${it.method} ${it.path} token=${it.getHeader("X-Emby-Token")}" }
        assertEquals(
            listOf(
                "GET /emby/Plugins/SmartLists/abc/images token=tok",
                "GET /emby/Plugins/SmartLists/abc/images/Primary/file token=tok",
                "DELETE /emby/Plugins/SmartLists/abc/images/Backdrop token=tok",
            ),
            paths,
        )
    }

    @Test fun `similarity fields come from the older fields endpoint`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"SimilarityComparisonFields":[{"Value":"Genre","Label":"Genre"},{"Value":"Tags","Label":"Tags"}],"Other":[]}"""))
        assertEquals(listOf("Genre", "Tags"), client().similarityFields().map { it.value })
    }
}