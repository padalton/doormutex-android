package net.padalton.doormutex.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SnapshotTest {
    private val state = HubJson.decodeFromString(State.serializer(), javaClass.getResource("/api/state-example.json")!!.readText())
    private val office = ProfileInfo("office", "Office", "office", state)
    private val teen = ProfileInfo("teen", "Teen", "teen", state)
    private val old = Snapshot(listOf(office, teen), fetchedAt = 1_000, attemptedAt = 2_000, problem = Problem.UNREACHABLE)

    @Test
    fun `an update replaces one state and makes the snapshot current`() {
        val free = state.copy(status = Status.FREE, light = Light.GREEN)
        val next = old.with(ProfileState("teen", free), now = 5_000)!!
        assertEquals(listOf(office, teen.copy(state = free)), next.profiles)
        assertEquals(5_000, next.fetchedAt)
        assertEquals(5_000, next.attemptedAt)
        assertNull(next.problem)
    }

    @Test
    fun `an unknown profile needs a full fetch`() {
        assertNull(old.with(ProfileState("garage", state), now = 5_000))
    }

    @Test
    fun `a keep-alive confirms without changing profiles`() {
        val next = old.confirmed(9_000)
        assertEquals(old.profiles, next.profiles)
        assertEquals(Freshness(stale = false, since = 9_000), Freshness.of(next, 9_000, LIVE_INTERVAL))
    }
}
