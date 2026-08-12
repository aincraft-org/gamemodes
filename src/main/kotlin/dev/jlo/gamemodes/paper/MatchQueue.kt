package dev.jlo.gamemodes.paper

import dev.jlo.gamemodes.domain.common.Team
import java.util.UUID

/** Supported playable Paper modes. */
enum class Mode { OPR, SIEGE;
    companion object {
        fun parse(value: String): Mode? = entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
}

/** Thread-safe queue with deterministic balanced team assignment. */
class MatchQueue(
    val mode: Mode,
    private val capacityPerTeam: Int,
    val quorumPerTeam: Int
) {
    init {
        require(capacityPerTeam > 0) { "Team capacity must be positive" }
        require(quorumPerTeam in 1..capacityPerTeam) { "Quorum must be within capacity" }
    }

    private val teams = linkedMapOf(Team.A to linkedSetOf<UUID>(), Team.B to linkedSetOf<UUID>())
    private val ready = linkedSetOf<UUID>()

    @Synchronized
    fun join(player: UUID, preferred: Team? = null): Team {
        check(teamOf(player) == null) { "Player is already queued" }
        val team = preferred?.takeIf { teams.getValue(it).size < capacityPerTeam }
            ?: teams.minWith(compareBy<Map.Entry<Team, LinkedHashSet<UUID>>> { it.value.size }.thenBy { it.key.ordinal }).key
        check(teams.getValue(team).size < capacityPerTeam) { "Queue is full" }
        teams.getValue(team).add(player)
        return team
    }

    @Synchronized
    fun leave(player: UUID): Team? {
        ready.remove(player)
        return teams.entries.firstOrNull { it.value.remove(player) }?.key
    }

    @Synchronized
    fun markReady(player: UUID): Boolean {
        check(teamOf(player) != null) { "Player is not queued" }
        return ready.add(player)
    }

    @Synchronized
    fun isReady(player: UUID): Boolean = player in ready

    @Synchronized
    fun teamOf(player: UUID): Team? = teams.entries.firstOrNull { player in it.value }?.key

    @Synchronized
    fun players(team: Team): Set<UUID> = teams.getValue(team).toSet()

    @Synchronized
    fun allPlayers(): Set<UUID> = teams.values.flatten().toSet()

    @Synchronized
    fun hasQuorum(): Boolean = Team.entries.all { teams.getValue(it).size >= quorumPerTeam }

    @Synchronized
    fun allReady(): Boolean = hasQuorum() && allPlayers().all(ready::contains)

    @Synchronized
    fun size(): Int = teams.values.sumOf { it.size }
}
