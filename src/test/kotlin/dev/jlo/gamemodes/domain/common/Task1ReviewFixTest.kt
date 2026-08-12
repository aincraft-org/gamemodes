package dev.jlo.gamemodes.domain.common

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class Task1ReviewFixTest {
    @Test
    fun `unregister releases every match-owned player`() {
        val coordinator = DefaultMatchCoordinator()
        val match = StubMatch(MatchId("m1"))
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        coordinator.register(match)
        coordinator.claim(first, Ownership.Match("m1"))
        coordinator.claim(second, Ownership.Match("m1"))

        coordinator.unregister(match.id)

        assertEquals(null, coordinator.ownerOf(first))
        assertEquals(null, coordinator.ownerOf(second))
    }

    @Test
    fun `leave only releases queue ownership`() {
        val coordinator = DefaultMatchCoordinator()
        val queuePlayer = UUID.randomUUID()
        val matchPlayer = UUID.randomUUID()
        coordinator.claim(queuePlayer, Ownership.Queue("opr"))
        coordinator.claim(matchPlayer, Ownership.Match("m1"))

        assertTrue(coordinator.leave(queuePlayer))
        assertEquals(null, coordinator.ownerOf(queuePlayer))
        assertTrue(!coordinator.leave(matchPlayer))
        assertEquals(Ownership.Match("m1"), coordinator.ownerOf(matchPlayer))
    }

    @Test
    fun `coordinator lookup expires reservations using injected clock`() {
        val start = Instant.parse("2026-08-11T12:00:00Z")
        val clock = MutableClock(start)
        val coordinator = DefaultMatchCoordinator(clock)
        val player = UUID.randomUUID()
        coordinator.claim(player, Ownership.Queue("opr"))
        coordinator.disconnect(player, Duration.ofMinutes(5))

        assertEquals(Ownership.Queue("opr"), coordinator.ownerOf(player))
        clock.instant = start.plusSeconds(300)
        assertEquals(null, coordinator.ownerOf(player))
    }

    @Test
    fun `same tick comparator orders event kinds before arrival order`() {
        val events = listOf(
            MatchEvent.DeadlineCheck(7),
            MatchEvent.Objective(7, 2),
            MatchEvent.VictoryCheck(7),
            MatchEvent.ScoreUpdate(7),
            MatchEvent.Objective(7, 1)
        )

        assertEquals(
            listOf(
                MatchEvent.Objective(7, 1), MatchEvent.Objective(7, 2),
                MatchEvent.ScoreUpdate(7), MatchEvent.VictoryCheck(7), MatchEvent.DeadlineCheck(7)
            ),
            events.sortedWith(MatchEvent.ORDERING)
        )
    }

    @Test
    fun `match result rejects inconsistent winner and draw states`() {
        assertFailsWith<IllegalArgumentException> { MatchResult(Team.A, draw = true) }
        assertFailsWith<IllegalArgumentException> { MatchResult(null, draw = false) }
        assertEquals(MatchResult(null, draw = true), MatchResult(null))
        assertEquals(MatchResult(Team.B, draw = false), MatchResult(Team.B))
    }

    @Test
    fun `runtime clock is monotonic while epoch time remains persistence clock`() {
        val clock = RuntimeClock(
            epochClock = MutableClock(Instant.parse("2026-08-11T12:00:00Z")),
            monotonicNanos = { 1_000_000_000L }
        )
        assertEquals(Instant.parse("2026-08-11T12:00:00Z"), clock.epochNow())
        assertEquals(1_000_000_000L, clock.monotonicNanos())
        assertTrue(clock.monotonicNanos() >= 0)
    }

    private class MutableClock(var instant: Instant) : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId) = this
        override fun instant() = instant
    }

    private class StubMatch(override val id: MatchId) : Match {
        override val lifecycle = MatchLifecycle()
        override fun handle(event: MatchEvent): List<MatchEvent> = emptyList()
    }
}
