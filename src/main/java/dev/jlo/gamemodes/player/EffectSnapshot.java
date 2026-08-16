package dev.jlo.gamemodes.player;

import java.util.Objects;

public final class EffectSnapshot {
    private final String type; private final int amplifier; private final int durationTicks; private final boolean ambient; private final boolean particles; private final boolean icon;
    public EffectSnapshot(String type, int amplifier, int durationTicks, boolean ambient, boolean particles, boolean icon) { this.type=type; this.amplifier=amplifier; this.durationTicks=durationTicks; this.ambient=ambient; this.particles=particles; this.icon=icon; }
    public String getType(){return type;} public int getAmplifier(){return amplifier;} public int getDurationTicks(){return durationTicks;} public boolean getAmbient(){return ambient;} public boolean getParticles(){return particles;} public boolean getIcon(){return icon;}
    public EffectSnapshot copy(String type,int amplifier,int durationTicks,boolean ambient,boolean particles,boolean icon){return new EffectSnapshot(type,amplifier,durationTicks,ambient,particles,icon);}
    @Override public boolean equals(Object o){if(this==o)return true;if(!(o instanceof EffectSnapshot e))return false;return amplifier==e.amplifier&&durationTicks==e.durationTicks&&ambient==e.ambient&&particles==e.particles&&icon==e.icon&&Objects.equals(type,e.type);}
    @Override public int hashCode(){return Objects.hash(type,amplifier,durationTicks,ambient,particles,icon);} @Override public String toString(){return "EffectSnapshot[type="+type+", amplifier="+amplifier+", durationTicks="+durationTicks+", ambient="+ambient+", particles="+particles+", icon="+icon+"]";}
}
