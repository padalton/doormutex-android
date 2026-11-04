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
