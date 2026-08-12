package dev.jlo.gamemodes.domain.common

sealed interface MatchEvent {
    val tick: Long
    val priority: Int
    val sequence: Long

    data class Objective(override val tick: Long, override val sequence: Long) : MatchEvent {
        override val priority: Int = 0
    }

    data class ScoreUpdate(override val tick: Long, override val sequence: Long = 0) : MatchEvent {
        override val priority: Int = 1
    }

    data class VictoryCheck(override val tick: Long, override val sequence: Long = 0) : MatchEvent {
        override val priority: Int = 2
    }

    data class DeadlineCheck(override val tick: Long, override val sequence: Long = 0) : MatchEvent {
        override val priority: Int = 3
    }

    companion object {
        val ORDERING: Comparator<MatchEvent> = Comparator { left, right ->
            compareValuesBy(left, right, MatchEvent::tick, MatchEvent::priority, MatchEvent::sequence)
        }
    }
}
