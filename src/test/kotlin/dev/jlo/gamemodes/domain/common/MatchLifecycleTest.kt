package dev.jlo.gamemodes.domain.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MatchLifecycleTest {
    @Test
    fun `lifecycle follows common arena flow`() {
        val lifecycle = MatchLifecycle()

        assertEquals(MatchPhase.DISABLED, lifecycle.phase)
        lifecycle.transitionTo(MatchPhase.WAITING)
        lifecycle.transitionTo(MatchPhase.PREPARING)
        lifecycle.transitionTo(MatchPhase.ACTIVE)
        lifecycle.transitionTo(MatchPhase.RESOLVING)
        lifecycle.transitionTo(MatchPhase.CLEANUP)
        lifecycle.transitionTo(MatchPhase.WAITING)
        assertEquals(MatchPhase.WAITING, lifecycle.phase)
    }

    @Test
    fun `illegal transitions are rejected`() {
        val lifecycle = MatchLifecycle()

        assertFailsWith<IllegalStateException> {
            lifecycle.transitionTo(MatchPhase.ACTIVE)
        }
        lifecycle.transitionTo(MatchPhase.WAITING)
        assertFailsWith<IllegalStateException> {
            lifecycle.transitionTo(MatchPhase.CLEANUP)
        }
    }
}
