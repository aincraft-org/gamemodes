package dev.jlo.gamemodes.domain.siege;
import java.time.Duration;
public final class SiegeMatchKt { private SiegeMatchKt() {} public static Duration coerceAtLeast(Duration value, Duration minimum) { return value.compareTo(minimum) < 0 ? minimum : value; } }
