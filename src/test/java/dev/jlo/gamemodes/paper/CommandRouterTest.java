package dev.jlo.gamemodes.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CommandRouterTest {
    @Test
    void joinStatusLeaveAndTeamRouteThroughService() {
        FakeCommandService service = new FakeCommandService();
        CommandRouter router = new CommandRouter(service);
        UUID player = UUID.randomUUID();

        assertEquals("joined OPR", router.execute(player, List.of("join", "opr")));
        assertEquals("team A", router.execute(player, List.of("team")));
        assertEquals("OPR", router.execute(player, List.of("status")));
        assertEquals("left", router.execute(player, List.of("leave")));
    }

    @Test
    void aliasesMapToJoinMode() {
        FakeCommandService service = new FakeCommandService();
        CommandRouter router = new CommandRouter(service);
        UUID player = UUID.randomUUID();

        assertEquals("joined OPR", router.execute(player, List.of("opr")));
        assertEquals("joined SIEGE", router.execute(player, List.of("siege")));
    }

    private static final class FakeCommandService implements CommandService {
        private Mode mode;
        private TeamAssignment team;

        @Override
        public TeamAssignment join(UUID player, Mode mode, String arena) {
            this.mode = mode;
            this.team = mode == Mode.OPR ? TeamAssignment.A : TeamAssignment.B;
            return team;
        }

        @Override
        public boolean leave(UUID player) {
            mode = null;
            team = null;
            return true;
        }

        @Override
        public boolean ready(UUID player) {
            return true;
        }

        @Override
        public String status(UUID player) {
            return mode == null ? "NONE" : mode.name();
        }

        @Override
        public TeamAssignment team(UUID player) {
            return team;
        }

        @Override
        public String admin(UUID player, String action, List<String> args) {
            return action;
        }
    }
}
