package dev.jlo.gamemodes.domain.common;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MatchEventTest {
    @Test
    void eventsOrderObjectiveThenScoreThenVictoryThenDeadline() {
        var events = List.of(
                new MatchEvent.DeadlineCheck(7),
                new MatchEvent.ScoreUpdate(7),
                new MatchEvent.Objective(7, 2),
                new MatchEvent.VictoryCheck(7),
                new MatchEvent.Objective(7, 1)
        );

        assertEquals(
                List.of(
                        new MatchEvent.Objective(7, 1),
                        new MatchEvent.Objective(7, 2),
                        new MatchEvent.ScoreUpdate(7),
                        new MatchEvent.VictoryCheck(7),
                        new MatchEvent.DeadlineCheck(7)
                ),
                events.stream().sorted(MatchEvent.ORDERING).toList()
        );
    }
}
