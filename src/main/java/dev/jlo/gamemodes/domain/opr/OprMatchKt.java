package dev.jlo.gamemodes.domain.opr;

import dev.jlo.gamemodes.domain.common.Team;

public final class OprMatchKt {
    private OprMatchKt() {}
    public static Team other(Team team) { return team == Team.A ? Team.B : Team.A; }
}
