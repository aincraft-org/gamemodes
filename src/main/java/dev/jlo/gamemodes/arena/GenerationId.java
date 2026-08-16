package dev.jlo.gamemodes.arena;

import java.util.Objects;

public final class GenerationId {
    private final String value;

    public GenerationId(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Generation ID must not be blank");
        this.value = value;
    }
    public String getValue() { return value; }
    @Override public boolean equals(Object o) { return o instanceof GenerationId other && value.equals(other.value); }
    @Override public int hashCode() { return Objects.hash(value); }
    @Override public String toString() { return "GenerationId(value=" + value + ")"; }
}
