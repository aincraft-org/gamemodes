package dev.jlo.gamemodes.reward;

import dev.jlo.gamemodes.persistence.SqlStatements;
import dev.jlo.gamemodes.persistence.SqliteMigrations;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RewardOutbox {
    public static final long DEFAULT_LEASE_DURATION_MS = 60_000L;

    private final Connection connection;
    private final Clock clock;

    public RewardOutbox(Connection connection) {
        this(connection, Clock.systemUTC());
    }

    public RewardOutbox(Connection connection, Clock clock) {
        this.connection = connection;
        this.clock = clock;
        try {
            new SqliteMigrations(clock).apply(connection);
        } catch (Exception e) {
            throw new RuntimeException("Failed to apply reward outbox migrations", e);
        }
    }

    public synchronized boolean enqueue(String matchId, UUID playerId, String rewardType, long amount) {
        if (matchId == null || matchId.isBlank() || rewardType == null || rewardType.isBlank() || amount < 0) {
            throw new IllegalArgumentException();
        }
        try (PreparedStatement ps = connection.prepareStatement(SqlStatements.load("reward/insert-outbox.sql"))) {
            ps.setString(1, matchId);
            ps.setString(2, playerId.toString());
            ps.setString(3, rewardType);
            ps.setLong(4, amount);
            ps.setLong(5, clock.millis());
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized List<Reward> claim() {
        return claim(100, DEFAULT_LEASE_DURATION_MS);
    }

    public synchronized List<Reward> claim(int limit) {
        return claim(limit, DEFAULT_LEASE_DURATION_MS);
    }

    public synchronized List<Reward> claim(int limit, long leaseDurationMs) {
        if (limit <= 0 || leaseDurationMs <= 0) {
            throw new IllegalArgumentException();
        }
        long now = clock.millis();
        long until = Math.addExact(now, leaseDurationMs);
        try {
            connection.setAutoCommit(false);
            List<String[]> keys = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(SqlStatements.load("reward/select-claimable.sql"))) {
                ps.setLong(1, now);
                ps.setInt(2, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        keys.add(new String[]{rs.getString(1), rs.getString(2), rs.getString(3)});
                    }
                }
            }
            if (!keys.isEmpty()) {
                try (PreparedStatement ps = connection.prepareStatement(SqlStatements.load("reward/update-claimed.sql"))) {
                    for (String[] key : keys) {
                        ps.setLong(1, now);
                        ps.setLong(2, until);
                        ps.setString(3, key[0]);
                        ps.setString(4, key[1]);
                        ps.setString(5, key[2]);
                        ps.setLong(6, now);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }
            connection.commit();
            List<Reward> result = new ArrayList<>();
            for (String[] key : keys) {
                Reward reward = get(key[0], key[1], key[2]);
                if (reward != null) result.add(reward);
            }
            return result;
        } catch (Throwable t) {
            try {
                connection.rollback();
            } catch (SQLException rollbackFailure) {
                t.addSuppressed(rollbackFailure);
            }
            if (t instanceof RuntimeException runtimeException) throw runtimeException;
            if (t instanceof Error error) throw error;
            throw new RuntimeException(t);
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public synchronized List<Reward> pending() {
        return claim(100);
    }

    public synchronized List<Reward> pending(int limit) {
        return claim(limit);
    }

    public synchronized boolean markApplied(Reward reward) {
        try (PreparedStatement ps = connection.prepareStatement(SqlStatements.load("reward/update-applied.sql"))) {
            ps.setLong(1, clock.millis());
            ps.setString(2, reward.getMatchId());
            ps.setString(3, reward.getPlayerId().toString());
            ps.setString(4, reward.getType());
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized int abortMatch(String matchId) {
        try (PreparedStatement ps = connection.prepareStatement(SqlStatements.load("reward/update-aborted.sql"))) {
            ps.setString(1, matchId);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private Reward get(String matchId, String playerId, String type) {
        try (PreparedStatement ps = connection.prepareStatement(SqlStatements.load("reward/select-outbox.sql"))) {
            ps.setString(1, matchId);
            ps.setString(2, playerId);
            ps.setString(3, type);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                long lease = rs.getLong(4);
                Long leaseUntil = rs.wasNull() ? null : lease;
                return new Reward(matchId, UUID.fromString(playerId), type, rs.getLong(1),
                        RewardState.valueOf(rs.getString(2)), rs.getInt(3), leaseUntil);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
