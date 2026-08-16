package dev.jlo.gamemodes.paper;

/** Supported playable Paper modes. */
public enum Mode {
    OPR,
    SIEGE;

    public String getName() {
        return name();
    }

    public static Mode parse(String value) {
        for (Mode mode : values()) {
            if (mode.name().equalsIgnoreCase(value)) {
                return mode;
            }
        }
        return null;
    }
}
