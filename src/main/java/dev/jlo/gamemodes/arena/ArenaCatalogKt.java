package dev.jlo.gamemodes.arena;

import java.nio.file.Path;

public final class ArenaCatalogKt {
    private ArenaCatalogKt() {
    }

    public static boolean isSafeRelativePath(String value) {
        if (value == null || value.isBlank() || value.startsWith("/") || value.startsWith("\\")) {
            return false;
        }
        Path path = Path.of(value);
        return !path.isAbsolute() && path.normalize().equals(path) && !containsParentSegment(path);
    }

    private static boolean containsParentSegment(Path path) {
        for (Path segment : path) {
            if (segment.toString().equals("..")) {
                return true;
            }
        }
        return false;
    }
}
