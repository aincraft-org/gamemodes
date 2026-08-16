package dev.jlo.gamemodes.domain.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class MatchRosterTest {
    @Test
    void assignmentKeepsTeamsBalancedAndRespectsQuorum() {
        MatchRoster roster = new MatchRoster(4, Quorum.minimumPlayers(4, 0.5));
        var players = IntStream.rangeClosed(1, 6)
                .mapToObj(it -> UUID.nameUUIDFromBytes(("player-" + it).getBytes(StandardCharsets.UTF_8)))
                .toList();

        for (UUID player : players) {
            roster.join(player);
        }

        assertEquals(3, roster.players(Team.A).size());
        assertEquals(3, roster.players(Team.B).size());
        assertEquals(true, roster.hasQuorum());
    }

    @Test
    void teamCapacityAndDuplicateJoinsAreRejected() {
        MatchRoster roster = new MatchRoster(1, Quorum.fixed(1));
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        roster.join(first);
        assertThrows(IllegalStateException.class, () -> roster.join(first));
        roster.join(second);
        assertThrows(IllegalStateException.class, () -> roster.join(UUID.randomUUID()));
    }

    @Test
    void quorumRoundsFractionalCapacityUp() {
        assertEquals(2, Quorum.minimumPlayers(3, 0.5).getRequiredPlayers());
        assertEquals(1, Quorum.minimumPlayers(3, 0.1).getRequiredPlayers());
    }
}
