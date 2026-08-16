package dev.jlo.gamemodes.domain.common;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimingTest {
    @Test
    void deadlineReportsRemainingTimeAndExpiry() {
        Deadline deadline = new Deadline(Instant.parse("2026-08-11T12:00:10Z"));
        Instant now = Instant.parse("2026-08-11T12:00:03Z");

        assertEquals(Duration.ofSeconds(7), deadline.remainingAt(now));
        assertFalse(deadline.isExpiredAt(now));
        assertTrue(deadline.isExpiredAt(deadline.getAt()));
        assertEquals(Duration.ZERO, deadline.remainingAt(deadline.getAt().plusSeconds(1)));
    }

    @Test
    void respawnWaveSchedulesNextWaveAtOrAfterDeath() {
        RespawnWave wave = new RespawnWave(
                Duration.ofSeconds(10),
                Instant.parse("2026-08-11T12:00:00Z"));

        assertEquals(
                Instant.parse("2026-08-11T12:00:10Z"),
                wave.nextWaveAt(Instant.parse("2026-08-11T12:00:01Z")));
        assertEquals(
                Instant.parse("2026-08-11T12:00:20Z"),
                wave.nextWaveAt(Instant.parse("2026-08-11T12:00:10Z")));
    }
}
