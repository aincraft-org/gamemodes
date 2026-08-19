package dev.jlo.gamemodes.persistence;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlStatementsTest {
    @Test
    void loadReturnsClasspathSqlResource() {
        assertEquals("SELECT 1", SqlStatements.load("test/select-constant.sql"));
    }

    @Test
    void loadFailsWhenResourceIsMissing() {
        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> SqlStatements.load("missing.sql"));
        assertTrue(failure.getMessage().contains("/sql/missing.sql"));
    }

    @Test
    void loadRejectsPathTraversalAndAbsoluteNames() {
        assertThrows(IllegalArgumentException.class, () -> SqlStatements.load("../secret.sql"));
        assertThrows(IllegalArgumentException.class, () -> SqlStatements.load("/sql/test/select-constant.sql"));
        assertThrows(IllegalArgumentException.class, () -> SqlStatements.load(""));
    }

    @Test
    void loadReturnsMigrationRunnerSql() {
        String create = SqlStatements.load("migrations/create-schema_migrations.sql");
        String select = SqlStatements.load("migrations/select-schema_migrations.sql");
        String insert = SqlStatements.load("migrations/insert-schema_migrations.sql");
        String initial = SqlStatements.load("migrations/V1__initial.sql");
        String leases = SqlStatements.load("migrations/V2__reward_outbox_leases.sql");
        assertTrue(create.contains("schema_migrations"));
        assertTrue(select.contains("schema_migrations"));
        assertTrue(insert.contains("schema_migrations"));
        assertTrue(initial.contains("match_snapshots"));
        assertTrue(leases.contains("lease_until_epoch_ms"));
    }

    @Test
    void javaSourcesDoNotContainInlineCreateOrInsert() throws IOException {
        Path root = Path.of(requiredProperty("project.root")).resolve("src/main/java");
        assertTrue(Files.isDirectory(root), "missing " + root);
        try (var stream = Files.walk(root)) {
            var sources = stream.filter(path -> path.toString().endsWith(".java")).toList();
            assertFalse(sources.isEmpty(), "no Java sources under " + root);
            for (Path source : sources) {
                String java = Files.readString(source);
                assertFalse(java.contains("CREATE TABLE"), () -> source.toString());
                assertFalse(java.contains("INSERT INTO"), () -> source.toString());
            }
        }
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name);
        assertTrue(value != null && !value.isBlank(), "missing system property " + name);
        return value;
    }
}
