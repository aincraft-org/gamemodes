package dev.jlo.gamemodes.player;

import dev.jlo.gamemodes.persistence.SqlStatements;
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
        } catch (SQLException e) {
            throw new RuntimeException("Failed to apply player restore migrations", e);
        }
    }

    public synchronized void put(PlayerSnapshot snapshot) {
        try (PreparedStatement statement = connection.prepareStatement(
                SqlStatements.load("restores/upsert-restore.sql"))) {
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
        try (PreparedStatement statement = connection.prepareStatement(
                SqlStatements.load("restores/update-claimed.sql"))) {
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
        try (PreparedStatement statement = connection.prepareStatement(
                SqlStatements.load("restores/update-failed.sql"))) {
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
        try (PreparedStatement statement = connection.prepareStatement(
                SqlStatements.load("restores/select-restore.sql"))) {
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
        try (PreparedStatement statement = connection.prepareStatement(
                SqlStatements.load("restores/update-completed.sql"))) {
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
