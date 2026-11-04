package net.padalton.doormutex.android.data

import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime

class ModelTest {
    private fun resource(name: String) = javaClass.getResource("/api/$name")!!.readText()

    @Test
    fun `parses the State example from openapi yaml`() {
        val state = HubJson.decodeFromString(State.serializer(), resource("state-example.json"))
        assertEquals(Status.DND, state.status)
        assertEquals(Phase.ENDING_SOON, state.phase)
        assertEquals(Light.RED_YELLOW, state.light)
        assertEquals(Reason.CALENDAR, state.reason)
        assertEquals("video_call", state.detail)
        assertEquals(OffsetDateTime.parse("2026-10-28T14:30:00+01:00"), state.until)
        assertEquals(Status.WORKING, state.next?.status)
        assertEquals(Reason.WORK_HOURS, state.next?.reason)
        assertEquals(OffsetDateTime.parse("2026-10-28T17:00:00+01:00"), state.nextFree?.start)
        assertNull(state.message)
    }

    @Test
    fun `parses profiles with the example state`() {
        val json = """[{"id":"office","name":"Office","mode":"office","state":${resource("state-example.json")}}]"""
        val profiles = HubJson.decodeFromString(ListSerializer(ProfileInfo.serializer()), json)
        assertEquals("office", profiles.single().id)
        assertEquals(Light.RED_YELLOW, profiles.single().state.light)
    }

    @Test
    fun `parses me`() {
        val me = HubJson.decodeFromString(Me.serializer(), resource("me-read.json"))
        assertEquals(Role.READ, me.role)
        assertEquals(listOf("office", "teen"), me.profiles)
    }

    @Test
    fun `unknown enum values and fields do not break parsing`() {
        val json =
            """
            {"status":"busy","phase":"new_phase","light":"purple","reason":"telepathy",
             "since":"2026-10-28T14:00:00Z","until":null,"next":null,"next_free":null,
             "updated":"2026-10-28T14:00:00Z","something_new":{"a":1}}
            """.trimIndent()
        val state = HubJson.decodeFromString(State.serializer(), json)
        assertEquals(Status.UNKNOWN, state.status)
        assertEquals(Phase.UNKNOWN, state.phase)
        assertEquals(Light.UNKNOWN, state.light)
        assertEquals(Reason.UNKNOWN, state.reason)
        assertNull(state.until)
    }

    @Test
    fun `snapshot survives a round trip through the cache`() {
        val state = HubJson.decodeFromString(State.serializer(), resource("state-example.json"))
        val snapshot = Snapshot(listOf(ProfileInfo("office", "Office", "office", state)), fetchedAt = 1, problem = Problem.UNREACHABLE)
        val back = HubJson.decodeFromString(Snapshot.serializer(), HubJson.encodeToString(Snapshot.serializer(), snapshot))
        assertEquals(snapshot, back)
    }

    @Test
    fun `every lamp pattern of the API is known`() {
        val wire = listOf("off", "red", "yellow", "green", "green_blink", "red_yellow", "red_yellow_alternate", "yellow_blink")
        wire.forEach { w -> assertTrue(w, Light.entries.any { it.wire == w }) }
    }
}
