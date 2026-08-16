package dev.jlo.gamemodes.paper;

import dev.jlo.gamemodes.domain.common.MatchId;
import dev.jlo.gamemodes.domain.common.MatchPhase;
import dev.jlo.gamemodes.domain.common.MatchResult;
import dev.jlo.gamemodes.domain.common.Team;
import dev.jlo.gamemodes.domain.opr.OprConfig;
import dev.jlo.gamemodes.domain.opr.OprMatch;
import dev.jlo.gamemodes.domain.opr.OutpostId;
import dev.jlo.gamemodes.domain.siege.SiegeConfig;
import dev.jlo.gamemodes.domain.siege.SiegeMatch;
import dev.jlo.gamemodes.domain.siege.RallyPoint;
import dev.jlo.gamemodes.domain.siege.SiegeWeapon;
import dev.jlo.gamemodes.domain.siege.Structure;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Owns one arena reservation and adapts either deterministic rules engine to the Paper layer. */
public class MatchSession {
    private final Mode mode;
    private final String arenaId;
    private final String id;
    private final MatchQueue queue;
    private final SiegeConfig configuredSiege;
    private final Map<UUID, Team> teams = new LinkedHashMap<>();
    private final OprMatch opr;
    private final SiegeMatch siege;
    private final Clock clock;
    private Instant preparationStartedAt;
    private Instant battleStartedAt;

    public MatchSession(Mode mode, String arenaId, int capacityPerTeam, int quorumPerTeam) {
        this(mode, arenaId, capacityPerTeam, quorumPerTeam, Clock.systemUTC(),
                new OprConfig(capacityPerTeam, quorumPerTeam), new SiegeConfig(quorumPerTeam));
    }

    public MatchSession(Mode mode, String arenaId, int capacityPerTeam, int quorumPerTeam, Clock clock) {
        this(mode, arenaId, capacityPerTeam, quorumPerTeam, clock,
                new OprConfig(capacityPerTeam, quorumPerTeam), new SiegeConfig(quorumPerTeam));
    }

    public MatchSession(Mode mode, String arenaId, int capacityPerTeam, int quorumPerTeam,
                        Clock clock, OprConfig oprConfig, SiegeConfig siegeConfig) {
        this.mode = mode;
        this.arenaId = arenaId;
        this.clock = clock;
        this.id = mode + ":" + arenaId + ":" + UUID.randomUUID();
        this.queue = new MatchQueue(mode, capacityPerTeam, quorumPerTeam);
        this.configuredSiege = siegeConfig;
        Instant now = clock.instant();
        this.opr = mode == Mode.OPR ? new OprMatch(oprConfig, now) : null;
        this.siege = mode == Mode.SIEGE ? new SiegeMatch(new MatchId(id), siegeConfig, now) : null;
    }

    public Mode getMode() {
        return mode;
    }

    public String getArenaId() {
        return arenaId;
    }

    public String getId() {
        return id;
    }

    public MatchPhase getPhase() {
        return opr != null ? opr.getLifecycle().getPhase() : siege.getLifecycle().getPhase();
    }

    public MatchResult getResult() {
        return opr != null ? opr.getResult() : siege.getResult();
    }

    public synchronized TeamAssignment join(UUID player) {
        return join(player, null);
    }

    public synchronized TeamAssignment join(UUID player, TeamAssignment preferred) {
        Team team = queue.join(player, preferred == null ? null : preferred == TeamAssignment.A ? Team.A : Team.B);
        teams.put(player, team);
        if (opr != null) {
            opr.assign(player, team);
        } else {
            siege.assign(player, team);
        }
        return team == Team.A ? TeamAssignment.A : TeamAssignment.B;
    }

    public synchronized TeamAssignment leave(UUID player) {
        Team team = queue.leave(player);
        if (team == null) return null;
        teams.remove(player);
        if (opr != null) {
            opr.leave(player);
        } else {
            siege.leave(player);
        }
        return team == Team.A ? TeamAssignment.A : TeamAssignment.B;
    }

    public synchronized TeamAssignment teamOf(UUID player) {
        Team team = queue.teamOf(player);
        return team == null ? null : team == Team.A ? TeamAssignment.A : TeamAssignment.B;
    }

    public synchronized boolean ready(UUID player) {
        if (siege != null) {
            queue.markReady(player);
            siege.ready(player);
            return true;
        }
        return queue.markReady(player);
    }

    public synchronized boolean startIfReady() {
        MatchPhase phase = getPhase();
        if (phase == MatchPhase.RESOLVING || phase == MatchPhase.CLEANUP) return false;
        if (!queue.hasQuorum()) return false;
        Instant now = clock.instant();
        if (siege != null) {
            if (phase == MatchPhase.WAITING) {
                siege.startPreparing(now);
                preparationStartedAt = now;
            }
            if (getPhase() == MatchPhase.PREPARING && queue.allReady()) {
                siege.advanceTo(now);
                if (getPhase() == MatchPhase.PREPARING && configuredSiege.getPreparation().isZero()) {
                    siege.advanceTo(now.plusNanos(1));
                }
            }
        } else {
            if (phase == MatchPhase.WAITING) {
                opr.beginPreparation(now);
                preparationStartedAt = now;
            }
            if (getPhase() == MatchPhase.PREPARING) opr.start(now);
        }
        if (getPhase() == MatchPhase.ACTIVE && battleStartedAt == null) battleStartedAt = now;
        return getPhase() == MatchPhase.ACTIVE;
    }

    public synchronized void advance() {
        Instant now = clock.instant();
        if (opr != null) opr.advanceTo(now); else siege.advanceTo(now);
    }

    public void advanceAt(Instant at) {
        if (opr != null) opr.advanceTo(at); else siege.advanceTo(at);
    }

    public boolean captureRally(RallyPoint point, UUID player, Instant at) {
        return siege != null && siege.captureRally(point, player, at);
    }

    public boolean damageGate(UUID player, int amount, Instant at) {
        return siege != null && siege.damageStructure(Structure.GATE, SiegeWeapon.CANNON, player, amount, at);
    }

    public boolean beginClaim(UUID player, Instant at) {
        return siege != null && siege.beginClaim(player, at);
    }

    public boolean completeClaim(UUID player, Instant at) {
        return siege != null && siege.completeClaim(player, at);
    }

    public void enterOutpost(UUID player, OutpostId outpost) {
        if (opr != null) opr.enterOutpost(player, outpost);
    }

    public void disconnect(UUID player) {
        if (opr != null) opr.disconnect(player, clock.instant());
        else if (siege != null) siege.disconnect(player);
    }

    public boolean recordCombat(UUID attacker, UUID victim) {
        return opr != null && opr.recordCombat(attacker, victim, clock.instant());
    }

    public boolean playerDied(UUID player) {
        return playerDied(player, null);
    }

    public boolean playerDied(UUID player, UUID killer) {
        if (getPhase() != MatchPhase.ACTIVE || teamOf(player) == null) return false;
        TeamAssignment killerTeam = killer == null ? null : teamOf(killer);
        TeamAssignment playerTeam = teamOf(player);
        UUID sameSessionEnemy = killerTeam != null && killerTeam != playerTeam ? killer : null;
        if (opr != null) return opr.recordDeath(player, sameSessionEnemy, clock.instant());
        siege.recordDeath(player, sameSessionEnemy);
        return sameSessionEnemy != null;
    }

    public Location respawnLocation(Player player) {
        return null;
    }

    public void reconcileObjective(UUID player, Location location) {
        if (opr == null || getPhase() != MatchPhase.ACTIVE) return;
        OutpostId[] entries = OutpostId.values();
        int index = Math.floorMod(location.getBlockX() + location.getBlockZ(), entries.length);
        OutpostId objective = entries[index];
        for (OutpostId outpost : entries) {
            if (outpost != objective) opr.leaveOutpost(player, outpost);
        }
        opr.enterOutpost(player, objective);
    }

    public void recordKill(UUID killer, UUID victim) {
        if (opr != null) opr.recordKill(killer, victim, clock.instant());
    }

    public synchronized void cleanup() {
        if (opr != null) {
            if (getPhase() != MatchPhase.WAITING) opr.resolveAndCleanup(clock.instant());
        } else if (getPhase() != MatchPhase.WAITING) {
            siege.abort();
            siege.cleanup();
        }
        for (UUID player : teams.keySet().toArray(UUID[]::new)) queue.leave(player);
        teams.clear();
    }
}
