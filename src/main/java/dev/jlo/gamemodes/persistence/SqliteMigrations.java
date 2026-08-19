package dev.jlo.gamemodes.persistence;

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
    private static final List<String> MIGRATIONS = List.of(
            "V1__initial.sql",
            "V2__reward_outbox_leases.sql");

    private final Clock clock;
    private final Pattern migrationName = Pattern.compile("V(\\d+)__([A-Za-z0-9_.-]+)\\.sql");

    public SqliteMigrations() {
        this(Clock.systemUTC());
    }

    public SqliteMigrations(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void apply(Connection connection) throws SQLException {
        Objects.requireNonNull(connection, "connection");
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(SqlStatements.load("migrations/create-schema_migrations.sql"));
        }
        List<Migration> migrations = discover();
        connection.setAutoCommit(false);
        try {
            for (Migration migration : migrations) {
                boolean exists;
                try (var ps = connection.prepareStatement(
                        SqlStatements.load("migrations/select-schema_migrations.sql"))) {
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
                    try (var ps = connection.prepareStatement(
                            SqlStatements.load("migrations/insert-schema_migrations.sql"))) {
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

    private List<Migration> discover() {
        List<Migration> migrations = new ArrayList<>(MIGRATIONS.size());
        for (String file : MIGRATIONS) {
            Matcher match = migrationName.matcher(file);
            if (!match.matches()) {
                throw new IllegalArgumentException("Invalid migration name: " + file);
            }
            String sql = SqlStatements.load("migrations/" + file);
            migrations.add(new Migration(Integer.parseInt(match.group(1)), file, sql));
        }
        migrations.sort(Comparator.comparingInt(Migration::version));
        return migrations;
    }

    private record Migration(int version, String name, String sql) {
    }
}
