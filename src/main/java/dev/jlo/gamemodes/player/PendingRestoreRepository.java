package dev.jlo.gamemodes.player;

import dev.jlo.gamemodes.persistence.SqliteMigrations;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.util.UUID;

public class PendingRestoreRepository {
    private final Connection connection;
    private final Clock clock;

    public PendingRestoreRepository(Connection connection) {
        this(connection, Clock.systemUTC());
    }

    public PendingRestoreRepository(Connection connection, Clock clock) {
        this.connection = connection;
        this.clock = clock;
        try {
            new SqliteMigrations(clock).apply(connection);
        } catch (SQLException | java.io.IOException e) {
            throw new RuntimeException("Failed to apply player restore migrations", e);
        }
    }

    public synchronized void put(PlayerSnapshot snapshot) {
        String sql = """
                INSERT INTO player_restores(
                    player_id, restore_version, payload, state, attempts,
                    available_at_epoch_ms, updated_at_epoch_ms
                ) VALUES (?, 1, ?, 'PENDING', 0, ?, ?)
                ON CONFLICT(player_id) DO UPDATE SET
                    payload = excluded.payload,
                    state = 'PENDING',
                    attempts = 0,
                    available_at_epoch_ms = excluded.available_at_epoch_ms,
                    updated_at_epoch_ms = excluded.updated_at_epoch_ms
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            long now = clock.millis();
            statement.setString(1, snapshot.getPlayerId().toString());
            statement.setBytes(2, snapshot.encode());
            statement.setLong(3, now);
            statement.setLong(4, now);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized PendingRestore claim(UUID playerId) {
        String sql = """
                UPDATE player_restores
                SET state = 'CLAIMED',
                    attempts = attempts + 1,
                    claimed_at_epoch_ms = ?,
                    updated_at_epoch_ms = ?
                WHERE player_id = ?
                  AND state IN ('PENDING', 'FAILED')
                  AND available_at_epoch_ms <= ?
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            long now = clock.millis();
            statement.setLong(1, now);
            statement.setLong(2, now);
            statement.setString(3, playerId.toString());
            statement.setLong(4, now);
            return statement.executeUpdate() == 0 ? null : get(playerId);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized boolean markRestored(UUID playerId) {
        return transition(playerId, "RESTORED");
    }

    public synchronized boolean markFailed(UUID playerId, String error) {
        String sql = """
                UPDATE player_restores
                SET state = 'FAILED',
                    last_error = ?,
                    updated_at_epoch_ms = ?
                WHERE player_id = ?
                  AND state = 'CLAIMED'
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, error);
            statement.setLong(2, clock.millis());
            statement.setString(3, playerId.toString());
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized boolean cancel(UUID playerId) {
        return transition(playerId, "CANCELLED");
    }

    public PendingRestore get(UUID playerId) {
        String sql = "SELECT payload, state, attempts "
                + "FROM player_restores WHERE player_id = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return new PendingRestore(
                        playerId,
                        PlayerSnapshot.decode(resultSet.getBytes(1)),
                        RestoreState.valueOf(resultSet.getString(2)),
                        resultSet.getInt(3)
                );
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean transition(UUID playerId, String state) {
        String sql = """
                UPDATE player_restores
                SET state = ?,
                    completed_at_epoch_ms = ?,
                    updated_at_epoch_ms = ?
                WHERE player_id = ?
                  AND state = 'CLAIMED'
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            long now = clock.millis();
            statement.setString(1, state);
            statement.setLong(2, now);
            statement.setLong(3, now);
            statement.setString(4, playerId.toString());
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
