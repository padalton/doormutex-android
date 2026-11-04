package net.padalton.doormutex.android.data

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

class FreshnessTest {
    private val interval = 15.minutes
    private val t0 = 1_000_000_000L

    @Test
    fun `fresh within twice the interval`() {
        val s = Snapshot(fetchedAt = t0, attemptedAt = t0)
        assertEquals(Freshness(stale = false, since = t0), Freshness.of(s, t0 + 30.minutes.inWholeMilliseconds, interval))
    }

    @Test
    fun `stale after twice the interval`() {
        val s = Snapshot(fetchedAt = t0, attemptedAt = t0)
        assertEquals(Freshness(stale = true, since = t0), Freshness.of(s, t0 + 30.minutes.inWholeMilliseconds + 1, interval))
    }

    @Test
    fun `stale at once when the hub did not answer`() {
        val s = Snapshot(fetchedAt = t0, attemptedAt = t0 + 1, problem = Problem.UNREACHABLE)
        assertEquals(Freshness(stale = true, since = t0), Freshness.of(s, t0 + 1, interval))
    }

    @Test
    fun `never fetched`() {
        assertEquals(Freshness(stale = false, since = null), Freshness.of(Snapshot(), t0, interval))
        assertEquals(Freshness(stale = true, since = null), Freshness.of(Snapshot(problem = Problem.UNAUTHORIZED), t0, interval))
    }
}
