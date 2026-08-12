package dev.jlo.gamemodes.domain.common

import java.util.UUID

@JvmInline
value class MatchId(val value: String) {
    init {
        require(value.isNotBlank()) { "Match ID must not be blank" }
    }
}

data class MatchResult(val winner: Team?, val draw: Boolean = winner == null) {
    init {
        require(draw == (winner == null)) { "A draw must not have a winner, and a winner is not a draw" }
    }
}

interface Match {
    val id: MatchId
    val lifecycle: MatchLifecycle
    fun handle(event: MatchEvent): List<MatchEvent>
}

interface MatchCoordinator {
    fun register(match: Match)
    fun unregister(id: MatchId)
    fun ownerOf(player: UUID): Ownership?
}

interface PaperGateway {
    fun sendMessage(player: UUID, message: String)
}
