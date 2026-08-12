package dev.jlo.gamemodes.domain.common

import java.time.Clock
import java.time.Duration
import java.time.Instant

/** Runtime clock with monotonic measurements for live scheduling and epoch time for persistence. */
class RuntimeClock(
    private val epochClock: Clock = Clock.systemUTC(),
    private val monotonicNanos: () -> Long = System::nanoTime
) {
    fun epochNow(): Instant = epochClock.instant()

    fun monotonicNanos(): Long = monotonicNanos.invoke()
}

@JvmInline
value class Deadline(val at: Instant) {
    fun remainingAt(now: Instant): Duration = if (now >= at) Duration.ZERO else Duration.between(now, at)

    fun isExpiredAt(now: Instant): Boolean = !now.isBefore(at)
}

data class RespawnWave(val period: Duration, val anchor: Instant) {
    init {
        require(!period.isZero && !period.isNegative) { "Respawn period must be positive" }
    }

    fun nextWaveAt(now: Instant): Instant {
        if (now.isBefore(anchor)) return anchor
        val elapsed = Duration.between(anchor, now)
        val periods = elapsed.toNanos() / period.toNanos() + 1
        return anchor.plus(period.multipliedBy(periods))
    }
}
