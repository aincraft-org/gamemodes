package dev.jlo.gamemodes.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaperMatchServiceTest {
    @Test
    void joinRejectsMissingAndWrongModeArenasWithoutSyntheticFallback() {
        PaperMatchService service = new PaperMatchService((mode, requested) -> requested, ignored -> {}, ignored -> {}, null);
        UUID player = UUID.randomUUID();

        assertThrows(IllegalStateException.class, () -> service.join(player, Mode.OPR, null));
        assertThrows(IllegalStateException.class, () -> service.join(player, Mode.OPR, "missing"));

        service.configureArenas(List.of(new PaperMatchService.ArenaConfig("siege", Mode.SIEGE, 2, 1)));
        assertThrows(IllegalArgumentException.class, () -> service.join(player, Mode.OPR, "siege"));
        assertFalse(service.isParticipant(player));
    }

    @Test
    void joinCapturesBeforeAdmissionAndLeaveRestoresExactlyOnce() {
        List<String> order = new ArrayList<>();
        PaperMatchService service = new PaperMatchService((mode, requested) -> requested, ignored -> {}, ignored -> {}, null);
        service.configureArenas(List.of(new PaperMatchService.ArenaConfig("opr", Mode.OPR, 2, 1)));
        service.installPlayerHooks(
                player -> order.add("capture:" + player),
                player -> order.add("restore:" + player),
                (id, session, team) -> order.add("admit:" + id + ":" + team));
        UUID player = UUID.randomUUID();

        assertEquals(TeamAssignment.A, service.join(player, Mode.OPR, "opr"));
        assertTrue(service.isParticipant(player));
        assertEquals(List.of("capture:" + player, "admit:" + player + ":A"), order);

        assertTrue(service.leave(player));
        assertFalse(service.isParticipant(player));
        assertEquals("restore:" + player, order.get(order.size() - 1));
        assertEquals(1, order.stream().filter(entry -> entry.equals("restore:" + player)).count());
    }

    @Test
    void failedAdmissionRollsBackOwnershipAndRestoresSnapshot() {
        List<UUID> restored = new ArrayList<>();
        PaperMatchService service = new PaperMatchService((mode, requested) -> requested, ignored -> {}, ignored -> {}, null);
        service.configureArenas(List.of(new PaperMatchService.ArenaConfig("opr", Mode.OPR, 2, 1)));
        service.installPlayerHooks(
                player -> {},
                restored::add,
                (id, session, team) -> { throw new IllegalStateException("teleport rejected"); });
        UUID player = UUID.randomUUID();

        assertThrows(IllegalStateException.class, () -> service.join(player, Mode.OPR, "opr"));
        assertEquals(List.of(player), restored);
        assertFalse(service.isParticipant(player));
        assertEquals(null, service.team(player));
    }

    @Test
    void terminalStopRestoresPlayersBeforeDeletingArenaSession() {
        List<String> order = new ArrayList<>();
        PaperMatchService service = new PaperMatchService(
                (mode, requested) -> requested,
                ignored -> {},
                session -> order.add("remove:" + session.getId()),
                null);
        service.configureArenas(List.of(new PaperMatchService.ArenaConfig("opr", Mode.OPR, 2, 1)));
        service.installPlayerHooks(
                player -> {},
                player -> order.add("restore:" + player),
                (id, session, team) -> {});
        UUID player = UUID.randomUUID();
        service.join(player, Mode.OPR, "opr");
        assertTrue(service.safetyPolicy().isActive(player));

        assertEquals("stopped 1", service.admin(null, "stop", List.of("opr")));

        assertEquals(2, order.size());
        assertEquals("restore:" + player, order.get(0));
        assertTrue(order.get(order.size() - 1).startsWith("remove:"));
        assertFalse(service.isParticipant(player));
        assertFalse(service.safetyPolicy().isActive(player));
        assertTrue(service.sessions().isEmpty());
    }
}
