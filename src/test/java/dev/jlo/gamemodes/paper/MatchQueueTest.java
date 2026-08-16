package dev.jlo.gamemodes.paper;

import dev.jlo.gamemodes.domain.common.Team;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MatchQueueTest {
    @Test
    void queueRejectsDuplicateOwnershipAndBalancesTeams() {
        MatchQueue queue = new MatchQueue(Mode.OPR, 2, 1);
        var players = IntStream.rangeClosed(1, 4)
                .mapToObj(i -> UUID.nameUUIDFromBytes(("player-" + i).getBytes(StandardCharsets.UTF_8)))
                .toList();

        for (UUID player : players) {
            queue.join(player);
        }

        assertEquals(Team.A, queue.teamOf(players.get(0)));
        assertEquals(Team.B, queue.teamOf(players.get(1)));
        assertEquals(Team.A, queue.teamOf(players.get(2)));
        assertEquals(Team.B, queue.teamOf(players.get(3)));
        assertThrows(IllegalStateException.class, () -> queue.join(UUID.randomUUID()));
        assertThrows(IllegalStateException.class, () -> queue.join(players.get(0)));
        assertEquals(true, queue.hasQuorum());
    }

    @Test
    void queueLeaveReleasesPlayer() {
        MatchQueue queue = new MatchQueue(Mode.SIEGE, 50, 1);
        UUID player = UUID.randomUUID();
        queue.join(player);

        assertEquals(Team.A, queue.leave(player));
        assertEquals(null, queue.teamOf(player));
    }
}
