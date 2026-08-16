package dev.jlo.gamemodes.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.Objects;

public final class MatchSnapshotStore {
    private final Connection connection;
    private final SqliteMigrations migrations;

    public MatchSnapshotStore(Connection connection) {
        this(connection, new SqliteMigrations());
    }

    public MatchSnapshotStore(Connection connection, SqliteMigrations migrations) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.migrations = Objects.requireNonNull(migrations, "migrations");
        try {
            migrations.apply(connection);
        } catch (Exception e) {
            throw new RuntimeException("Failed to apply match snapshot migrations", e);
        }
    }


    public synchronized MatchSnapshot load(String matchId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT sequence, snapshot_version, payload, deadline_epoch_ms FROM match_snapshots WHERE match_id = ?")) {
            ps.setString(1, matchId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                long deadlineEpochMs = rs.getLong(4);
                Instant deadline = rs.wasNull() ? null : Instant.ofEpochMilli(deadlineEpochMs);
                return new MatchSnapshot(matchId, rs.getLong(1), rs.getInt(2), rs.getBytes(3), deadline);
            }
        }
    }

    public synchronized void save(long expectedSequence, MatchSnapshot snapshot) throws SQLException {
        Objects.requireNonNull(snapshot, "snapshot");
        if (snapshot.getSequence() != expectedSequence + 1) {
            throw new IllegalArgumentException("Snapshot sequence must increment expected sequence");
        }
        connection.setAutoCommit(false);
        try {
            int updated;
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE match_snapshots SET sequence=?, snapshot_version=?, payload=?, deadline_epoch_ms=?, updated_at_epoch_ms=? WHERE match_id=? AND sequence=?")) {
                ps.setLong(1, snapshot.getSequence());
                ps.setInt(2, snapshot.getVersion());
                ps.setBytes(3, snapshot.getPayload());
                setDeadline(ps, 4, snapshot.getDeadline());
                ps.setLong(5, System.currentTimeMillis());
                ps.setString(6, snapshot.getMatchId());
                ps.setLong(7, expectedSequence);
                updated = ps.executeUpdate();
            }
            if (updated == 0) {
                MatchSnapshot existing = load(snapshot.getMatchId());
                if (existing != null || expectedSequence != 0L) {
                    throw new StaleMatchSequenceException(
                            "Expected sequence " + expectedSequence + " for " + snapshot.getMatchId());
                }
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO match_snapshots(match_id, sequence, snapshot_version, payload, deadline_epoch_ms, updated_at_epoch_ms) VALUES (?, ?, ?, ?, ?, ?)")) {
                    ps.setString(1, snapshot.getMatchId());
                    ps.setLong(2, snapshot.getSequence());
                    ps.setInt(3, snapshot.getVersion());
                    ps.setBytes(4, snapshot.getPayload());
                    setDeadline(ps, 5, snapshot.getDeadline());
                    ps.setLong(6, System.currentTimeMillis());
                    ps.executeUpdate();
                }
            }
            connection.commit();
        } catch (Throwable throwable) {
            connection.rollback();
            throw throwable;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private static void setDeadline(PreparedStatement ps, int index, Instant deadline) throws SQLException {
        if (deadline == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setLong(index, deadline.toEpochMilli());
        }
    }
}
