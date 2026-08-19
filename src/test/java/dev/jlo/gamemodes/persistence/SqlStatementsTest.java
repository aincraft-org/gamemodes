package dev.jlo.gamemodes.persistence;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
