package dev.jlo.gamemodes.domain.common

import kotlin.test.Test
import kotlin.test.assertEquals

class MatchEventTest {
    @Test
    fun `events order objective then score then victory then deadline`() {
        val events = listOf(
            MatchEvent.DeadlineCheck(tick = 7),
            MatchEvent.ScoreUpdate(tick = 7),
            MatchEvent.Objective(tick = 7, sequence = 2),
            MatchEvent.VictoryCheck(tick = 7),
            MatchEvent.Objective(tick = 7, sequence = 1)
        )

        assertEquals(
            listOf(
                MatchEvent.Objective(tick = 7, sequence = 1),
                MatchEvent.Objective(tick = 7, sequence = 2),
                MatchEvent.ScoreUpdate(tick = 7),
                MatchEvent.VictoryCheck(tick = 7),
                MatchEvent.DeadlineCheck(tick = 7)
            ),
            events.sortedWith(MatchEvent.ORDERING)
        )
    }
}
