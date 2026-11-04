package net.padalton.doormutex.android.ui

import android.content.Context
import android.text.format.DateFormat
import net.padalton.doormutex.android.R
import net.padalton.doormutex.android.data.Phase
import net.padalton.doormutex.android.data.ProfileInfo
import net.padalton.doormutex.android.data.Reason
import net.padalton.doormutex.android.data.State
import net.padalton.doormutex.android.data.Status
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Texts shared by the app and the widgets, worded as in the Plasma widget.

/** Lamp colours as on the device and in the Plasma widget, as ARGB. */
object LampColors {
    const val RED = 0xFFE5322D
    const val YELLOW = 0xFFF5B301
    const val GREEN = 0xFF1FA84F
    const val UNLIT = 0xFF3A3D44
    const val HOUSING = 0xFF24262C
}

fun Context.statusText(state: State): String =
    getString(
        when {
            state.phase == Phase.FAULT -> {
                R.string.status_fault
            }

            else -> {
                statusRes(state.status)
            }
        },
    )

fun Context.statusText(status: Status): String = getString(statusRes(status))

private fun statusRes(status: Status) =
    when (status) {
        Status.FREE -> R.string.status_free
        Status.WORKING -> R.string.status_working
        Status.DND -> R.string.status_dnd
        Status.AWAY -> R.string.status_away
        Status.UNKNOWN -> R.string.status_unknown
    }

/** "calendar – video call"; the detail is never an event title (hub rule). */
fun Context.reasonText(state: State): String? {
    val reason = reasonText(state.reason)
    val detail = state.detail?.takeIf { it.isNotBlank() }?.replace('_', ' ')
    return listOfNotNull(reason, detail).joinToString(" – ").ifEmpty { null }
}

fun Context.reasonText(reason: Reason): String? =
    when (reason) {
        Reason.OVERRIDE -> R.string.reason_override
        Reason.POMODORO -> R.string.reason_pomodoro
        Reason.POMODORO_BREAK -> R.string.reason_pomodoro_break
        Reason.MICROPHONE -> R.string.reason_microphone
        Reason.CAMERA -> R.string.reason_camera
        Reason.SCREEN_LOCKED -> R.string.reason_screen_locked
        Reason.PC_OFF -> R.string.reason_pc_off
        Reason.COMPANION_LOST -> R.string.reason_companion_lost
        Reason.COMPANION_OFFLINE -> R.string.reason_companion_offline
        Reason.CALENDAR -> R.string.reason_calendar
        Reason.WORK_HOURS -> R.string.reason_work_hours
        Reason.SIGNAL -> R.string.reason_signal
        Reason.DEFAULT -> R.string.reason_default
        Reason.UNKNOWN -> null
    }?.let(::getString)

/** "until 17:00", "until Thu 09:00" or "until further notice". */
fun Context.untilText(state: State): String =
    state.until?.let { getString(R.string.until, time(it)) } ?: getString(R.string.until_further_notice)

/** "free from 17:00" when not free now and a free slot is known. */
fun Context.nextFreeText(state: State): String? {
    if (state.status == Status.FREE) return null
    val start = state.nextFree?.start ?: return null
    // "until 22:00" already says it when the free slot starts right then.
    if (state.until?.isEqual(start) == true) return null
    return getString(R.string.free_from, time(start))
}

/** TalkBack: "Office: Do not disturb, until 17:00". */
fun Context.describe(profile: ProfileInfo): String =
    getString(R.string.a11y_profile_until, profile.name, statusText(profile.state), untilText(profile.state))

fun Context.time(t: OffsetDateTime): String = time(t.toInstant())

fun Context.time(epochMillis: Long): String = time(Instant.ofEpochMilli(epochMillis))

/** Local time, with the weekday when it is not today. */
fun Context.time(t: Instant): String {
    val zone = ZoneId.systemDefault()
    val local = t.atZone(zone)
    val locale = resources.configuration.locales[0] ?: Locale.getDefault()
    val skeleton = if (DateFormat.is24HourFormat(this)) "Hm" else "hm"
    val pattern =
        if (local.toLocalDate() == LocalDate.now(zone)) {
            DateFormat.getBestDateTimePattern(locale, skeleton)
        } else {
            DateFormat.getBestDateTimePattern(locale, "EEE$skeleton")
        }
    return DateTimeFormatter.ofPattern(pattern, locale).format(local)
}
