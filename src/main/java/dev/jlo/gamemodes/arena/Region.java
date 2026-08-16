package dev.jlo.gamemodes.arena;

public final class Region {
    private final int minX,minY,minZ,maxX,maxY,maxZ;
    public Region(int minX,int minY,int minZ,int maxX,int maxY,int maxZ){if(minX>maxX||minY>maxY||minZ>maxZ)throw new IllegalArgumentException("Region bounds must not be inverted");this.minX=minX;this.minY=minY;this.minZ=minZ;this.maxX=maxX;this.maxY=maxY;this.maxZ=maxZ;}
    public int getMinX(){return minX;} public int getMinY(){return minY;} public int getMinZ(){return minZ;} public int getMaxX(){return maxX;} public int getMaxY(){return maxY;} public int getMaxZ(){return maxZ;}
    public boolean contains(BlockPosition p){return p.getX()>=minX&&p.getX()<=maxX&&p.getY()>=minY&&p.getY()<=maxY&&p.getZ()>=minZ&&p.getZ()<=maxZ;}
    public boolean overlaps(Region o){return minX<=o.maxX&&maxX>=o.minX&&minY<=o.maxY&&maxY>=o.minY&&minZ<=o.maxZ&&maxZ>=o.minZ;}
    @Override public boolean equals(Object o){return o instanceof Region r&&minX==r.minX&&minY==r.minY&&minZ==r.minZ&&maxX==r.maxX&&maxY==r.maxY&&maxZ==r.maxZ;}
    @Override public int hashCode(){return java.util.Objects.hash(minX,minY,minZ,maxX,maxY,maxZ);}
    @Override public String toString(){return "Region(minX="+minX+", minY="+minY+", minZ="+minZ+", maxX="+maxX+", maxY="+maxY+", maxZ="+maxZ+")";}
}
