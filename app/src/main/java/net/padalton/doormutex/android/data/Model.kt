package net.padalton.doormutex.android.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import java.time.OffsetDateTime

// Types from the hub API, see api/openapi.yaml in padalton/doormutex.
// Enums decode unknown values to UNKNOWN instead of failing: a newer hub
// may add a reason or lamp pattern, and the app must keep showing the rest.

val HubJson =
    Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

@Serializable(with = StatusSerializer::class)
enum class Status(
    val wire: String,
) {
    FREE("free"),
    WORKING("working"),
    DND("dnd"),
    AWAY("away"),
    UNKNOWN(""),
}

@Serializable(with = PhaseSerializer::class)
enum class Phase(
    val wire: String,
) {
    NORMAL("normal"),
    LAST_CALL("last_call"),
    AMBER("amber"),
    ENDING_SOON("ending_soon"),
    FAULT("fault"),
    UNKNOWN(""),
}

@Serializable(with = LightSerializer::class)
enum class Light(
    val wire: String,
    val red: Boolean = false,
    val yellow: Boolean = false,
    val green: Boolean = false,
    val blinks: Boolean = false,
) {
    OFF("off"),
    RED("red", red = true),
    YELLOW("yellow", yellow = true),
    GREEN("green", green = true),
    GREEN_BLINK("green_blink", green = true, blinks = true),
    RED_YELLOW("red_yellow", red = true, yellow = true),
    RED_YELLOW_ALTERNATE("red_yellow_alternate", red = true, yellow = true, blinks = true),
    YELLOW_BLINK("yellow_blink", yellow = true, blinks = true),
    UNKNOWN(""),
}

@Serializable(with = ReasonSerializer::class)
enum class Reason(
    val wire: String,
) {
    OVERRIDE("override"),
    POMODORO("pomodoro"),
    POMODORO_BREAK("pomodoro_break"),
    MICROPHONE("microphone"),
    CAMERA("camera"),
    SCREEN_LOCKED("screen_locked"),
    PC_OFF("pc_off"),
    COMPANION_LOST("companion_lost"),
    COMPANION_OFFLINE("companion_offline"),
    CALENDAR("calendar"),
    WORK_HOURS("work_hours"),
    SIGNAL("signal"),
    DEFAULT("default"),
    UNKNOWN(""),
}

@Serializable(with = RoleSerializer::class)
enum class Role(
    val wire: String,
) {
    FULL("full"),
    READ("read"),
    UNKNOWN(""),
}

@Serializable
data class State(
    val status: Status,
    val phase: Phase = Phase.NORMAL,
    val light: Light,
    val reason: Reason = Reason.UNKNOWN,
    val detail: String? = null,
    @Serializable(with = DateTimeSerializer::class) val since: OffsetDateTime,
    @Serializable(with = DateTimeSerializer::class) val until: OffsetDateTime? = null,
    val next: Slot? = null,
    @SerialName("next_free") val nextFree: FreeSlot? = null,
    val message: String? = null,
    @Serializable(with = DateTimeSerializer::class) val updated: OffsetDateTime,
)

@Serializable
data class Slot(
    val status: Status,
    val reason: Reason = Reason.UNKNOWN,
    @Serializable(with = DateTimeSerializer::class) val since: OffsetDateTime,
    @Serializable(with = DateTimeSerializer::class) val until: OffsetDateTime? = null,
)

@Serializable
data class FreeSlot(
    @Serializable(with = DateTimeSerializer::class) val start: OffsetDateTime,
    @Serializable(with = DateTimeSerializer::class) val end: OffsetDateTime? = null,
)

@Serializable
data class ProfileInfo(
    val id: String,
    val name: String,
    val mode: String = "office",
    val state: State,
)

/** `GET /api/v1/timeline`. */
@Serializable
data class Timeline(
    @Serializable(with = DateTimeSerializer::class) val from: OffsetDateTime,
    @Serializable(with = DateTimeSerializer::class) val to: OffsetDateTime,
    val slots: List<Slot> = emptyList(),
)

/** Body of `PUT /api/v1/override`; [status] and [mode] as on the wire. */
@Serializable
data class OverrideRequest(
    val status: String,
    val mode: String,
    val duration: String? = null,
)

/** One event of `GET /api/v1/events?profile=*`: a profile's new state. */
@Serializable
data class ProfileState(
    val profile: String,
    val state: State,
)

/** `GET /api/v1/me`, hub API 0.2.0 and later. */
@Serializable
data class Me(
    val name: String,
    val role: Role,
    val profiles: List<String> = emptyList(),
    val admin: Boolean = false,
)

@Serializable
data class HubErrorBody(
    val error: String,
)

object DateTimeSerializer : KSerializer<OffsetDateTime> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("doormutex.DateTime", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: OffsetDateTime,
    ) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): OffsetDateTime = OffsetDateTime.parse(decoder.decodeString())
}

/** Maps a string enum by its wire value; unknown values become [fallback]. */
open class WireEnumSerializer<T : Enum<T>>(
    name: String,
    private val entries: List<T>,
    private val fallback: T,
    private val wire: (T) -> String,
) : KSerializer<T> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("doormutex.$name", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: T,
    ) = encoder.encodeString(wire(value))

    override fun deserialize(decoder: Decoder): T {
        val s = decoder.decodeString()
        return entries.firstOrNull { wire(it) == s } ?: fallback
    }
}

object StatusSerializer : WireEnumSerializer<Status>("Status", Status.entries, Status.UNKNOWN, { it.wire })

object PhaseSerializer : WireEnumSerializer<Phase>("Phase", Phase.entries, Phase.UNKNOWN, { it.wire })

object LightSerializer : WireEnumSerializer<Light>("Light", Light.entries, Light.UNKNOWN, { it.wire })

object ReasonSerializer : WireEnumSerializer<Reason>("Reason", Reason.entries, Reason.UNKNOWN, { it.wire })

object RoleSerializer : WireEnumSerializer<Role>("Role", Role.entries, Role.UNKNOWN, { it.wire })
