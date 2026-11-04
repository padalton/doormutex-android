package net.padalton.doormutex.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HubUrlTest {
    @Test
    fun `https is accepted and normalised`() {
        assertEquals(HubUrl.Ok("https://hub.example", cleartext = false), HubUrl.parse(" https://hub.example/ "))
        assertEquals(HubUrl.Ok("https://hub.example:8443/dm", cleartext = false), HubUrl.parse("https://hub.example:8443/dm//"))
    }

    @Test
    fun `missing scheme means https`() {
        assertEquals(HubUrl.Ok("https://hub.example", cleartext = false), HubUrl.parse("hub.example"))
    }

    @Test
    fun `http only for private addresses`() {
        listOf(
            "http://192.168.1.20:8080",
            "http://10.0.0.5",
            "http://172.16.0.1",
            "http://172.31.255.1",
            "http://doormutex.local",
            "http://127.0.0.1:8080",
            "http://localhost:8080",
        ).forEach { assertEquals(it, HubUrl.Ok(it, cleartext = true), HubUrl.parse(it)) }
        listOf("http://hub.example", "http://172.32.0.1", "http://8.8.8.8", "http://192.169.0.1", "http://local")
            .forEach { assertEquals(it, HubUrl.CleartextNotPrivate, HubUrl.parse(it)) }
    }

    @Test
    fun `garbage is invalid`() {
        listOf("", "   ", "ftp://hub", "https://", "https://hub.example/?x=1", "https://user:pw@hub.example", "https://hub example")
            .forEach { assertEquals(it, HubUrl.Invalid, HubUrl.parse(it)) }
    }

    @Test
    fun `private ranges`() {
        assertTrue(HubUrl.isPrivate("192.168.0.1"))
        assertFalse(HubUrl.isPrivate("192.168.0.256"))
        assertFalse(HubUrl.isPrivate("10.0.0"))
        assertFalse(HubUrl.isPrivate("example.com"))
    }
}
