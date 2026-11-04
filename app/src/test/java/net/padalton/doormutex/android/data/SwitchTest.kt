package net.padalton.doormutex.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SwitchTest {
    @Test
    fun `office mode ends a red or green override with the next automatic change`() {
        assertEquals(OverrideRequest("dnd", "next_change"), Switch.RED.request("office"))
        assertEquals(OverrideRequest("free", "next_change"), Switch.GREEN.request("office"))
    }

    @Test
    fun `room mode keeps it until switched again`() {
        assertEquals(OverrideRequest("dnd", "indefinite"), Switch.RED.request("room"))
        assertEquals(OverrideRequest("free", "indefinite"), Switch.GREEN.request("room"))
    }

    @Test
    fun `fixed requests`() {
        assertEquals(OverrideRequest("dnd", "duration", "1h"), Switch.RED_HOUR.request("office"))
        assertEquals(OverrideRequest("away", "indefinite"), Switch.OFF.request("office"))
        assertNull(Switch.AUTOMATIC.request("office"))
    }

    @Test
    fun `no null fields on the wire, the hub rejects unknown ones`() {
        assertEquals(
            """{"status":"dnd","mode":"next_change"}""",
            HubJson.encodeToString(OverrideRequest.serializer(), Switch.RED.request("office")!!),
        )
    }
}
