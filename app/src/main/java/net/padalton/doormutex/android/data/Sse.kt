package net.padalton.doormutex.android.data

/** What the hub's event stream delivers. */
sealed interface StreamEvent {
    /** `event: state` with a [ProfileState]. */
    data class Update(
        val update: ProfileState,
    ) : StreamEvent

    /** The hub's keep-alive comment every 25 s: the connection and the data are still current. */
    data object KeepAlive : StreamEvent
}

/**
 * Server-Sent Events, line by line (WHATWG "event stream interpretation").
 * Own parser instead of okhttp-sse, because the keep-alive comments matter:
 * they prove the shown state is current although nothing changed.
 */
class SseParser {
    private var event = ""
    private val data = StringBuilder()
    private var hasData = false

    /** Feeds one line without its terminator; returns an event when one is complete. */
    fun line(line: String): Raw? {
        if (line.isEmpty()) return dispatch()
        if (line.startsWith(":")) return Raw.Comment
        val colon = line.indexOf(':')
        val field = if (colon < 0) line else line.substring(0, colon)
        val value =
            if (colon < 0) {
                ""
            } else {
                line.substring(colon + 1).removePrefix(" ")
            }
        when (field) {
            "event" -> {
                event = value
            }

            "data" -> {
                if (hasData) data.append('\n')
                data.append(value)
                hasData = true
            }
            // id and retry are not used: the hub sends the full state on every connect.
        }
        return null
    }

    private fun dispatch(): Raw? {
        val out = if (hasData) Raw.Event(event.ifEmpty { "message" }, data.toString()) else null
        event = ""
        data.clear()
        hasData = false
        return out
    }

    sealed interface Raw {
        data class Event(
            val type: String,
            val data: String,
        ) : Raw

        data object Comment : Raw
    }
}
