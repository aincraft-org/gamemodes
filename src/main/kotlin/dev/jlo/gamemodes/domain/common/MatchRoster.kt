package dev.jlo.gamemodes.domain.common

import java.util.UUID
import kotlin.math.ceil

enum class Team {
    A,
    B
}

data class Quorum private constructor(val requiredPlayers: Int) {
    init {
        require(requiredPlayers > 0) { "Quorum must require at least one player" }
    }

    companion object {
        fun fixed(requiredPlayers: Int): Quorum = Quorum(requiredPlayers)

        fun minimumPlayers(capacityPerTeam: Int, fraction: Double): Quorum {
            require(capacityPerTeam > 0) { "Capacity must be positive" }
            require(fraction > 0.0 && fraction <= 1.0) { "Quorum fraction must be in (0, 1]" }
            return Quorum(ceil(capacityPerTeam * fraction).toInt())
        }
    }
}

class MatchRoster(private val teamCapacity: Int, private val quorum: Quorum) {
    private val members = linkedMapOf<Team, LinkedHashSet<UUID>>(
        Team.A to linkedSetOf(),
        Team.B to linkedSetOf()
    )

    init {
        require(teamCapacity > 0) { "Team capacity must be positive" }
    }

    fun join(player: UUID): Team {
        check(members.values.none { player in it }) { "Player is already in this roster" }
        val team = members.minWith(compareBy<Map.Entry<Team, LinkedHashSet<UUID>>> { it.value.size }.thenBy { it.key.ordinal }).key
        check(members.getValue(team).size < teamCapacity) { "Both teams are full" }
        members.getValue(team).add(player)
        return team
    }

    fun leave(player: UUID): Team? = members.entries.firstOrNull { it.value.remove(player) }?.key

    fun players(team: Team): Set<UUID> = members.getValue(team).toSet()

    fun teamOf(player: UUID): Team? = members.entries.firstOrNull { player in it.value }?.key

    fun hasQuorum(): Boolean = Team.entries.all { members.getValue(it).size >= quorum.requiredPlayers }
}
