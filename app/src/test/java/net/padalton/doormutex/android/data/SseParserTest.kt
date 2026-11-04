package net.padalton.doormutex.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SseParserTest {
    private fun parse(text: String): List<SseParser.Raw> {
        val parser = SseParser()
        return text.split("\n").mapNotNull(parser::line)
    }

    @Test
    fun `event with type and data`() {
        assertEquals(
            listOf(SseParser.Raw.Event("state", """{"a":1}""")),
            parse("event: state\ndata: {\"a\":1}\n\n"),
        )
    }

    @Test
    fun `comments are keep-alives`() {
        assertEquals(listOf(SseParser.Raw.Comment), parse(": keep-alive\n\n"))
    }

    @Test
    fun `multi-line data is joined and the type resets`() {
        assertEquals(
            listOf(SseParser.Raw.Event("state", "a\nb"), SseParser.Raw.Event("message", "c")),
            parse("event: state\ndata: a\ndata: b\n\ndata:c\n\n"),
        )
    }

    @Test
    fun `no event without data or before the blank line`() {
        val parser = SseParser()
        assertNull(parser.line("event: state"))
        assertNull(parser.line(""))
        assertNull(parser.line("data: x"))
        assertNull(parser.line("id: 7"))
        assertEquals(SseParser.Raw.Event("message", "x"), parser.line(""))
    }
}
