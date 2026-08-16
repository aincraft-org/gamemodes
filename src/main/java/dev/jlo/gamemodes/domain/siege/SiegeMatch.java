package dev.jlo.gamemodes.domain.siege;

import dev.jlo.gamemodes.domain.common.Match;
import dev.jlo.gamemodes.domain.common.MatchEvent;
import dev.jlo.gamemodes.domain.common.MatchId;
import dev.jlo.gamemodes.domain.common.MatchLifecycle;
import dev.jlo.gamemodes.domain.common.MatchPhase;
import dev.jlo.gamemodes.domain.common.MatchResult;
import dev.jlo.gamemodes.domain.common.Team;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SiegeMatch implements Match {
    private final MatchId id;
    private final SiegeConfig config;
    private final Instant createdAt;
    private final MatchLifecycle lifecycle = new MatchLifecycle(MatchPhase.WAITING);
    private final Map<Team, LinkedHashSet<UUID>> roster = new EnumMap<>(Team.class);
    private final LinkedHashSet<UUID> ready = new LinkedHashSet<>();
    private final Map<RallyPoint, Boolean> rallies = new EnumMap<>(RallyPoint.class);
    private final Map<RallyPoint, Duration> rallyProgress = new EnumMap<>(RallyPoint.class);
    private final Map<RallyPoint, LinkedHashSet<UUID>> rallyPresence = new EnumMap<>(RallyPoint.class);
    private final Set<RallyPoint> rallyContested = EnumSet.noneOf(RallyPoint.class);
    private final Map<RallyPoint, Instant> rallyStarted = new EnumMap<>(RallyPoint.class);
    private final Set<RallyPoint> rallyCaptureStarted = EnumSet.noneOf(RallyPoint.class);
    private final LinkedHashSet<UUID> claimPresence = new LinkedHashSet<>();
    private final Map<Structure, SiegeStructureState> structures = new EnumMap<>(Structure.class);
    private final Map<UUID, SiegePlayerStats> stats = new LinkedHashMap<>();
    private final Map<WeaponUse, Integer> weaponUses = new LinkedHashMap<>();
    private final Map<WeaponUse, Instant> weaponLastUse = new LinkedHashMap<>();
    private final Map<String, SiegeKeg> kegs = new LinkedHashMap<>();
    private Instant claimStarted;
    private Instant preparationStarted;
    private Instant battleStarted;
    private Instant lastAdvancedAt;
    private MatchResult resolvedResult;
    private long nextKegId;
    private int manuallyGeneratedSupplies;
    private int defenderWaveCount;
    private int attackerSupplies;

    public SiegeMatch(MatchId id) { this(id, new SiegeConfig(), Instant.now()); }
    public SiegeMatch(MatchId id, SiegeConfig config) { this(id, config, Instant.now()); }
    public SiegeMatch(MatchId id, SiegeConfig config, Instant createdAt) {
        this.id = id;
        this.config = config;
        this.createdAt = createdAt;
        this.lastAdvancedAt = createdAt;
        for (Team team : Team.values()) roster.put(team, new LinkedHashSet<>());
        for (RallyPoint point : RallyPoint.values()) {
            rallies.put(point, false);
            rallyProgress.put(point, Duration.ZERO);
            rallyPresence.put(point, new LinkedHashSet<>());
        }
        for (Structure structure : Structure.values()) {
            int health = config.getStructureHealth().getOrDefault(structure, 100);
            structures.put(structure, new SiegeStructureState(health, health));
        }
    }

    @Override public MatchId getId() { return id; }
    @Override public MatchLifecycle getLifecycle() { return lifecycle; }
    public MatchResult getResult() { return resolvedResult; }
    public Instant getStart() { return battleStarted; }
    public String getMode() { return "siege"; }
    public SiegeConfig getConfig() { return config; }
    public Map<Team, Set<UUID>> getRoster() { Map<Team, Set<UUID>> out = new EnumMap<>(Team.class); for (var e : roster.entrySet()) out.put(e.getKey(), Set.copyOf(e.getValue())); return Collections.unmodifiableMap(out); }
    public Set<Team> getTeams() { return Set.copyOf(roster.keySet()); }
    public Map<RallyPoint, Boolean> getRallyPoints() { return Collections.unmodifiableMap(new EnumMap<>(rallies)); }
    public Map<Structure, SiegeStructureState> getStructures() { return Collections.unmodifiableMap(new EnumMap<>(structures)); }
    public Map<String, SiegeKeg> getKegs() { return Collections.unmodifiableMap(new LinkedHashMap<>(kegs)); }
    public Map<UUID, SiegePlayerStats> getStats() { return Collections.unmodifiableMap(new LinkedHashMap<>(stats)); }
    public Instant getBattleDeadline() { return (battleStarted == null ? createdAt : battleStarted).plus(config.getBattle()); }
    public boolean isAllRalliesCaptured() { return rallies.values().stream().allMatch(Boolean::booleanValue); }
    public Duration getClaimProgress() { return claimStarted == null ? Duration.ZERO : coerceAtLeast(Duration.between(claimStarted, lastAdvancedAt), Duration.ZERO); }
    public Map<Structure, SiegeStructureState> getStructuresState() { return getStructures(); }
    public Set<UUID> neutralPresence(RallyPoint point) { return Set.copyOf(rallyPresence.get(point)); }

    public Team join(UUID player) {
        requireWaitingOrPreparing();
        if (teamOf(player) != null) throw new IllegalStateException("Player is already in this match");
        Team selected = Team.A;
        for (Team team : Team.values()) if (roster.get(team).size() < roster.get(selected).size()) selected = team;
        if (roster.get(selected).size() >= config.getQuorum() * 2) throw new IllegalStateException("Roster is full");
        roster.get(selected).add(player); stats.putIfAbsent(player, new SiegePlayerStats()); return selected;
    }
    public void assign(UUID player, Team team) {
        requireWaitingOrPreparing();
        if (teamOf(player) != null) throw new IllegalStateException("Player is already in this match");
        if (roster.get(team).size() >= config.getQuorum() * 2) throw new IllegalStateException("Roster is full");
        roster.get(team).add(player); stats.putIfAbsent(player, new SiegePlayerStats());
    }
    public Team leave(UUID player) {
        Team found = null; for (Team team : Team.values()) if (roster.get(team).remove(player)) { found = team; break; }
        ready.remove(player); for (Set<UUID> p : rallyPresence.values()) p.remove(player); claimPresence.remove(player); return found;
    }
    public void disconnect(UUID player) { if (teamOf(player) == null) throw new IllegalStateException("Player is not in match"); for (Set<UUID> p : rallyPresence.values()) p.remove(player); claimPresence.remove(player); }
    public void recordDeath(UUID victim, UUID killer) {
        if (lifecycle.getPhase() != MatchPhase.ACTIVE) throw new IllegalStateException("Match is not active");
        Team victimTeam = teamOf(victim); if (victimTeam == null) throw new IllegalStateException("Player is not in match");
        SiegePlayerStats old = stats(victim); stats.put(victim, old.copy(old.getKills(), old.getDeaths()+1, old.getContribution(), old.getBattleTokens()));
        if (killer != null && teamOf(killer) != null && teamOf(killer) != victimTeam) { old = stats(killer); stats.put(killer, old.copy(old.getKills()+1, old.getDeaths(), old.getContribution()+1, old.getBattleTokens()+config.getContributionTokenRate())); }
    }
    public Team teamOf(UUID player) { for (var e : roster.entrySet()) if (e.getValue().contains(player)) return e.getKey(); return null; }
    public Set<UUID> players(Team team) { return Set.copyOf(roster.get(team)); }
    public SiegePlayerStats stats(UUID player) { return stats.getOrDefault(player, new SiegePlayerStats()); }
    public void ready(UUID player) { if (teamOf(player) == null) throw new IllegalStateException("Player is not in match"); ready.add(player); }
    public void enterRally(RallyPoint point, UUID player) { if (teamOf(player) == null) throw new IllegalStateException("Player is not in match"); rallyPresence.get(point).add(player); }
    public void leaveRally(RallyPoint point, UUID player) { rallyPresence.get(point).remove(player); }
    public void startPreparing(Instant at) { if (lifecycle.getPhase()!=MatchPhase.WAITING) throw new IllegalStateException(); if (roster.values().stream().allMatch(Set::isEmpty)) throw new IllegalStateException("At least one roster is required"); preparationStarted=at; lastAdvancedAt=at; lifecycle.transitionTo(MatchPhase.PREPARING); }
    public void startPreparing() { startPreparing(createdAt); }
    private boolean hasQuorumAndReadiness() { for (Team team: Team.values()) if (roster.get(team).size()<config.getQuorum() || !ready.containsAll(roster.get(team))) return false; return true; }
    public void advanceTo(Instant now) {
        if (now.isBefore(lastAdvancedAt)) throw new IllegalArgumentException("time must not move backwards"); lastAdvancedAt=now;
        if (lifecycle.getPhase()==MatchPhase.PREPARING) { if (preparationStarted != null && !now.isBefore(preparationStarted.plus(config.getPreparation())) && hasQuorumAndReadiness()) { lifecycle.transitionTo(MatchPhase.ACTIVE); battleStarted=now; lastAdvancedAt=now; } }
        else if (lifecycle.getPhase()==MatchPhase.ACTIVE) { processKegFuses(now); updateRallies(now); if (!now.isBefore(getBattleDeadline())) { resolve(Team.B); return; } if (claimStarted != null && !now.isBefore(claimStarted.plus(config.getClaimCapture()))) { resolve(Team.A); return; } if (!config.getDefenderWave().isZero() && config.getSuppliesPerWave()>0) { long waves=Duration.between(battleStarted,now).toNanos()/config.getDefenderWave().toNanos(); if (waves>defenderWaveCount) { attackerSupplies += (int)(waves-defenderWaveCount)*config.getSuppliesPerWave(); defenderWaveCount=(int)waves; } } }
    }
    private void updateRallies(Instant now) { for (RallyPoint point:RallyPoint.values()) { if (rallies.get(point)) continue; Set<Team> teams=new LinkedHashSet<>(); for(UUID p:rallyPresence.get(point)){Team t=teamOf(p);if(t!=null)teams.add(t);} Instant previous=rallyStarted.get(point); Duration elapsed=previous==null?Duration.ZERO:coerceAtLeast(Duration.between(previous,now),Duration.ZERO); rallyStarted.put(point,now); if(teams.size()==2) rallyContested.add(point); else if(teams.size()==1&&teams.contains(Team.A)){rallyContested.remove(point); rallyProgress.put(point,rallyProgress.get(point).plus(elapsed)); if(rallyProgress.get(point).compareTo(config.getRallyCapture())>=0){rallies.put(point,true);rallyStarted.remove(point);}} else {rallyContested.remove(point); if(previous!=null&&elapsed.compareTo(config.getRallyDecayDelay())>=0) rallyProgress.put(point,coerceAtLeast(rallyProgress.get(point).minus(elapsed),Duration.ZERO));}} }
    private void processKegFuses(Instant now) { List<SiegeKeg> due=new ArrayList<>(); for(SiegeKeg k:kegs.values()) if(!k.getDestroyed()&&!k.getDisarmed()&&!now.isBefore(k.getArmedAt().plus(config.getKegFuse()))) due.add(k); due.sort((a,b)->{int c=a.getArmedAt().compareTo(b.getArmedAt());return c!=0?c:a.getId().compareTo(b.getId());}); for(SiegeKeg k:due){Team owner=k.getOwner();UUID p=roster.get(owner).stream().findFirst().orElse(null);kegs.put(k.getId(),k.copy(k.getId(),k.getOwner(),k.getArmedAt(),true,k.getDisarmed()));if(p!=null)damageStructure(Structure.GATE,owner==Team.A?SiegeWeapon.CANNON:SiegeWeapon.BALLISTA,p,config.getKegDamage(),now);} }
    public boolean captureRally(RallyPoint point, UUID player, Instant now) { if(lifecycle.getPhase()!=MatchPhase.ACTIVE||teamOf(player)!=Team.A) throw new IllegalStateException(); rallyPresence.get(point).removeIf(p->teamOf(p)==Team.B); if(rallies.get(point))return false; if(!rallyCaptureStarted.contains(point)){rallyCaptureStarted.add(point);rallyStarted.put(point,now);lastAdvancedAt=now;enterRally(point,player);return false;} enterRally(point,player);Instant started=rallyStarted.getOrDefault(point,now);Duration elapsed=coerceAtLeast(Duration.between(started,now),Duration.ZERO);rallyStarted.put(point,now);Set<Team> teams=new LinkedHashSet<>();for(UUID p:rallyPresence.get(point)){Team t=teamOf(p);if(t!=null)teams.add(t);}if(teams.size()==1&&teams.contains(Team.A)){rallyProgress.put(point,rallyProgress.get(point).plus(elapsed));if(rallyProgress.get(point).compareTo(config.getRallyCapture())>=0){rallies.put(point,true);rallyStarted.remove(point);lastAdvancedAt=now;return true;}}lastAdvancedAt=now;return false; }
    public boolean damageStructure(Structure structure, SiegeWeapon weapon, UUID player, int amount, Instant now) { if(lifecycle.getPhase()!=MatchPhase.ACTIVE)throw new IllegalStateException();Team team=teamOf(player);if(team==null||weapon.getTeam()!=team||amount<=0||(structure==Structure.GATE&&!isAllRalliesCaptured()))return false;SiegeStructureState state=structures.get(structure);if(state.getHealth()==0||!canUse(player,weapon,now))return false;structures.put(structure,state.copy(Math.max(0,state.getHealth()-amount),state.getMaxHealth()));return true; }
    public boolean damageStructure(Structure s, SiegeWeapon w, UUID p, Instant n) { return damageStructure(s,w,p,config.weapon(w).getDamage(),n); }
    public boolean beginClaim(UUID player, Instant now) { if(teamOf(player)!=Team.A)throw new IllegalStateException();if(!isAllRalliesCaptured()||structures.get(Structure.GATE).getHealth()>0||!now.isBefore(getBattleDeadline()))return false;claimPresence.add(player);if(claimStarted==null)claimStarted=now;lastAdvancedAt=now;return true; }
    public boolean completeClaim(UUID player, Instant now) { if(!now.isBefore(getBattleDeadline())){if(lifecycle.getPhase()==MatchPhase.ACTIVE)lifecycle.transitionTo(MatchPhase.RESOLVING);if(resolvedResult==null)resolvedResult=new MatchResult(Team.B);return false;}if(teamOf(player)!=Team.A||!isAllRalliesCaptured()||structures.get(Structure.GATE).getHealth()>0||!claimPresence.contains(player)||claimStarted==null)return false;if(!now.isBefore(claimStarted.plus(config.getClaimCapture()))){resolve(Team.A);return true;}return false; }
    public void resolve(Team winner) { if(lifecycle.getPhase()==MatchPhase.ACTIVE)lifecycle.transitionTo(MatchPhase.RESOLVING);if(resolvedResult==null)resolvedResult=new MatchResult(winner);if(lifecycle.getPhase()==MatchPhase.RESOLVING)lifecycle.transitionTo(MatchPhase.CLEANUP); }
    public void abort() { switch(lifecycle.getPhase()){case WAITING->{}case PREPARING->lifecycle.transitionTo(MatchPhase.CLEANUP);case ACTIVE->resolve(null);default->{}} }
    public String armKeg(UUID player, Instant now) { if(lifecycle.getPhase()!=MatchPhase.ACTIVE)throw new IllegalStateException();Team owner=teamOf(player);if(owner==null)throw new IllegalStateException("Player is not in match");String id="keg-"+(++nextKegId);kegs.put(id,new SiegeKeg(id,owner,now,false,false));return id; }
    public boolean disarmKeg(String id, UUID player) { SiegeKeg k=kegs.get(id);if(k==null||k.getOwner()!=teamOf(player)||k.getDestroyed()||k.getDisarmed())return false;kegs.put(id,k.copy(k.getId(),k.getOwner(),k.getArmedAt(),k.getDestroyed(),true));return true; }
    public boolean destroyKeg(String id, Instant now) { SiegeKeg k=kegs.get(id);if(k==null||k.getDestroyed()||k.getDisarmed())return false;kegs.put(id,k.copy(k.getId(),k.getOwner(),k.getArmedAt(),true,k.getDisarmed()));if(!now.isBefore(k.getArmedAt().plus(config.getKegFuse()))){UUID p=roster.get(k.getOwner()).stream().findFirst().orElse(null);if(p!=null)damageStructure(Structure.GATE,k.getOwner()==Team.A?SiegeWeapon.CANNON:SiegeWeapon.BALLISTA,p,config.getKegDamage(),now);}return true; }
    public void cleanup() { kegs.clear();weaponUses.clear();weaponLastUse.clear();rallyPresence.values().forEach(Set::clear);claimPresence.clear();rallyCaptureStarted.clear();rallyStarted.clear();rallyContested.clear();for(RallyPoint p:RallyPoint.values()){rallyProgress.put(p,Duration.ZERO);rallies.put(p,false);}for(Structure s:Structure.values()){int h=config.getStructureHealth().getOrDefault(s,100);structures.put(s,new SiegeStructureState(h,h));}claimStarted=null;preparationStarted=null;battleStarted=null;lastAdvancedAt=createdAt;nextKegId=0;manuallyGeneratedSupplies=0;defenderWaveCount=0;attackerSupplies=0;if(lifecycle.getPhase()==MatchPhase.RESOLVING)lifecycle.transitionTo(MatchPhase.CLEANUP);if(lifecycle.getPhase()==MatchPhase.CLEANUP){roster.values().forEach(Set::clear);ready.clear();stats.clear();resolvedResult=null;lifecycle.transitionTo(MatchPhase.WAITING);} }
    public void contestRally(RallyPoint point,UUID player){if(teamOf(player)!=Team.B)throw new IllegalStateException();enterRally(point,player);if(!rallies.get(point)){rallyProgress.put(point,Duration.ZERO);rallyStarted.put(point,lastAdvancedAt);}}
    public Duration rallyProgress(RallyPoint point){return rallyProgress.get(point);}
    public boolean repairStructure(Structure s,UUID p,int amount){if(teamOf(p)!=Team.B)throw new IllegalStateException();if(attackerSupplies<config.getRepairCost()||amount<=0)return false;SiegeStructureState st=structures.get(s);if(st.getHealth()>=st.getMaxHealth()&&manuallyGeneratedSupplies<=0)return false;attackerSupplies-=config.getRepairCost();if(st.getHealth()<st.getMaxHealth())structures.put(s,st.copy(Math.min(st.getMaxHealth(),st.getHealth()+amount),st.getMaxHealth()));else manuallyGeneratedSupplies-=config.getRepairCost();return true;}
    public boolean repairStructure(Structure s,UUID p){return repairStructure(s,p,config.getRepairRate());}
    public void addContribution(UUID p,int amount){if(amount<0)throw new IllegalArgumentException();SiegePlayerStats o=stats(p);stats.put(p,o.copy(o.getKills(),o.getDeaths(),o.getContribution()+amount,o.getBattleTokens()+amount*config.getContributionTokenRate()));}
    public void generateSiegeSupplies(int amount){int generated=Math.max(0,amount);attackerSupplies+=generated;manuallyGeneratedSupplies+=generated;}
    public void generateSiegeSupplies(){generateSiegeSupplies(config.getSuppliesPerWave());}
    public boolean spendBattleTokens(UUID p,int amount){if(amount<0)return false;SiegePlayerStats o=stats(p);if(o.getBattleTokens()<amount)return false;stats.put(p,o.copy(o.getKills(),o.getDeaths(),o.getContribution(),o.getBattleTokens()-amount));return true;}
    private boolean canUse(UUID p,SiegeWeapon w,Instant now){WeaponUse key=new WeaponUse(p,w);SiegeWeaponConfig settings=config.weapon(w);int uses=weaponUses.getOrDefault(key,0);if(uses>=settings.getQuota()||uses>=settings.getAmmo())return false;Instant last=weaponLastUse.get(key);if(last!=null&&Duration.between(last,now).compareTo(settings.getCooldown())<0)return false;weaponUses.put(key,uses+1);weaponLastUse.put(key,now);return true;}
    @Override public List<MatchEvent> handle(MatchEvent event){advanceTo(createdAt.plusSeconds(event.getTick()));return List.of();}
    private void requireWaitingOrPreparing(){if(lifecycle.getPhase()!=MatchPhase.WAITING&&lifecycle.getPhase()!=MatchPhase.PREPARING)throw new IllegalStateException();}
    private static Duration coerceAtLeast(Duration a,Duration b){return a.compareTo(b)<0?b:a;}
    private record WeaponUse(UUID player, SiegeWeapon weapon) {}
}
