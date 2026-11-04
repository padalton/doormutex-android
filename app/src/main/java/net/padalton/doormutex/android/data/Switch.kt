package net.padalton.doormutex.android.data

/**
 * The override buttons of the detail screen (full tokens only), with the
 * same requests as the shortcuts in docs/android.md of the main repository.
 */
enum class Switch {
    RED,
    RED_HOUR,
    GREEN,
    OFF,
    AUTOMATIC,
    ;

    /**
     * The request body; null for [AUTOMATIC] (a DELETE). In room mode there is
     * no companion whose changes would end a `next_change` override, so the
     * light stays until it is switched again.
     */
    fun request(profileMode: String): OverrideRequest? {
        val untilChange = if (profileMode == "room") "indefinite" else "next_change"
        return when (this) {
            RED -> OverrideRequest(Status.DND.wire, untilChange)
            RED_HOUR -> OverrideRequest(Status.DND.wire, "duration", "1h")
            GREEN -> OverrideRequest(Status.FREE.wire, untilChange)
            OFF -> OverrideRequest(Status.AWAY.wire, "indefinite")
            AUTOMATIC -> null
        }
    }
}
