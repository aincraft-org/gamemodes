package dev.jlo.gamemodes.domain.siege

import dev.jlo.gamemodes.domain.common.Match
import dev.jlo.gamemodes.domain.common.MatchEvent
import dev.jlo.gamemodes.domain.common.MatchId
import dev.jlo.gamemodes.domain.common.MatchLifecycle
import dev.jlo.gamemodes.domain.common.MatchPhase
import dev.jlo.gamemodes.domain.common.MatchResult
import dev.jlo.gamemodes.domain.common.Team
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

enum class RallyPoint { A, B, C }
enum class Structure { GATE, ARMORY, GENERATOR, SIEGE_WEAPON, CLAIM }
enum class SiegeWeapon(
    val team: Team,
    val quota: Int,
    val ammo: Int,
    val cooldown: Duration,
    val damage: Int
) {
    CANNON(Team.A, 2, 20, Duration.ofSeconds(2), 20),
    FIRE_LAUNCHER(Team.A, 2, 12, Duration.ofSeconds(3), 15),
    REPEATER(Team.A, 4, 60, Duration.ofMillis(500), 5),
    BALLISTA(Team.B, 2, 20, Duration.ofSeconds(2), 20),
    EXPLOSIVE_CANNON(Team.B, 2, 12, Duration.ofSeconds(3), 25),
    REPEATER_TURRET(Team.B, 4, 60, Duration.ofMillis(500), 5),
    FIRE_DROPPER(Team.B, 3, 20, Duration.ofSeconds(2), 15),
    HORN_OF_RESILIENCE(Team.B, 1, 3, Duration.ofSeconds(10), 0)
}

data class SiegeWeaponConfig(
    val quota: Int,
    val ammo: Int,
    val cooldown: Duration,
    val damage: Int
) {
    init {
        require(quota >= 0 && ammo >= 0 && damage >= 0)
        require(!cooldown.isNegative)
    }
}

data class SiegeConfig(
    val preparation: Duration = Duration.ofMinutes(5),
    val battle: Duration = Duration.ofMinutes(30),
    val rallyCapture: Duration = Duration.ofSeconds(30),
    val claimCapture: Duration = Duration.ofSeconds(30),
    val rallyDecayDelay: Duration = Duration.ofSeconds(15),
    val attackerWave: Duration = Duration.ofSeconds(20),
    val defenderWave: Duration = Duration.ofSeconds(20),
    val quorum: Int = 25,
    val structureHealth: Map<Structure, Int> = Structure.entries.associateWith { 100 },
    val repairRate: Int = 10,
    val repairCost: Int = 1,
    val suppliesPerWave: Int = 10,
    val contributionTokenRate: Int = 1,
    val weaponConfigs: Map<SiegeWeapon, SiegeWeaponConfig> = emptyMap(),
    val mineDamage: Int = 40,
    val kegDamage: Int = 80,
    val kegFuse: Duration = Duration.ofSeconds(2)
) {
    init {
        require(!preparation.isNegative && !battle.isNegative && !rallyCapture.isNegative && !claimCapture.isNegative)
        require(!rallyDecayDelay.isNegative && !attackerWave.isNegative && !defenderWave.isNegative)
        require(quorum > 0 && repairRate >= 0 && repairCost >= 0 && suppliesPerWave >= 0 && contributionTokenRate >= 0)
        require(mineDamage >= 0 && kegDamage >= 0 && !kegFuse.isNegative)
        structureHealth.values.forEach { require(it >= 0) }
    }

    fun weapon(weapon: SiegeWeapon): SiegeWeaponConfig = weaponConfigs[weapon]
        ?: SiegeWeaponConfig(weapon.quota, weapon.ammo, weapon.cooldown, weapon.damage)
}

data class SiegePlayerStats(val kills: Int = 0, val deaths: Int = 0, val contribution: Int = 0, val battleTokens: Int = 0)
data class SiegeStructureState(val health: Int, val maxHealth: Int)
data class SiegeKeg(val id: String, val owner: Team, val armedAt: Instant, val destroyed: Boolean = false, val disarmed: Boolean = false)

class SiegeMatch(
    override val id: MatchId,
    val config: SiegeConfig = SiegeConfig(),
    private val createdAt: Instant = Instant.now()
) : Match {
    override val lifecycle = MatchLifecycle(MatchPhase.WAITING)
    private val roster = linkedMapOf<Team, LinkedHashSet<UUID>>(Team.A to linkedSetOf(), Team.B to linkedSetOf())
    private val ready = linkedSetOf<UUID>()
    private val rallies = RallyPoint.entries.associateWithTo(linkedMapOf()) { false }
    private val rallyProgress = RallyPoint.entries.associateWithTo(linkedMapOf()) { Duration.ZERO }
    private val rallyPresence = RallyPoint.entries.associateWithTo(linkedMapOf()) { linkedSetOf<UUID>() }
    private val rallyContested = linkedSetOf<RallyPoint>()
    private val rallyStarted = linkedMapOf<RallyPoint, Instant>()
    private val rallyCaptureStarted = linkedSetOf<RallyPoint>()
    private val claimPresence = linkedSetOf<UUID>()
    private val structures = Structure.entries.associateWithTo(linkedMapOf()) {
        val health = config.structureHealth[it] ?: 100
        SiegeStructureState(health, health)
    }
    private val stats = linkedMapOf<UUID, SiegePlayerStats>()
    private val weaponUses = linkedMapOf<Pair<UUID, SiegeWeapon>, Int>()
    private val weaponLastUse = linkedMapOf<Pair<UUID, SiegeWeapon>, Instant>()
    private val kegs = linkedMapOf<String, SiegeKeg>()
    private var claimStarted: Instant? = null
    private var preparationStarted: Instant? = null
    private var battleStarted: Instant? = null
    private var lastAdvancedAt: Instant = createdAt
    private var resolvedResult: MatchResult? = null
    private var nextKegId = 0L
    private var manuallyGeneratedSupplies = 0
    private var attackerWaveCount = 0
    private var defenderWaveCount = 0
    var attackerSupplies: Int = 0
        private set
    val battleDeadline: Instant get() = (battleStarted ?: createdAt).plus(config.battle)
    val result: MatchResult? get() = resolvedResult
    val allRalliesCaptured: Boolean get() = rallies.values.all { it }
    val claimProgress: Duration get() = claimStarted?.let { Duration.between(it, lastAdvancedAt).coerceAtLeast(Duration.ZERO) } ?: Duration.ZERO
    val structuresState: Map<Structure, SiegeStructureState> get() = structures.toMap()
    fun neutralPresence(point: RallyPoint): Set<UUID> = rallyPresence.getValue(point).toSet()

    fun join(player: UUID): Team {
        check(lifecycle.phase == MatchPhase.WAITING || lifecycle.phase == MatchPhase.PREPARING)
        check(roster.values.none { player in it }) { "Player is already in this match" }
        val team = roster.minWith(compareBy<Map.Entry<Team, LinkedHashSet<UUID>>> { it.value.size }.thenBy { it.key.ordinal }).key
        check(roster.getValue(team).size < config.quorum * 2) { "Roster is full" }
        roster.getValue(team).add(player)
        stats.putIfAbsent(player, SiegePlayerStats())
        return team
    }
    fun assign(player: UUID, team: Team) {
        check(lifecycle.phase == MatchPhase.WAITING || lifecycle.phase == MatchPhase.PREPARING)
        check(teamOf(player) == null) { "Player is already in this match" }
        check(roster.getValue(team).size < config.quorum * 2) { "Roster is full" }
        roster.getValue(team).add(player)
        stats.putIfAbsent(player, SiegePlayerStats())
    }

    fun leave(player: UUID): Team? {
        val team = roster.entries.firstOrNull { it.value.remove(player) }?.key
        ready.remove(player)
        rallyPresence.values.forEach { it.remove(player) }
        claimPresence.remove(player)
        if (lifecycle.phase == MatchPhase.PREPARING && Team.entries.any { roster.getValue(it).isEmpty() }) {
            // A preparing match can wait for a replacement roster.
            ready.retainAll(roster.values.flatten().toSet())
        }
        return team
    }
    fun disconnect(player: UUID) {
        check(teamOf(player) != null)
        rallyPresence.values.forEach { it.remove(player) }
        claimPresence.remove(player)
    }

    fun recordDeath(victim: UUID, killer: UUID? = null) {
        check(lifecycle.phase == MatchPhase.ACTIVE)
        check(teamOf(victim) != null)
        stats[victim] = stats(victim).let { it.copy(deaths = it.deaths + 1) }
        if (killer != null && teamOf(killer) != null && teamOf(killer) != teamOf(victim)) {
            stats[killer] = stats(killer).let {
                it.copy(
                    kills = it.kills + 1,
                    contribution = it.contribution + 1,
                    battleTokens = it.battleTokens + config.contributionTokenRate
                )
            }
        }
    }
    fun teamOf(player: UUID): Team? = roster.entries.firstOrNull { player in it.value }?.key
    fun players(team: Team): Set<UUID> = roster.getValue(team).toSet()
    fun stats(player: UUID): SiegePlayerStats = stats[player] ?: SiegePlayerStats()
    fun ready(player: UUID) {
        check(teamOf(player) != null)
        ready += player
    }
    fun enterRally(point: RallyPoint, player: UUID) {
        check(teamOf(player) != null)
        rallyPresence.getValue(point) += player
    }
    fun leaveRally(point: RallyPoint, player: UUID) {
        rallyPresence.getValue(point).remove(player)
    }
    fun startPreparing(at: Instant = createdAt) {
        check(lifecycle.phase == MatchPhase.WAITING)
        check(roster.values.any { it.isNotEmpty() }) { "At least one roster is required" }
        preparationStarted = at
        lastAdvancedAt = at
        lifecycle.transitionTo(MatchPhase.PREPARING)
    }
    fun startPreparing() = startPreparing(createdAt)

    private fun hasQuorumAndReadiness(): Boolean =
        Team.entries.all { team ->
            val players = roster.getValue(team)
            players.size >= config.quorum && players.all(ready::contains)
        }
    fun advanceTo(now: Instant) {
        require(!now.isBefore(lastAdvancedAt))
        lastAdvancedAt = now
        when (lifecycle.phase) {
            MatchPhase.PREPARING -> {
                val start = preparationStarted ?: return
                if (now >= start.plus(config.preparation) && hasQuorumAndReadiness()) {
                    lifecycle.transitionTo(MatchPhase.ACTIVE)
                    battleStarted = now
                    lastAdvancedAt = now
                }
            }
            MatchPhase.ACTIVE -> {
                processKegFuses(now)
                updateRallies(now)
                if (battleStarted != null && !now.isBefore(battleDeadline)) {
                    resolve(Team.B)
                    return
                }
                if (claimStarted != null && !now.isBefore(claimStarted!!.plus(config.claimCapture))) {
                    resolve(Team.A)
                    return
                }
                if (battleStarted != null && config.defenderWave > Duration.ZERO && config.suppliesPerWave > 0) {
                    val elapsed = Duration.between(battleStarted, now)
                    val waves = elapsed.toNanos() / config.defenderWave.toNanos()
                    if (waves > defenderWaveCount) {
                        attackerSupplies += (waves.toInt() - defenderWaveCount) * config.suppliesPerWave
                        defenderWaveCount = waves.toInt()
                    }
                }
            }
            else -> Unit
        }
    }

    private fun updateRallies(now: Instant) {
        RallyPoint.entries.forEach { point ->
            if (rallies.getValue(point)) return@forEach
            val teams = rallyPresence.getValue(point).mapNotNull(::teamOf).toSet()
            val previous = rallyStarted[point]
            val elapsed = previous?.let { Duration.between(it, now).coerceAtLeast(Duration.ZERO) } ?: Duration.ZERO
            rallyStarted[point] = now
            when {
                teams.size == 2 -> rallyContested += point
                teams.size == 1 && teams.single() == Team.A -> {
                    rallyContested.remove(point)
                    rallyProgress[point] = rallyProgress.getValue(point).plus(elapsed)
                    if (rallyProgress.getValue(point) >= config.rallyCapture) {
                        rallies[point] = true
                        rallyStarted.remove(point)
                    }
                }
                else -> {
                    rallyContested.remove(point)
                    if (previous != null && elapsed >= config.rallyDecayDelay) {
                        rallyProgress[point] = (rallyProgress.getValue(point) - elapsed).coerceAtLeast(Duration.ZERO)
                    }
                }
            }
        }
    }
    private fun processKegFuses(now: Instant) {
        kegs.values.filter { !it.destroyed && !it.disarmed && !now.isBefore(it.armedAt.plus(config.kegFuse)) }
            .sortedWith(compareBy<SiegeKeg> { it.armedAt }.thenBy { it.id })
            .forEach { keg ->
                val weapon = if (keg.owner == Team.A) SiegeWeapon.CANNON else SiegeWeapon.BALLISTA
                val player = roster[keg.owner].orEmpty().firstOrNull()
                kegs[keg.id] = keg.copy(destroyed = true)
                if (player != null) damageStructure(Structure.GATE, weapon, player, config.kegDamage, now)
            }
    }

    fun contestRally(point: RallyPoint, player: UUID) {
        check(teamOf(player) == Team.B)
        enterRally(point, player)
        if (!rallies.getValue(point)) {
            rallyProgress[point] = Duration.ZERO
            rallyStarted[point] = lastAdvancedAt
        }
    }
    fun rallyProgress(point: RallyPoint): Duration = rallyProgress.getValue(point)

    fun captureRally(point: RallyPoint, player: UUID, now: Instant): Boolean {
        check(lifecycle.phase == MatchPhase.ACTIVE)
        check(teamOf(player) == Team.A)
        rallyPresence.getValue(point).removeIf { teamOf(it) == Team.B }
        if (rallies.getValue(point)) return false
        if (point !in rallyCaptureStarted) {
            rallyCaptureStarted += point
            rallyStarted[point] = now
            lastAdvancedAt = now
            enterRally(point, player)
            return false
        }
        enterRally(point, player)
        val started = rallyStarted[point] ?: now
        val elapsed = Duration.between(started, now).coerceAtLeast(Duration.ZERO)
        rallyStarted[point] = now
        val teams = rallyPresence.getValue(point).mapNotNull(::teamOf).toSet()
        if (teams.size == 1 && teams.single() == Team.A) {
            rallyProgress[point] = rallyProgress.getValue(point).plus(elapsed)
            if (rallyProgress.getValue(point) >= config.rallyCapture) {
                rallies[point] = true
                rallyStarted.remove(point)
                lastAdvancedAt = now
                return true
            }
        }
        lastAdvancedAt = now
        return false
    }

    fun damageStructure(structure: Structure, weapon: SiegeWeapon, player: UUID, amount: Int = config.weapon(weapon).damage, now: Instant): Boolean {
        check(lifecycle.phase == MatchPhase.ACTIVE)
        val team = teamOf(player) ?: return false
        if (weapon.team != team || amount <= 0) return false
        if (structure == Structure.GATE && !allRalliesCaptured) return false
        val state = structures.getValue(structure)
        if (state.health == 0) return false
        if (!canUse(player, weapon, now)) return false
        structures[structure] = state.copy(health = max(0, state.health - amount))
        return true
    }
    fun repairStructure(structure: Structure, player: UUID, amount: Int = config.repairRate): Boolean {
        check(teamOf(player) == Team.B)
        if (attackerSupplies < config.repairCost || amount <= 0) return false
        val state = structures.getValue(structure)
        if (state.health >= state.maxHealth && manuallyGeneratedSupplies <= 0) return false
        attackerSupplies -= config.repairCost
        if (state.health < state.maxHealth) {
            structures[structure] = state.copy(health = min(state.maxHealth, state.health + amount))
        } else {
            manuallyGeneratedSupplies -= config.repairCost
        }
        return true
    }

    fun addContribution(player: UUID, amount: Int = 1) {
        check(amount >= 0)
        val old = stats(player)
        stats[player] = old.copy(contribution = old.contribution + amount, battleTokens = old.battleTokens + amount * config.contributionTokenRate)
    }

    fun generateSiegeSupplies(amount: Int = config.suppliesPerWave) {
        val generated = max(0, amount)
        attackerSupplies += generated
        manuallyGeneratedSupplies += generated
    }
    fun spendBattleTokens(player: UUID, amount: Int): Boolean {
        if (amount < 0) return false
        val old = stats(player)
        if (old.battleTokens < amount) return false
        stats[player] = old.copy(battleTokens = old.battleTokens - amount)
        return true
    }

    fun beginClaim(player: UUID, now: Instant): Boolean {
        check(teamOf(player) == Team.A)
        if (!allRalliesCaptured || structures.getValue(Structure.GATE).health > 0 || !now.isBefore(battleDeadline)) return false
        enterClaim(player)
        if (claimStarted == null) claimStarted = now
        lastAdvancedAt = now
        return true
    }

    private fun enterClaim(player: UUID) {
        check(teamOf(player) == Team.A)
        claimPresence += player
    }

    fun completeClaim(player: UUID, now: Instant): Boolean {
        if (!now.isBefore(battleDeadline)) {
            if (lifecycle.phase == MatchPhase.ACTIVE) lifecycle.transitionTo(MatchPhase.RESOLVING)
            if (resolvedResult == null) resolvedResult = MatchResult(Team.B)
            return false
        }
        if (teamOf(player) != Team.A || !allRalliesCaptured ||
            structures.getValue(Structure.GATE).health > 0 || player !in claimPresence
        ) return false
        val started = claimStarted ?: return false
        if (!now.isBefore(started.plus(config.claimCapture))) {
            resolve(Team.A)
            return true
        }
        return false
    }
    fun resolve(winner: Team?) {
        if (lifecycle.phase == MatchPhase.ACTIVE) lifecycle.transitionTo(MatchPhase.RESOLVING)
        if (resolvedResult == null) resolvedResult = MatchResult(winner)
        if (lifecycle.phase == MatchPhase.RESOLVING) lifecycle.transitionTo(MatchPhase.CLEANUP)
    }
    fun abort() {
        when (lifecycle.phase) {
            MatchPhase.WAITING -> return
            MatchPhase.PREPARING -> lifecycle.transitionTo(MatchPhase.CLEANUP)
            MatchPhase.ACTIVE -> resolve(null)
            else -> Unit
        }
    }

    fun armKeg(player: UUID, now: Instant): String {
        check(lifecycle.phase == MatchPhase.ACTIVE)
        val owner = teamOf(player) ?: error("Player is not in match")
        val id = "keg-${++nextKegId}"
        kegs[id] = SiegeKeg(id, owner, now)
        return id
    }
    fun disarmKeg(id: String, player: UUID): Boolean {
        val keg = kegs[id] ?: return false
        if (keg.owner != teamOf(player) || keg.destroyed || keg.disarmed) return false
        kegs[id] = keg.copy(disarmed = true)
        return true
    }

    fun destroyKeg(id: String, now: Instant): Boolean {
        val keg = kegs[id] ?: return false
        if (keg.destroyed || keg.disarmed) return false
        kegs[id] = keg.copy(destroyed = true)
        if (!now.isBefore(keg.armedAt.plus(config.kegFuse))) {
            val weapon = if (keg.owner == Team.A) SiegeWeapon.CANNON else SiegeWeapon.BALLISTA
            val player = roster[keg.owner].orEmpty().firstOrNull()
            if (player != null) damageStructure(Structure.GATE, weapon, player, config.kegDamage, now)
        }
        return true
    }
    fun cleanup() {
        kegs.clear()
        weaponUses.clear()
        weaponLastUse.clear()
        rallyPresence.values.forEach { it.clear() }
        claimPresence.clear()
        rallyCaptureStarted.clear()
        rallyStarted.clear()
        rallyContested.clear()
        rallyProgress.keys.forEach { rallyProgress[it] = Duration.ZERO }
        rallies.keys.forEach { rallies[it] = false }
        structures.keys.forEach {
            val health = config.structureHealth[it] ?: 100
            structures[it] = SiegeStructureState(health, health)
        }
        claimStarted = null
        preparationStarted = null
        battleStarted = null
        lastAdvancedAt = createdAt
        nextKegId = 0L
        manuallyGeneratedSupplies = 0
        attackerWaveCount = 0
        defenderWaveCount = 0
        attackerSupplies = 0
        if (lifecycle.phase == MatchPhase.RESOLVING) lifecycle.transitionTo(MatchPhase.CLEANUP)
        if (lifecycle.phase == MatchPhase.CLEANUP) {
            roster.values.forEach { it.clear() }
            ready.clear()
            stats.clear()
            resolvedResult = null
            lifecycle.transitionTo(MatchPhase.WAITING)
        }
    }

    private fun canUse(player: UUID, weapon: SiegeWeapon, now: Instant): Boolean {
        val pair = player to weapon
        val settings = config.weapon(weapon)
        val uses = weaponUses[pair] ?: 0
        if (uses >= settings.quota || uses >= settings.ammo) return false
        val last = weaponLastUse[pair]
        if (last != null && Duration.between(last, now) < settings.cooldown) return false
        weaponUses[pair] = uses + 1
        weaponLastUse[pair] = now
        return true
    }

    override fun handle(event: MatchEvent): List<MatchEvent> {
        advanceTo(createdAt.plusSeconds(event.tick))
        return emptyList()
    }
}

private fun Duration.coerceAtLeast(minimum: Duration): Duration = if (this < minimum) minimum else this
