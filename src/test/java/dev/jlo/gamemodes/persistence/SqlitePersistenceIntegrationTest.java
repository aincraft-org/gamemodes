package dev.jlo.gamemodes.persistence;

import dev.jlo.gamemodes.player.LocationSnapshot;
import dev.jlo.gamemodes.player.PendingRestore;
import dev.jlo.gamemodes.player.PendingRestoreRepository;
import dev.jlo.gamemodes.player.PlayerSnapshot;
import dev.jlo.gamemodes.player.RestoreState;
import dev.jlo.gamemodes.reward.Reward;
import dev.jlo.gamemodes.reward.RewardOutbox;
import dev.jlo.gamemodes.reward.RewardState;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlitePersistenceIntegrationTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-11T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void rewardLeaseSurvivesCloseAndReopenWithClaimedSchemaState() throws IOException, SQLException {
        Path database = Files.createTempFile("gamemodes-reward", ".sqlite");
        Files.deleteIfExists(database);
        UUID player = UUID.randomUUID();
        try {
            try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath())) {
                RewardOutbox outbox = new RewardOutbox(connection, clock);
                assertTrue(outbox.enqueue("match-1", player, "WIN_TOKEN", 1));
                List<Reward> claimed = outbox.claim(1, 60_000);
                assertEquals(1, claimed.size());
                assertEquals(RewardState.CLAIMED, claimed.getFirst().getState());
            }

            try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath())) {
                RewardOutbox outbox = new RewardOutbox(connection, clock);
                List<Reward> claimed = outbox.claim(1, 60_000);
                assertTrue(claimed.isEmpty(), "an unexpired lease must not be claimed twice");
                String state;
                try (var statement = connection.prepareStatement(
                        "SELECT state FROM reward_outbox WHERE match_id='match-1'")) {
                    try (var rows = statement.executeQuery()) {
                        rows.next();
                        state = rows.getString(1);
                    }
                }
                assertEquals("CLAIMED", state);
            }
        } finally {
            Files.deleteIfExists(database);
        }
    }

    @Test
    void pendingPlayerRestoreSurvivesCloseAndReopen() throws IOException, SQLException {
        Path database = Files.createTempFile("gamemodes-restore", ".sqlite");
        Files.deleteIfExists(database);
        UUID player = UUID.randomUUID();
        PlayerSnapshot snapshot = new PlayerSnapshot(
                player,
                List.of(),
                null,
                List.of(),
                null,
                List.of(),
                Map.of("minecraft:max_health", 20.0),
                17.0,
                16,
                4.0,
                0.25f,
                12,
                "SURVIVAL",
                false,
                false,
                new LocationSnapshot("world", 1.0, 65.0, 2.0, 90f, 0f));
        try {
            try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath())) {
                new PendingRestoreRepository(connection, clock).put(snapshot);
            }
            try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath())) {
                PendingRestoreRepository repository = new PendingRestoreRepository(connection, clock);
                PendingRestore claimed = repository.claim(player);
                assertNotNull(claimed);
                assertEquals(RestoreState.CLAIMED, claimed.getState());
                assertEquals(snapshot, claimed.getSnapshot());
                assertTrue(repository.markRestored(player));
                assertEquals(RestoreState.RESTORED, repository.get(player).getState());
            }
        } finally {
            Files.deleteIfExists(database);
        }
    }
}
