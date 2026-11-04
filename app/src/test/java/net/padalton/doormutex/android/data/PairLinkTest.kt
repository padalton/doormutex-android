package net.padalton.doormutex.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PairLinkTest {
    @Test
    fun `link from the web UI`() {
        assertEquals(
            PairLink("https://doormutex.home.example", "dm_abc-DEF_1", "couch"),
            PairLink.parse("doormutex://pair?hub=https%3A%2F%2Fdoormutex.home.example&token=dm_abc-DEF_1&name=couch"),
        )
    }

    @Test
    fun `name is optional, order does not matter, scheme is case-insensitive`() {
        assertEquals(
            PairLink("http://192.168.1.20:8080", "dm_x", null),
            PairLink.parse(" DoorMutex://pair?token=dm_x&hub=http%3A%2F%2F192.168.1.20%3A8080 "),
        )
    }

    @Test
    fun `an encoded ampersand stays in the value`() {
        assertEquals("a&b", PairLink.parse("doormutex://pair?hub=h&token=t&name=a%26b")?.name)
    }

    @Test
    fun `anything else is not a pairing link`() {
        listOf(
            "",
            "https://doormutex.home.example",
            "doormutex://other?hub=h&token=t",
            "doormutex://pair?hub=h",
            "doormutex://pair?token=t",
            "doormutex://pair?hub=%zz&token=t",
            "not a uri at all",
        ).forEach { assertNull(it, PairLink.parse(it)) }
    }

    @Test
    fun `toString hides the token`() {
        assertEquals("PairLink(hub=h, token=***, name=null)", PairLink("h", "dm_secret", null).toString())
    }
}
