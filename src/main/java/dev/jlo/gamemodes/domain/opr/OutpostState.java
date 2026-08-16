package dev.jlo.gamemodes.domain.opr;

import dev.jlo.gamemodes.domain.common.Team;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class OutpostState {
    private final OutpostId id; private Team owner; private Duration progress; private Instant lastTick, emptySince;
    private Team capturingTeam; private Instant ownershipSince; private final Set<UUID> occupants; private Team wardTeam; private Instant wardUntil;
    public OutpostState(OutpostId id){this(id,null,Duration.ZERO,null,null,null,null,new LinkedHashSet<>(),null,null);}
    public OutpostState(OutpostId id,Team owner,Duration progress,Instant lastTick,Instant emptySince,Team capturingTeam,Instant ownershipSince,Set<UUID> occupants,Team wardTeam,Instant wardUntil){this.id=id;this.owner=owner;this.progress=progress;this.lastTick=lastTick;this.emptySince=emptySince;this.capturingTeam=capturingTeam;this.ownershipSince=ownershipSince;this.occupants=new LinkedHashSet<>(occupants);this.wardTeam=wardTeam;this.wardUntil=wardUntil;}
    public OutpostId getId(){return id;} public Team getOwner(){return owner;} public void setOwner(Team v){owner=v;} public Duration getProgress(){return progress;} public void setProgress(Duration v){progress=v;} public Instant getLastTick(){return lastTick;} public void setLastTick(Instant v){lastTick=v;} public Instant getEmptySince(){return emptySince;} public void setEmptySince(Instant v){emptySince=v;} public Team getCapturingTeam(){return capturingTeam;} public void setCapturingTeam(Team v){capturingTeam=v;} public Instant getOwnershipSince(){return ownershipSince;} public void setOwnershipSince(Instant v){ownershipSince=v;} public Set<UUID> getOccupants(){return occupants;} public Team getWardTeam(){return wardTeam;} public void setWardTeam(Team v){wardTeam=v;} public Instant getWardUntil(){return wardUntil;} public void setWardUntil(Instant v){wardUntil=v;}
    public boolean captureBlockedByWard(Team team,Instant now){return wardTeam!=null&&wardTeam!=team&&wardUntil!=null&&wardUntil.isAfter(now);}
    public OutpostState copy(OutpostId id,Team owner,Duration progress,Instant lastTick,Instant emptySince,Team capturingTeam,Instant ownershipSince,Set<UUID> occupants,Team wardTeam,Instant wardUntil){return new OutpostState(id,owner,progress,lastTick,emptySince,capturingTeam,ownershipSince,occupants,wardTeam,wardUntil);}
    @Override public boolean equals(Object o){if(this==o)return true;if(!(o instanceof OutpostState x))return false;return Objects.equals(id,x.id)&&Objects.equals(owner,x.owner)&&Objects.equals(progress,x.progress)&&Objects.equals(lastTick,x.lastTick)&&Objects.equals(emptySince,x.emptySince)&&Objects.equals(capturingTeam,x.capturingTeam)&&Objects.equals(ownershipSince,x.ownershipSince)&&Objects.equals(occupants,x.occupants)&&Objects.equals(wardTeam,x.wardTeam)&&Objects.equals(wardUntil,x.wardUntil);}
    @Override public int hashCode(){return Objects.hash(id,owner,progress,lastTick,emptySince,capturingTeam,ownershipSince,occupants,wardTeam,wardUntil);}
    @Override public String toString(){return "OutpostState(id="+id+", owner="+owner+")";}
}
