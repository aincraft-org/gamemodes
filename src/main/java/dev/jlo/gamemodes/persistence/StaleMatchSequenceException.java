package dev.jlo.gamemodes.persistence;

public class StaleMatchSequenceException extends IllegalStateException {
    public StaleMatchSequenceException(String message) {
        super(message);
    }
}
