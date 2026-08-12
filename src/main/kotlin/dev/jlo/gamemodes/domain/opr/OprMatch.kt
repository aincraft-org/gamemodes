package dev.jlo.gamemodes.domain.opr

import dev.jlo.gamemodes.domain.common.MatchLifecycle
import dev.jlo.gamemodes.domain.common.MatchPhase
import dev.jlo.gamemodes.domain.common.MatchResult
import dev.jlo.gamemodes.domain.common.MatchRoster
import dev.jlo.gamemodes.domain.common.Quorum
import dev.jlo.gamemodes.domain.common.Team
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.math.max

enum class OutpostId { LUNA, SOL, ASTRA }
enum class Resource { INFUSED_WOOD, ORE, HIDES, AZOTH_ESSENCE }
enum class ArmoryItem { GATE_TIER_TWO, COMMAND_POST_TIER_TWO, PROTECTION_WARD, REPAIR }
enum class SummonKind { BEAR, SPECTER, BRUTE }

data class OprConfig(
    val teamCapacity: Int = 20,
    val quorumPerTeam: Int = 10,
    val captureDuration: Duration = Duration.ofSeconds(30),
    val captureDecayDelay: Duration = Duration.ofSeconds(15),
    val scoreInterval: Duration = Duration.ofSeconds(3),
    val victimCooldown: Duration = Duration.ofSeconds(60),
    val spawnProtection: Duration = Duration.ofSeconds(10),
    val combatWindow: Duration = Duration.ofSeconds(30),
    val targetScore: Int = 1000,
    val matchDuration: Duration = Duration.ofMinutes(30),
    val disconnectReservation: Duration = Duration.ofMinutes(5),
    val suddenDeathDuration: Duration = Duration.ofSeconds(90),
    val baronessInterval: Duration = Duration.ofMinutes(10),
    val portalInterval: Duration = Duration.ofMinutes(5),
    val gateTierTwoCost: Int = 25,
    val commandPostTierTwoCost: Int = 20,
    val protectionWardCost: Int = 30,
    val repairCost: Int = 10,
    val summonCapPerTeam: Int = 6,
    val waveInterval: Duration = Duration.ofSeconds(10)
) {
    init {
        require(teamCapacity > 0)
        require(quorumPerTeam > 0 && quorumPerTeam <= teamCapacity)
        require(listOf(captureDuration, captureDecayDelay, scoreInterval, victimCooldown,
            spawnProtection, combatWindow, matchDuration, disconnectReservation,
            suddenDeathDuration, baronessInterval, portalInterval, waveInterval)
            .all { !it.isNegative && !it.isZero })
        require(targetScore > 0)
        require(listOf(gateTierTwoCost, commandPostTierTwoCost, protectionWardCost, repairCost).all { it >= 0 })
        require(summonCapPerTeam > 0)
    }
}

data class OutpostState(
    val id: OutpostId,
    var owner: Team? = null,
    var progress: Duration = Duration.ZERO,
    var lastTick: Instant? = null,
    var emptySince: Instant? = null,
    var capturingTeam: Team? = null,
    var ownershipSince: Instant? = null,
    val occupants: MutableSet<UUID> = linkedSetOf(),
    var wardTeam: Team? = null,
    var wardUntil: Instant? = null
) {
    fun captureBlockedByWard(team: Team, now: Instant): Boolean =
        wardTeam == team.other() && wardUntil?.isAfter(now) == true
}
data class GateState(var tier: Int = 1, var health: Int = 100)
data class Summon(val team: Team, val kind: SummonKind, val outpost: OutpostId)
private fun Team.other(): Team = if (this == Team.A) Team.B else Team.A


class OprMatch(
    val config: OprConfig = OprConfig(),
    val start: Instant,
    val lifecycle: MatchLifecycle = MatchLifecycle(MatchPhase.WAITING)
) {
    val outposts = OutpostId.entries.associateWith { OutpostState(it) }.toMutableMap()
    val gates = Team.entries.associateWith { GateState() }.toMutableMap()
    val expiredPlayers = linkedSetOf<UUID>()
    var result: MatchResult? = null
        private set
    private val roster = MatchRoster(config.teamCapacity, Quorum.fixed(config.quorumPerTeam))
    private val teams = mutableMapOf<UUID, Team>()
    private val scores = Team.entries.associateWith { 0 }.toMutableMap()
    private val carried = mutableMapOf<UUID, MutableMap<Resource, Int>>()
    private val storage = Team.entries.associateWith { Resource.entries.associateWith { 0 }.toMutableMap() }.toMutableMap()
    private val cooldowns = mutableMapOf<UUID, Instant>()
    private val disconnected = mutableMapOf<UUID, Instant>()
    private val spawnProtectedUntil = mutableMapOf<UUID, Instant>()
    private val recentCombat = mutableMapOf<UUID, Instant>()
    private val tokenWallet = Team.entries.associateWith { 0 }.toMutableMap()
    private val scoreLocks = mutableMapOf<Team, Instant>()
    private val commandPostTier = Team.entries.associateWith { 1 }.toMutableMap()
    private val summonsByTeam = Team.entries.associateWith { mutableListOf<Summon>() }.toMutableMap()
    private var now: Instant = start
    private var battleStartedAt: Instant? = null
    private var nextScoreTick = start.plus(config.scoreInterval)
    private var suddenDeathUntil: Instant? = null
    private var wardUntil = mutableMapOf<Team, Instant>()
    private var portalClosedAt: Instant? = null
    fun join(player: UUID): Team {
        check(lifecycle.phase == MatchPhase.WAITING)
        val team = roster.join(player)
        teams[player] = team
        return team
    }
    fun leave(player: UUID): Team? {
        val team = teams.remove(player) ?: return null
        roster.leave(player)
        outposts.values.forEach { outpost ->
            outpost.occupants.remove(player)
            if (outpost.occupants.isEmpty()) {
                outpost.emptySince = now
                outpost.lastTick = now
            }
        }
        carried.remove(player)
        disconnected.remove(player)
        cooldowns.remove(player)
        return team
    }
    fun assign(player: UUID, team: Team) {
        check(player !in teams)
        teams[player] = team
        if (lifecycle.phase == MatchPhase.ACTIVE) {
            spawnProtectedUntil[player] = (battleStartedAt ?: now).plus(config.spawnProtection)
        }
    }
    fun baronessDefeated(team: Team, at: Instant, deficit: Int = 0) {
        val lockDuration = Duration.ofSeconds((60L + deficit.coerceAtLeast(0)).coerceAtMost(150L))
        scoreLocks[team.other()] = at.plus(lockDuration)
    }

    fun beginPreparation(at: Instant) { now = at; lifecycle.transitionTo(MatchPhase.PREPARING) }
    fun start(at: Instant) {
        check(roster.hasQuorum() || teams.size >= config.quorumPerTeam * 2)
        now = at
        battleStartedAt = at
        teams.keys.forEach { spawnProtectedUntil[it] = at.plus(config.spawnProtection) }
        lifecycle.transitionTo(MatchPhase.ACTIVE)
        nextScoreTick = at.plus(config.scoreInterval)
    }
    fun enterOutpost(player: UUID, id: OutpostId) {
        check(teams.containsKey(player))
        val outpost = outposts.getValue(id)
        if (outpost.occupants.isEmpty()) {
            val enteringTeam = teams.getValue(player)
            if (outpost.owner != enteringTeam) {
                outpost.capturingTeam = enteringTeam
                outpost.progress = Duration.ZERO
            }
            outpost.lastTick = now
            outpost.emptySince = null
        }
        outpost.occupants += player
    }
    fun leaveOutpost(player: UUID, id: OutpostId) {
        val outpost = outposts.getValue(id)
        outpost.occupants -= player
        if (outpost.occupants.isEmpty()) {
            outpost.emptySince = now
            outpost.lastTick = now
        }
    }

    fun advanceTo(at: Instant) {
        require(!at.isBefore(now)); now = at
        expireDisconnects(at)
        if (lifecycle.phase != MatchPhase.ACTIVE) return
        if (suddenDeathUntil != null) {
            if (!at.isBefore(suddenDeathUntil)) resolve(null)
            return
        }
        for (outpost in outposts.values) updateCapture(outpost, at)
        while (!at.isBefore(nextScoreTick)) {
            for (outpost in outposts.values) {
                val owner = outpost.owner
                if (owner != null &&
                    outpost.ownershipSince?.isBefore(nextScoreTick) == true &&
                    !isScoreLocked(owner, nextScoreTick)
                ) scores[owner] = score(owner) + 1
            }
            nextScoreTick = nextScoreTick.plus(config.scoreInterval)
        }
        val deadline = battleStartedAt?.plus(config.matchDuration)
        if (deadline != null && at >= deadline) resolveAtDeadline(at) else checkTarget()
    }
    private fun updateCapture(outpost: OutpostState, at: Instant) {
        val present = outpost.occupants.mapNotNull { teams[it] }.toSet()
        if (present.isEmpty()) {
            val emptySince = outpost.emptySince ?: at.also { outpost.emptySince = it }
            val previous = outpost.lastTick ?: at
            val decayStarts = maxOf(previous, emptySince.plus(config.captureDecayDelay))
            if (at.isAfter(decayStarts)) {
                outpost.progress = outpost.progress.minus(Duration.between(decayStarts, at))
                    .coerceAtLeast(Duration.ZERO)
                if (outpost.progress.isZero) outpost.capturingTeam = null
            }
            outpost.lastTick = at
            return
        }
        outpost.emptySince = null
        if (present.size != 1) {
            outpost.lastTick = at
            return
        }
        val team = present.single()
        if (outpost.owner == team) {
            outpost.lastTick = at
            outpost.progress = Duration.ZERO
            outpost.capturingTeam = null
            return
        }
        if (outpost.captureBlockedByWard(team, at)) {
            outpost.lastTick = at
            return
        }
        if (outpost.capturingTeam != team) {
            outpost.capturingTeam = team
            outpost.progress = Duration.ZERO
            outpost.lastTick = at
            return
        }
        val elapsed = outpost.lastTick?.let { Duration.between(it, at) } ?: Duration.ZERO
        outpost.lastTick = at
        outpost.progress += elapsed
        if (outpost.progress >= config.captureDuration) {
            outpost.owner = team
            outpost.ownershipSince = at
            outpost.progress = Duration.ZERO
            outpost.capturingTeam = null
        }
    }

    fun score(team: Team): Int = scores.getValue(team)
    fun addScore(team: Team, amount: Int, at: Instant) {
        require(amount >= 0)
        if (!isScoreLocked(team, at)) scores[team] = score(team) + amount
        checkTarget()
    }
    private fun checkTarget() { if (lifecycle.phase == MatchPhase.ACTIVE) Team.entries.firstOrNull { score(it) >= config.targetScore }?.let { resolve(it) } }
    private fun isScoreLocked(team: Team, at: Instant): Boolean = scoreLocks[team]?.isAfter(at) == true

    fun recordKill(killer: UUID, victim: UUID, at: Instant) {
        val kt = teams[killer] ?: return
        val vt = teams[victim] ?: return
        if (kt == vt || at.isBefore(cooldowns[victim] ?: Instant.MIN)) return
        cooldowns[victim] = at.plus(config.victimCooldown)
        addScore(kt, 1, at)
    }
    fun recordCombat(attacker: UUID, victim: UUID, at: Instant): Boolean {
        if (lifecycle.phase != MatchPhase.ACTIVE) return false
        val attackerTeam = teams[attacker] ?: return false
        val victimTeam = teams[victim] ?: return false
        if (attackerTeam == victimTeam) return false
        if (at.isBefore(spawnProtectedUntil[attacker] ?: Instant.MIN) ||
            at.isBefore(spawnProtectedUntil[victim] ?: Instant.MIN)
        ) return false
        recentCombat[attacker] = at
        recentCombat[victim] = at
        return true
    }

    fun recordDeath(victim: UUID, killer: UUID?, at: Instant): Boolean {
        playerDied(victim)
        val eligible = killer != null &&
            teams[killer] != null &&
            teams[victim] != null &&
            teams[killer] != teams[victim] &&
            !at.isBefore(spawnProtectedUntil[victim] ?: Instant.MIN) &&
            recentCombat[killer]?.let { Duration.between(it, at) <= config.combatWindow } == true &&
            recentCombat[victim]?.let { Duration.between(it, at) <= config.combatWindow } == true
        if (eligible) recordKill(killer, victim, at)
        recentCombat.remove(victim)
        return eligible
    }

    fun deposit(player: UUID, resource: Resource, amount: Int) { val team = teams.getValue(player); val held = carried(player, resource); require(amount in 0..held); carried.getOrPut(player) { mutableMapOf() }[resource] = held - amount; storage.getValue(team)[resource] = storage.getValue(team).getValue(resource) + amount }
    fun carry(player: UUID, resource: Resource, amount: Int) { require(amount >= 0); carried.getOrPut(player) { mutableMapOf() }[resource] = carried(player, resource) + amount }
    fun carried(player: UUID, resource: Resource): Int = carried[player]?.get(resource) ?: 0
    fun storage(team: Team, resource: Resource): Int = storage.getValue(team).getValue(resource)
    fun playerDied(player: UUID) { for (resource in Resource.entries) carried.getOrPut(player) { mutableMapOf() }[resource] = carried(player, resource) / 2 }

    fun addBattleTokens(team: Team, amount: Int) { require(amount >= 0); tokenWallet[team] = tokenWallet.getValue(team) + amount }
    fun battleTokens(team: Team): Int = tokenWallet.getValue(team)
    fun purchaseArmory(player: UUID, item: ArmoryItem) {
        val team = teams.getValue(player)
        val cost = when (item) {
            ArmoryItem.GATE_TIER_TWO -> config.gateTierTwoCost
            ArmoryItem.COMMAND_POST_TIER_TWO -> config.commandPostTierTwoCost
            ArmoryItem.PROTECTION_WARD -> config.protectionWardCost
            ArmoryItem.REPAIR -> config.repairCost
        }
        require(tokenWallet.getValue(team) >= cost)
        tokenWallet[team] = tokenWallet.getValue(team) - cost
        when (item) {
            ArmoryItem.GATE_TIER_TWO -> gates.getValue(team).tier = max(2, gates.getValue(team).tier)
            ArmoryItem.COMMAND_POST_TIER_TWO -> commandPostTier[team] = 2
            ArmoryItem.PROTECTION_WARD -> activateWard(team, now.plus(config.suddenDeathDuration))
            ArmoryItem.REPAIR -> gates.getValue(team).health = 100
        }
    }
    fun activateWard(team: Team, until: Instant) { wardUntil[team] = until; outposts.values.forEach { it.wardTeam = team; it.wardUntil = until } }
    fun summon(team: Team, kind: SummonKind, outpost: OutpostId) { require(this.outposts.getValue(outpost).owner == team); require(summonsByTeam.getValue(team).size < config.summonCapPerTeam); summonsByTeam.getValue(team) += Summon(team, kind, outpost) }
    fun summons(team: Team): List<Summon> = summonsByTeam.getValue(team).toList()

    fun disconnect(player: UUID, at: Instant) { disconnected[player] = at.plus(config.disconnectReservation) }
    fun reconnect(player: UUID, at: Instant): Boolean { val expiry = disconnected[player] ?: return false; if (at <= expiry) { disconnected.remove(player); return true }; expiredPlayers += player; return false }
    private fun expireDisconnects(at: Instant) { disconnected.filterValues { at > it }.keys.forEach { expiredPlayers += it; disconnected.remove(it); playerDied(it) } }
    private fun resolveAtDeadline(at: Instant) {
        val winner = when {
            score(Team.A) != score(Team.B) -> if (score(Team.A) > score(Team.B)) Team.A else Team.B
            outposts.values.count { it.owner == Team.A } != outposts.values.count { it.owner == Team.B } ->
                if (outposts.values.count { it.owner == Team.A } > outposts.values.count { it.owner == Team.B }) Team.A else Team.B
            else -> null
        }
        if (winner == null) {
            suddenDeathUntil = at.plus(config.suddenDeathDuration)
            return
        }
        resolve(winner)
    }
    private fun resolve(winner: Team?) {
        if (lifecycle.phase == MatchPhase.ACTIVE) {
            lifecycle.transitionTo(MatchPhase.RESOLVING)
            result = MatchResult(winner)
        }
    }
    fun resolveAndCleanup(at: Instant) {
        when (lifecycle.phase) {
            MatchPhase.WAITING -> return
            MatchPhase.PREPARING -> lifecycle.transitionTo(MatchPhase.CLEANUP)
            MatchPhase.ACTIVE -> {
                resolveAtDeadline(at)
                if (lifecycle.phase == MatchPhase.ACTIVE && suddenDeathUntil != null) resolve(null)
            }
            else -> Unit
        }
        if (lifecycle.phase == MatchPhase.RESOLVING) lifecycle.transitionTo(MatchPhase.CLEANUP)
        if (lifecycle.phase == MatchPhase.CLEANUP) {
            lifecycle.transitionTo(MatchPhase.WAITING)
            roster.players(Team.A).toList().forEach { roster.leave(it) }
            roster.players(Team.B).toList().forEach { roster.leave(it) }
            teams.clear()
            expiredPlayers.clear()
            outposts.values.forEach {
                it.owner = null
                it.progress = Duration.ZERO
                it.lastTick = null
                it.emptySince = null
                it.capturingTeam = null
                it.ownershipSince = null
                it.occupants.clear()
                it.wardTeam = null
                it.wardUntil = null
            }
            gates.values.forEach {
                it.tier = 1
                it.health = 100
            }
            scores.keys.forEach { scores[it] = 0 }
            carried.clear()
            storage.values.forEach { resources -> resources.keys.forEach { resources[it] = 0 } }
            cooldowns.clear()
            disconnected.clear()
            spawnProtectedUntil.clear()
            recentCombat.clear()
            tokenWallet.keys.forEach { tokenWallet[it] = 0 }
            scoreLocks.clear()
            commandPostTier.keys.forEach { commandPostTier[it] = 1 }
            summonsByTeam.values.forEach { it.clear() }
            wardUntil.clear()
            portalClosedAt = null
            suddenDeathUntil = null
            battleStartedAt = null
            now = at
            nextScoreTick = at.plus(config.scoreInterval)
            result = null
        }
    }
}
