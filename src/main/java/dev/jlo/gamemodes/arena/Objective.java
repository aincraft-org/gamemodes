package dev.jlo.gamemodes.arena;
public record Objective(String id, Region region) { public Objective { java.util.Objects.requireNonNull(id); java.util.Objects.requireNonNull(region); } public String getId(){return id;} public Region getRegion(){return region;} public Objective copy(String id,Region region){return new Objective(id,region);} }
