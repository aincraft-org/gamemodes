package dev.jlo.gamemodes.persistence;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Applies classpath SQL migrations exactly once, in version order. */
public class SqliteMigrations {
    private final Clock clock;
    private final String resourcePrefix;
    private final Pattern migrationName = Pattern.compile("V(\\d+)__([A-Za-z0-9_.-]+)\\.sql");

    public SqliteMigrations() {
        this(Clock.systemUTC(), "db/migrations/");
    }

    public SqliteMigrations(Clock clock) {
        this(clock, "db/migrations/");
    }

    public SqliteMigrations(Clock clock, String resourcePrefix) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.resourcePrefix = Objects.requireNonNull(resourcePrefix, "resourcePrefix");
    }

    public void apply(Connection connection) throws SQLException, IOException {
        Objects.requireNonNull(connection, "connection");
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS schema_migrations (version INTEGER PRIMARY KEY, name TEXT NOT NULL, applied_at_epoch_ms INTEGER NOT NULL)");
        }
        List<Migration> migrations = discover();
        connection.setAutoCommit(false);
        try {
            for (Migration migration : migrations) {
                boolean exists;
                try (var ps = connection.prepareStatement("SELECT 1 FROM schema_migrations WHERE version = ?")) {
                    ps.setInt(1, migration.version());
                    try (ResultSet rs = ps.executeQuery()) {
                        exists = rs.next();
                    }
                }
                if (!exists) {
                    try (Statement statement = connection.createStatement()) {
                        for (String sql : migration.sql().split(";")) {
                            String trimmed = sql.trim();
                            if (!trimmed.isEmpty()) {
                                statement.executeUpdate(trimmed);
                            }
                        }
                    }
                    try (var ps = connection.prepareStatement("INSERT INTO schema_migrations(version, name, applied_at_epoch_ms) VALUES (?, ?, ?)")) {
                        ps.setInt(1, migration.version());
                        ps.setString(2, migration.name());
                        ps.setLong(3, clock.millis());
                        ps.executeUpdate();
                    }
                }
            }
            connection.commit();
        } catch (Throwable throwable) {
            try {
                connection.rollback();
            } catch (SQLException rollbackFailure) {
                throwable.addSuppressed(rollbackFailure);
            }
            if (throwable instanceof SQLException sqlException) {
                throw sqlException;
            }
            if (throwable instanceof IOException ioException) {
                throw ioException;
            }
            if (throwable instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (throwable instanceof Error error) {
                throw error;
            }
            throw new RuntimeException(throwable);
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private List<Migration> discover() throws IOException {
        List<String> names = List.of("V1__initial.sql", "V2__reward_outbox_leases.sql");
        List<Migration> migrations = new ArrayList<>(names.size());
        for (String file : names) {
            Matcher match = migrationName.matcher(file);
            if (!match.matches()) {
                throw new IllegalArgumentException("Invalid migration name: " + file);
            }
            String sql;
            InputStream input = SqliteMigrations.class.getClassLoader().getResourceAsStream(resourcePrefix + file);
            if (input == null) {
                throw new IllegalStateException("Missing migration resource: " + file);
            }
            try (InputStream migrationInput = input) {
                sql = new String(migrationInput.readAllBytes(), StandardCharsets.UTF_8);
            }
            migrations.add(new Migration(Integer.parseInt(match.group(1)), file, sql));
        }
        migrations.sort(Comparator.comparingInt(Migration::version));
        return migrations;
    }

    private record Migration(int version, String name, String sql) {
    }
}
