package dev.jlo.gamemodes.domain.siege;

import java.time.Duration;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public final class SiegeConfig {
    private final Duration preparation, battle, rallyCapture, claimCapture, rallyDecayDelay, attackerWave, defenderWave;
    private final int quorum;
    private final Map<Structure,Integer> structureHealth;
    private final int repairRate, repairCost, suppliesPerWave, contributionTokenRate, mineDamage, kegDamage;
    private final Map<SiegeWeapon,SiegeWeaponConfig> weaponConfigs;
    private final Duration kegFuse;

    public SiegeConfig() { this(Duration.ofMinutes(5), Duration.ofMinutes(30), Duration.ofSeconds(30), Duration.ofSeconds(30), Duration.ofSeconds(15), Duration.ofSeconds(20), Duration.ofSeconds(20), 25, defaultHealth(), 10, 1, 10, 1, Collections.emptyMap(), 40, 80, Duration.ofSeconds(2)); }
    public SiegeConfig(Duration preparation, Duration battle, Duration rallyCapture, Duration claimCapture, Duration rallyDecayDelay, Duration attackerWave, Duration defenderWave, int quorum, Map<Structure,Integer> structureHealth, int repairRate, int repairCost, int suppliesPerWave, int contributionTokenRate, Map<SiegeWeapon,SiegeWeaponConfig> weaponConfigs, int mineDamage, int kegDamage, Duration kegFuse) {
        if (preparation.isNegative() || battle.isNegative() || rallyCapture.isNegative() || claimCapture.isNegative() || rallyDecayDelay.isNegative() || attackerWave.isNegative() || defenderWave.isNegative()) throw new IllegalArgumentException("durations must not be negative");
        if (quorum <= 0 || repairRate < 0 || repairCost < 0 || suppliesPerWave < 0 || contributionTokenRate < 0 || mineDamage < 0 || kegDamage < 0 || kegFuse.isNegative()) throw new IllegalArgumentException("invalid siege configuration");
        structureHealth.values().forEach(v -> { if (v < 0) throw new IllegalArgumentException("structure health must not be negative"); });
        this.preparation=preparation; this.battle=battle; this.rallyCapture=rallyCapture; this.claimCapture=claimCapture; this.rallyDecayDelay=rallyDecayDelay; this.attackerWave=attackerWave; this.defenderWave=defenderWave; this.quorum=quorum; this.structureHealth=Map.copyOf(structureHealth); this.repairRate=repairRate; this.repairCost=repairCost; this.suppliesPerWave=suppliesPerWave; this.contributionTokenRate=contributionTokenRate; this.weaponConfigs=Map.copyOf(weaponConfigs); this.mineDamage=mineDamage; this.kegDamage=kegDamage; this.kegFuse=kegFuse;
    }
    public SiegeConfig(int quorum) { this(Duration.ofMinutes(5), Duration.ofMinutes(30), Duration.ofSeconds(30), Duration.ofSeconds(30), Duration.ofSeconds(15), Duration.ofSeconds(20), Duration.ofSeconds(20), quorum, defaultHealth(), 10, 1, 10, 1, Collections.emptyMap(), 40, 80, Duration.ofSeconds(2)); }
    private static Map<Structure,Integer> defaultHealth() { EnumMap<Structure,Integer> m=new EnumMap<>(Structure.class); for (Structure s:Structure.values()) m.put(s,100); return m; }
    public Duration getPreparation(){return preparation;} public Duration getBattle(){return battle;} public Duration getRallyCapture(){return rallyCapture;} public Duration getClaimCapture(){return claimCapture;} public Duration getRallyDecayDelay(){return rallyDecayDelay;} public Duration getAttackerWave(){return attackerWave;} public Duration getDefenderWave(){return defenderWave;} public int getQuorum(){return quorum;} public Map<Structure,Integer> getStructureHealth(){return structureHealth;} public int getRepairRate(){return repairRate;} public int getRepairCost(){return repairCost;} public int getSuppliesPerWave(){return suppliesPerWave;} public int getContributionTokenRate(){return contributionTokenRate;} public Map<SiegeWeapon,SiegeWeaponConfig> getWeaponConfigs(){return weaponConfigs;} public int getMineDamage(){return mineDamage;} public int getKegDamage(){return kegDamage;} public Duration getKegFuse(){return kegFuse;}
    public SiegeWeaponConfig weapon(SiegeWeapon weapon){ return weaponConfigs.getOrDefault(weapon,new SiegeWeaponConfig(weapon.getQuota(),weapon.getAmmo(),weapon.getCooldown(),weapon.getDamage())); }
    public SiegeConfig copy(Duration preparation, Duration battle, Duration rallyCapture, Duration claimCapture, Duration rallyDecayDelay, Duration attackerWave, Duration defenderWave, int quorum, Map<Structure,Integer> structureHealth, int repairRate, int repairCost, int suppliesPerWave, int contributionTokenRate, Map<SiegeWeapon,SiegeWeaponConfig> weaponConfigs, int mineDamage, int kegDamage, Duration kegFuse){return new SiegeConfig(preparation,battle,rallyCapture,claimCapture,rallyDecayDelay,attackerWave,defenderWave,quorum,structureHealth,repairRate,repairCost,suppliesPerWave,contributionTokenRate,weaponConfigs,mineDamage,kegDamage,kegFuse);}
    @Override public boolean equals(Object o){return this==o||(o instanceof SiegeConfig x&&quorum==x.quorum&&repairRate==x.repairRate&&repairCost==x.repairCost&&suppliesPerWave==x.suppliesPerWave&&contributionTokenRate==x.contributionTokenRate&&mineDamage==x.mineDamage&&kegDamage==x.kegDamage&&Objects.equals(preparation,x.preparation)&&Objects.equals(battle,x.battle)&&Objects.equals(rallyCapture,x.rallyCapture)&&Objects.equals(claimCapture,x.claimCapture)&&Objects.equals(rallyDecayDelay,x.rallyDecayDelay)&&Objects.equals(attackerWave,x.attackerWave)&&Objects.equals(defenderWave,x.defenderWave)&&Objects.equals(structureHealth,x.structureHealth)&&Objects.equals(weaponConfigs,x.weaponConfigs)&&Objects.equals(kegFuse,x.kegFuse));}
    @Override public int hashCode(){return Objects.hash(preparation,battle,rallyCapture,claimCapture,rallyDecayDelay,attackerWave,defenderWave,quorum,structureHealth,repairRate,repairCost,suppliesPerWave,contributionTokenRate,weaponConfigs,mineDamage,kegDamage,kegFuse);}
    @Override public String toString(){return "SiegeConfig(preparation="+preparation+", battle="+battle+", quorum="+quorum+")";}
}
