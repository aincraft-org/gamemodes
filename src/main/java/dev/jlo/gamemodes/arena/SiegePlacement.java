package dev.jlo.gamemodes.arena;
public record SiegePlacement(String id, BlockPosition position) { public SiegePlacement { java.util.Objects.requireNonNull(id); java.util.Objects.requireNonNull(position); } public String getId(){return id;} public BlockPosition getPosition(){return position;} public SiegePlacement copy(String id,BlockPosition position){return new SiegePlacement(id,position);} }
