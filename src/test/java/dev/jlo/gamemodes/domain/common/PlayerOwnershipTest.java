package dev.jlo.gamemodes.domain.common;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerOwnershipTest {
    @Test
    void playerCanOwnOnlyOneQueueOrMatch() {
        PlayerOwnership ownership = new PlayerOwnership();
        UUID player = UUID.randomUUID();

        ownership.claim(player, new Ownership.Queue("opr"));
        assertThrows(IllegalStateException.class, () ->
                ownership.claim(player, new Ownership.Match("siege-1")));
        ownership.release(player);
        ownership.claim(player, new Ownership.Match("siege-1"));
        assertEquals(new Ownership.Match("siege-1"), ownership.ownerOf(player));
    }

    @Test
    void disconnectReservationExpiresAfterConfiguredDuration() {
        PlayerOwnership ownership = new PlayerOwnership();
        UUID player = UUID.randomUUID();
        Instant disconnectedAt = Instant.parse("2026-08-11T12:00:00Z");

        ownership.claim(player, new Ownership.Match("opr-1"));
        ownership.disconnect(player, disconnectedAt, Duration.ofMinutes(5));

        assertTrue(ownership.isReserved(player, disconnectedAt.plusSeconds(299)));
        assertFalse(ownership.isReserved(player, disconnectedAt.plusSeconds(300)));
        assertNull(ownership.expireReservation(player, disconnectedAt.plusSeconds(300)));
    }
}
