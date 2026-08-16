package dev.jlo.gamemodes.player;

import java.util.Objects;

public final class LocationSnapshot {
    private final String world; private final double x,y,z; private final float yaw,pitch;
    public LocationSnapshot(String world,double x,double y,double z,float yaw,float pitch){this.world=world;this.x=x;this.y=y;this.z=z;this.yaw=yaw;this.pitch=pitch;}
    public String getWorld(){return world;} public double getX(){return x;} public double getY(){return y;} public double getZ(){return z;} public float getYaw(){return yaw;} public float getPitch(){return pitch;}
    public LocationSnapshot copy(String world,double x,double y,double z,float yaw,float pitch){return new LocationSnapshot(world,x,y,z,yaw,pitch);}
    @Override public boolean equals(Object o){if(this==o)return true;if(!(o instanceof LocationSnapshot l))return false;return Double.compare(x,l.x)==0&&Double.compare(y,l.y)==0&&Double.compare(z,l.z)==0&&Float.compare(yaw,l.yaw)==0&&Float.compare(pitch,l.pitch)==0&&Objects.equals(world,l.world);}
    @Override public int hashCode(){return Objects.hash(world,x,y,z,yaw,pitch);} @Override public String toString(){return "LocationSnapshot[world="+world+", x="+x+", y="+y+", z="+z+", yaw="+yaw+", pitch="+pitch+"]";}
}
