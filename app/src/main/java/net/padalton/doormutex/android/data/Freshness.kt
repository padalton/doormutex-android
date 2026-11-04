package net.padalton.doormutex.android.data

import kotlin.time.Duration

/**
 * Never show an old state as current: the data is stale when the last
 * refresh failed or when it is older than twice the refresh interval.
 * [since] is the time to show ("since 14:32"), epoch milliseconds.
 */
data class Freshness(
    val stale: Boolean,
    val since: Long?,
) {
    companion object {
        fun of(
            snapshot: Snapshot,
            now: Long,
            interval: Duration,
        ): Freshness {
            if (snapshot.fetchedAt == 0L) return Freshness(stale = snapshot.problem != null, since = null)
            val tooOld = now - snapshot.fetchedAt > 2 * interval.inWholeMilliseconds
            return Freshness(stale = tooOld || snapshot.problem != null, since = snapshot.fetchedAt)
        }
    }
}
