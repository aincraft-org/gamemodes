package dev.jlo.gamemodes.domain.opr;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

public final class OprConfig {
    private final int teamCapacity, quorumPerTeam, targetScore, gateTierTwoCost, commandPostTierTwoCost,
            protectionWardCost, repairCost, summonCapPerTeam;
    private final Duration captureDuration, captureDecayDelay, scoreInterval, victimCooldown, spawnProtection,
            combatWindow, matchDuration, disconnectReservation, suddenDeathDuration, baronessInterval,
            portalInterval, waveInterval;

    public OprConfig() {
        this(20, 10, Duration.ofSeconds(30), Duration.ofSeconds(15), Duration.ofSeconds(3), Duration.ofSeconds(60),
                Duration.ofSeconds(10), Duration.ofSeconds(30), 1000, Duration.ofMinutes(30), Duration.ofMinutes(5),
                Duration.ofSeconds(90), Duration.ofMinutes(10), Duration.ofMinutes(5), 25, 20, 30, 10, 6,
                Duration.ofSeconds(10));
    }
    public OprConfig(int teamCapacity, int quorumPerTeam) {
        this(teamCapacity, quorumPerTeam, Duration.ofSeconds(30), Duration.ofSeconds(15), Duration.ofSeconds(3), Duration.ofSeconds(60),
                Duration.ofSeconds(10), Duration.ofSeconds(30), 1000, Duration.ofMinutes(30), Duration.ofMinutes(5),
                Duration.ofSeconds(90), Duration.ofMinutes(10), Duration.ofMinutes(5), 25, 20, 30, 10, 6,
                Duration.ofSeconds(10));
    }

    public OprConfig(int teamCapacity, int quorumPerTeam, Duration captureDuration, Duration captureDecayDelay,
                     Duration scoreInterval, Duration victimCooldown, Duration spawnProtection, Duration combatWindow,
                     int targetScore, Duration matchDuration, Duration disconnectReservation, Duration suddenDeathDuration,
                     Duration baronessInterval, Duration portalInterval, int gateTierTwoCost, int commandPostTierTwoCost,
                     int protectionWardCost, int repairCost, int summonCapPerTeam, Duration waveInterval) {
        if (teamCapacity <= 0 || quorumPerTeam <= 0 || quorumPerTeam > teamCapacity) throw new IllegalArgumentException();
        for (Duration d : List.of(captureDuration, captureDecayDelay, scoreInterval, victimCooldown, spawnProtection,
                combatWindow, matchDuration, disconnectReservation, suddenDeathDuration, baronessInterval, portalInterval, waveInterval))
            if (d == null || d.isNegative() || d.isZero()) throw new IllegalArgumentException();
        if (targetScore <= 0 || gateTierTwoCost < 0 || commandPostTierTwoCost < 0 || protectionWardCost < 0 || repairCost < 0 || summonCapPerTeam <= 0)
            throw new IllegalArgumentException();
        this.teamCapacity = teamCapacity; this.quorumPerTeam = quorumPerTeam; this.captureDuration = captureDuration;
        this.captureDecayDelay = captureDecayDelay; this.scoreInterval = scoreInterval; this.victimCooldown = victimCooldown;
        this.spawnProtection = spawnProtection; this.combatWindow = combatWindow; this.targetScore = targetScore;
        this.matchDuration = matchDuration; this.disconnectReservation = disconnectReservation; this.suddenDeathDuration = suddenDeathDuration;
        this.baronessInterval = baronessInterval; this.portalInterval = portalInterval; this.gateTierTwoCost = gateTierTwoCost;
        this.commandPostTierTwoCost = commandPostTierTwoCost; this.protectionWardCost = protectionWardCost; this.repairCost = repairCost;
        this.summonCapPerTeam = summonCapPerTeam; this.waveInterval = waveInterval;
    }

    public int getTeamCapacity(){return teamCapacity;} public int getQuorumPerTeam(){return quorumPerTeam;}
    public Duration getCaptureDuration(){return captureDuration;} public Duration getCaptureDecayDelay(){return captureDecayDelay;}
    public Duration getScoreInterval(){return scoreInterval;} public Duration getVictimCooldown(){return victimCooldown;}
    public Duration getSpawnProtection(){return spawnProtection;} public Duration getCombatWindow(){return combatWindow;}
    public int getTargetScore(){return targetScore;} public Duration getMatchDuration(){return matchDuration;}
    public Duration getDisconnectReservation(){return disconnectReservation;} public Duration getSuddenDeathDuration(){return suddenDeathDuration;}
    public Duration getBaronessInterval(){return baronessInterval;} public Duration getPortalInterval(){return portalInterval;}
    public int getGateTierTwoCost(){return gateTierTwoCost;} public int getCommandPostTierTwoCost(){return commandPostTierTwoCost;}
    public int getProtectionWardCost(){return protectionWardCost;} public int getRepairCost(){return repairCost;}
    public int getSummonCapPerTeam(){return summonCapPerTeam;} public Duration getWaveInterval(){return waveInterval;}
    public OprConfig copy(int teamCapacity,int quorumPerTeam,Duration captureDuration,Duration captureDecayDelay,Duration scoreInterval,Duration victimCooldown,Duration spawnProtection,Duration combatWindow,int targetScore,Duration matchDuration,Duration disconnectReservation,Duration suddenDeathDuration,Duration baronessInterval,Duration portalInterval,int gateTierTwoCost,int commandPostTierTwoCost,int protectionWardCost,int repairCost,int summonCapPerTeam,Duration waveInterval){return new OprConfig(teamCapacity,quorumPerTeam,captureDuration,captureDecayDelay,scoreInterval,victimCooldown,spawnProtection,combatWindow,targetScore,matchDuration,disconnectReservation,suddenDeathDuration,baronessInterval,portalInterval,gateTierTwoCost,commandPostTierTwoCost,protectionWardCost,repairCost,summonCapPerTeam,waveInterval);}
    @Override public boolean equals(Object o){if(this==o)return true; if(!(o instanceof OprConfig x))return false; return teamCapacity==x.teamCapacity&&quorumPerTeam==x.quorumPerTeam&&targetScore==x.targetScore&&gateTierTwoCost==x.gateTierTwoCost&&commandPostTierTwoCost==x.commandPostTierTwoCost&&protectionWardCost==x.protectionWardCost&&repairCost==x.repairCost&&summonCapPerTeam==x.summonCapPerTeam&&Objects.equals(captureDuration,x.captureDuration)&&Objects.equals(captureDecayDelay,x.captureDecayDelay)&&Objects.equals(scoreInterval,x.scoreInterval)&&Objects.equals(victimCooldown,x.victimCooldown)&&Objects.equals(spawnProtection,x.spawnProtection)&&Objects.equals(combatWindow,x.combatWindow)&&Objects.equals(matchDuration,x.matchDuration)&&Objects.equals(disconnectReservation,x.disconnectReservation)&&Objects.equals(suddenDeathDuration,x.suddenDeathDuration)&&Objects.equals(baronessInterval,x.baronessInterval)&&Objects.equals(portalInterval,x.portalInterval)&&Objects.equals(waveInterval,x.waveInterval);}
    @Override public int hashCode(){return Objects.hash(teamCapacity,quorumPerTeam,captureDuration,captureDecayDelay,scoreInterval,victimCooldown,spawnProtection,combatWindow,targetScore,matchDuration,disconnectReservation,suddenDeathDuration,baronessInterval,portalInterval,gateTierTwoCost,commandPostTierTwoCost,protectionWardCost,repairCost,summonCapPerTeam,waveInterval);}
    @Override public String toString(){return "OprConfig(teamCapacity="+teamCapacity+", quorumPerTeam="+quorumPerTeam+")";}
}
