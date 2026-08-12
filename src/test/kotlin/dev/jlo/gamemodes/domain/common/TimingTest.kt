package dev.jlo.gamemodes.domain.common

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TimingTest {
    @Test
    fun `deadline reports remaining time and expiry`() {
        val deadline = Deadline(Instant.parse("2026-08-11T12:00:10Z"))
        val now = Instant.parse("2026-08-11T12:00:03Z")

        assertEquals(Duration.ofSeconds(7), deadline.remainingAt(now))
        assertFalse(deadline.isExpiredAt(now))
        assertTrue(deadline.isExpiredAt(deadline.at))
        assertEquals(Duration.ZERO, deadline.remainingAt(deadline.at.plusSeconds(1)))
    }

    @Test
    fun `respawn wave schedules next wave at or after death`() {
        val wave = RespawnWave(period = Duration.ofSeconds(10), anchor = Instant.parse("2026-08-11T12:00:00Z"))

        assertEquals(Instant.parse("2026-08-11T12:00:10Z"), wave.nextWaveAt(Instant.parse("2026-08-11T12:00:01Z")))
        assertEquals(Instant.parse("2026-08-11T12:00:20Z"), wave.nextWaveAt(Instant.parse("2026-08-11T12:00:10Z")))
    }
}
