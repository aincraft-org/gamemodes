package dev.jlo.gamemodes.player;

import java.util.Objects;
import java.util.UUID;

public final class PendingRestore {
    private final UUID playerId; private final PlayerSnapshot snapshot; private final RestoreState state; private final int attempts;
    public PendingRestore(UUID playerId, PlayerSnapshot snapshot, RestoreState state, int attempts){this.playerId=playerId;this.snapshot=snapshot;this.state=state;this.attempts=attempts;}
    public UUID getPlayerId(){return playerId;} public PlayerSnapshot getSnapshot(){return snapshot;} public RestoreState getState(){return state;} public int getAttempts(){return attempts;}
    public PendingRestore copy(UUID playerId,PlayerSnapshot snapshot,RestoreState state,int attempts){return new PendingRestore(playerId,snapshot,state,attempts);}
    @Override public boolean equals(Object o){if(this==o)return true;if(!(o instanceof PendingRestore p))return false;return attempts==p.attempts&&Objects.equals(playerId,p.playerId)&&Objects.equals(snapshot,p.snapshot)&&state==p.state;}
    @Override public int hashCode(){return Objects.hash(playerId,snapshot,state,attempts);} @Override public String toString(){return "PendingRestore[playerId="+playerId+", snapshot="+snapshot+", state="+state+", attempts="+attempts+"]";}
}
