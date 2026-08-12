package dev.jlo.gamemodes.paper

import dev.jlo.gamemodes.domain.common.MatchPhase
import dev.jlo.gamemodes.domain.common.MatchResult
import dev.jlo.gamemodes.domain.common.Team
import dev.jlo.gamemodes.domain.opr.OprConfig
import dev.jlo.gamemodes.domain.opr.OprMatch
import dev.jlo.gamemodes.domain.siege.SiegeConfig
import dev.jlo.gamemodes.domain.siege.SiegeMatch
import java.time.Clock
import java.util.UUID

@Suppress("unused")
enum class TeamAssignment { A, B }

/** Owns one arena reservation and adapts either deterministic rules engine to the Paper layer. */
class MatchSession(
    val mode: Mode,
    val arenaId: String,
    capacityPerTeam: Int,
    quorumPerTeam: Int,
    private val clock: Clock = Clock.systemUTC(),
    oprConfig: OprConfig = OprConfig(teamCapacity = capacityPerTeam, quorumPerTeam = quorumPerTeam),
    siegeConfig: SiegeConfig = SiegeConfig(quorum = quorumPerTeam)
) {
    val id: String = "$mode:$arenaId:${UUID.randomUUID()}"
    private val queue = MatchQueue(mode, capacityPerTeam, quorumPerTeam)
    private val configuredSiege = siegeConfig
    private val teams = linkedMapOf<UUID, Team>()
    private val opr = if (mode == Mode.OPR) OprMatch(oprConfig, clock.instant()) else null
    private val siege = if (mode == Mode.SIEGE) SiegeMatch(dev.jlo.gamemodes.domain.common.MatchId(id), configuredSiege, clock.instant()) else null

    private var preparationStartedAt: java.time.Instant? = null
    private var battleStartedAt: java.time.Instant? = null

    val phase: MatchPhase get() = when {
        opr != null -> opr.lifecycle.phase
        else -> siege!!.lifecycle.phase
    }

    val result: MatchResult? get() = opr?.result ?: siege?.result

    @Synchronized
    fun join(player: UUID, preferred: TeamAssignment? = null): TeamAssignment {
        val team = queue.join(player, preferred?.let { if (it == TeamAssignment.A) Team.A else Team.B })
        teams[player] = team
        if (opr != null) opr.assign(player, team) else siege!!.assign(player, team)
        return if (team == Team.A) TeamAssignment.A else TeamAssignment.B
    }

    @Synchronized
    fun leave(player: UUID): TeamAssignment? {
        val team = queue.leave(player) ?: return null
        teams.remove(player)
        if (opr != null) opr.leave(player) else siege!!.leave(player)
        return if (team == Team.A) TeamAssignment.A else TeamAssignment.B
    }

    @Synchronized
    fun teamOf(player: UUID): TeamAssignment? = queue.teamOf(player)?.let { if (it == Team.A) TeamAssignment.A else TeamAssignment.B }

    @Synchronized
    fun ready(player: UUID): Boolean = if (siege != null) {
        queue.markReady(player)
        siege!!.ready(player)
        true
    } else queue.markReady(player)

    @Synchronized
    fun startIfReady(): Boolean {
        if (phase == MatchPhase.RESOLVING || phase == MatchPhase.CLEANUP) return false
        if (!queue.hasQuorum()) return false
        val now = clock.instant()
        if (siege != null) {
            if (phase == MatchPhase.WAITING) {
                siege.startPreparing(now)
                preparationStartedAt = now
            }
            if (phase == MatchPhase.PREPARING && queue.allReady()) {
                siege.advanceTo(now)
                if (phase == MatchPhase.PREPARING && configuredSiege.preparation.isZero) siege.advanceTo(now.plusNanos(1))
            }
        } else {
            if (phase == MatchPhase.WAITING) {
                opr!!.beginPreparation(now)
                preparationStartedAt = now
            }
            if (phase == MatchPhase.PREPARING) opr!!.start(now)
        }
        if (phase == MatchPhase.ACTIVE && battleStartedAt == null) battleStartedAt = now
        return phase == MatchPhase.ACTIVE
    }

    @Synchronized
    fun advance() {
        val now = clock.instant()
        if (opr != null) opr.advanceTo(now) else siege!!.advanceTo(now)
    }

    fun advanceAt(at: java.time.Instant) {
        if (opr != null) opr.advanceTo(at) else siege!!.advanceTo(at)
    }

    fun captureRally(point: dev.jlo.gamemodes.domain.siege.RallyPoint, player: UUID, at: java.time.Instant): Boolean =
        siege?.captureRally(point, player, at) ?: false

    fun damageGate(player: UUID, amount: Int, at: java.time.Instant): Boolean =
        siege?.damageStructure(dev.jlo.gamemodes.domain.siege.Structure.GATE, dev.jlo.gamemodes.domain.siege.SiegeWeapon.CANNON, player, amount, at) ?: false

    fun beginClaim(player: UUID, at: java.time.Instant): Boolean = siege?.beginClaim(player, at) ?: false

    fun completeClaim(player: UUID, at: java.time.Instant): Boolean = siege?.completeClaim(player, at) ?: false
    fun enterOutpost(player: UUID, outpost: dev.jlo.gamemodes.domain.opr.OutpostId) {
        opr?.enterOutpost(player, outpost)
    }

    fun disconnect(player: UUID) {
        if (opr != null) {
            opr.disconnect(player, clock.instant())
        } else {
            siege?.disconnect(player)
        }
    }

    fun recordCombat(attacker: UUID, victim: UUID): Boolean =
        opr?.recordCombat(attacker, victim, clock.instant()) ?: false

    fun playerDied(player: UUID, killer: UUID? = null): Boolean {
        if (phase != MatchPhase.ACTIVE || teamOf(player) == null) return false
        val sameSessionEnemy = killer?.takeIf {
            teamOf(it) != null && teamOf(it) != teamOf(player)
        }
        return if (opr != null) {
            opr.recordDeath(player, sameSessionEnemy, clock.instant())
        } else {
            siege!!.recordDeath(player, sameSessionEnemy)
            sameSessionEnemy != null
        }
    }

    fun respawnLocation(player: org.bukkit.entity.Player): org.bukkit.Location? = null

    fun reconcileObjective(player: UUID, location: org.bukkit.Location) {
        if (opr == null || phase != MatchPhase.ACTIVE) return
        val index = Math.floorMod(location.blockX + location.blockZ, dev.jlo.gamemodes.domain.opr.OutpostId.entries.size)
        val objective = dev.jlo.gamemodes.domain.opr.OutpostId.entries[index]
        dev.jlo.gamemodes.domain.opr.OutpostId.entries.filter { it != objective }.forEach { opr.leaveOutpost(player, it) }
        opr.enterOutpost(player, objective)
    }
    fun recordKill(killer: UUID, victim: UUID) {
        opr?.recordKill(killer, victim, clock.instant())
    }

    @Synchronized
    fun cleanup() {
        if (opr != null) {
            if (phase != MatchPhase.WAITING) opr.resolveAndCleanup(clock.instant())
        } else if (phase != MatchPhase.WAITING) {
            siege!!.abort()
            siege!!.cleanup()
        }
        teams.keys.toList().forEach { queue.leave(it) }
        teams.clear()
    }
}
