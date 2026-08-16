package dev.jlo.gamemodes.domain.common;

import java.util.List;

public interface Match {
    MatchId getId();
    MatchLifecycle getLifecycle();
    List<MatchEvent> handle(MatchEvent event);
}
