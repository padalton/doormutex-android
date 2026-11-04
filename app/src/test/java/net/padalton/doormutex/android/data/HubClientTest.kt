package net.padalton.doormutex.android.data

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HubClientTest {
    private val server = MockWebServer()
    private val client = HubClient()
    private lateinit var hub: HubConfig

    private fun resource(name: String) = javaClass.getResource("/api/$name")!!.readText()

    @Before
    fun start() {
        server.start()
        hub = HubConfig(server.url("/").toString().trimEnd('/'), "dm_secret")
    }

    @After
    fun stop() {
        server.close()
    }

    private fun respond(
        code: Int,
        body: String,
    ) = server.enqueue(
        MockResponse
            .Builder()
            .code(code)
            .body(body)
            .build(),
    )

    @Test
    fun `profiles sends the bearer token and parses the list`() =
        runTest {
            respond(200, """[{"id":"office","name":"Office","mode":"office","state":${resource("state-example.json")}}]""")
            val profiles = client.profiles(hub)
            assertEquals("Office", profiles.single().name)
            val request = server.takeRequest()
            assertEquals("/api/v1/profiles", request.url.encodedPath)
            assertEquals("Bearer dm_secret", request.headers["Authorization"])
        }

    @Test
    fun `401 means the token was rejected`() =
        runTest {
            respond(401, """{"error":"invalid token"}""")
            val e = runCatching { client.profiles(hub) }.exceptionOrNull()
            assertTrue(e.toString(), e is HubException.Unauthorized)
        }

    @Test
    fun `403 carries the hub's reason`() =
        runTest {
            respond(403, """{"error":"read-only token"}""")
            val e = runCatching { client.profiles(hub) }.exceptionOrNull()
            assertEquals("read-only token", (e as HubException.Forbidden).reason)
        }

    @Test
    fun `me is null on a hub without the route`() =
        runTest {
            respond(404, """{"error":"not found"}""")
            assertNull(client.me(hub))
        }

    @Test
    fun `me parses role`() =
        runTest {
            respond(200, resource("me-read.json"))
            assertEquals(Role.READ, client.me(hub)?.role)
        }

    @Test
    fun `garbage is a bad response, not a crash`() =
        runTest {
            respond(200, "<html>proxy login</html>")
            val e = runCatching { client.profiles(hub) }.exceptionOrNull()
            assertTrue(e.toString(), e is HubException.BadResponse)
        }

    @Test
    fun `no hub is unreachable`() =
        runTest {
            val gone = hub.copy(url = "http://127.0.0.1:1")
            val e = runCatching { client.profiles(gone) }.exceptionOrNull()
            assertTrue(e.toString(), e is HubException.Unreachable)
        }

    @Test
    fun `the token never shows up in toString`() {
        assertTrue("dm_secret" !in hub.toString())
    }

    @Test
    fun `events parse states and keep-alives until the hub ends the stream`() =
        runTest {
            val state = resource("state-example.json").replace("\n", "")
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(200)
                    .setHeader("Content-Type", "text/event-stream")
                    .body("event: state\ndata: {\"profile\":\"office\",\"state\":$state}\n\n: keep-alive\n\n")
                    .build(),
            )
            val events = client.events(hub).toList()
            assertEquals(2, events.size)
            assertEquals("office", (events[0] as StreamEvent.Update).update.profile)
            assertEquals(StreamEvent.KeepAlive, events[1])
            val request = server.takeRequest()
            assertEquals("/api/v1/events?profile=*", request.url.encodedPath + "?" + request.url.encodedQuery)
            assertEquals("Bearer dm_secret", request.headers["Authorization"])
        }

    @Test
    fun `events on a hub without the multi-profile stream fail with 404`() =
        runTest {
            respond(404, """{"error":"unknown profile *"}""")
            val e = runCatching { client.events(hub).toList() }.exceptionOrNull()
            assertTrue(e.toString(), e is HubException.Http && e.code == 404)
        }

    @Test
    fun `events with a revoked token fail with Unauthorized`() =
        runTest {
            respond(401, """{"error":"invalid token"}""")
            val e = runCatching { client.events(hub).toList() }.exceptionOrNull()
            assertTrue(e.toString(), e is HubException.Unauthorized)
        }
}
