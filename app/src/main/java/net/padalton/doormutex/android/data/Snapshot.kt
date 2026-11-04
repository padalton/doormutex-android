package net.padalton.doormutex.android.data

import kotlinx.serialization.Serializable

/** Why the last refresh failed; null in [Snapshot.problem] when it worked. */
@Serializable
enum class Problem {
    UNREACHABLE,
    UNAUTHORIZED,
    FORBIDDEN,
    OTHER,
    ;

    companion object {
        fun of(e: HubException): Problem =
            when (e) {
                is HubException.Unreachable -> UNREACHABLE
                is HubException.Unauthorized -> UNAUTHORIZED
                is HubException.Forbidden -> FORBIDDEN
                else -> OTHER
            }
    }
}

/**
 * The last known state of all profiles, cached so the widgets and the app
 * can show something (dimmed, with its time) when the hub is not reachable.
 */
@Serializable
data class Snapshot(
    val profiles: List<ProfileInfo> = emptyList(),
    /** When [profiles] was fetched, epoch milliseconds; 0 = never. */
    val fetchedAt: Long = 0,
    /** When the last attempt ran, successful or not. */
    val attemptedAt: Long = 0,
    val problem: Problem? = null,
)

/**
 * [update] applied to a known profile: its new state, and the snapshot is
 * current as of [now]. Null when the profile is unknown (added on the hub
 * since the last full fetch): then fetch everything to learn its name.
 */
fun Snapshot.with(
    update: ProfileState,
    now: Long,
): Snapshot? {
    if (profiles.none { it.id == update.profile }) return null
    return copy(
        profiles = profiles.map { if (it.id == update.profile) it.copy(state = update.state) else it },
        fetchedAt = now,
        attemptedAt = now,
        problem = null,
    )
}

/** Nothing changed, but the hub confirmed it at [now] (keep-alive on the open stream). */
fun Snapshot.confirmed(now: Long) = copy(fetchedAt = now, attemptedAt = now, problem = null)
