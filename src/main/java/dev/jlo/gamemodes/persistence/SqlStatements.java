package dev.jlo.gamemodes.persistence;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Loads SQL statement text from classpath {@code /sql} resources. */
public final class SqlStatements {
    private SqlStatements() {}

    public static String load(String name) {
        Objects.requireNonNull(name, "name");
        if (name.isBlank() || name.startsWith("/") || name.contains("..")) {
            throw new IllegalArgumentException("invalid SQL resource name: " + name);
        }
        String path = "/sql/" + name;
        try (InputStream in = SqlStatements.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("missing SQL resource: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read SQL resource " + path, e);
        }
    }
}
