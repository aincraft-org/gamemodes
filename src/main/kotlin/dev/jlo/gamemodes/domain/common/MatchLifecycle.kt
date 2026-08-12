package dev.jlo.gamemodes.domain.common

enum class MatchPhase {
    DISABLED,
    WAITING,
    PREPARING,
    ACTIVE,
    RESOLVING,
    CLEANUP
}

class MatchLifecycle(initialPhase: MatchPhase = MatchPhase.DISABLED) {
    var phase: MatchPhase = initialPhase
        private set

    fun transitionTo(next: MatchPhase) {
        check(next in allowedTransitions[phase].orEmpty()) {
            "Illegal match transition: $phase -> $next"
        }
        phase = next
    }

    private companion object {
        val allowedTransitions = mapOf(
            MatchPhase.DISABLED to setOf(MatchPhase.WAITING),
            MatchPhase.WAITING to setOf(MatchPhase.PREPARING, MatchPhase.DISABLED),
            MatchPhase.PREPARING to setOf(MatchPhase.ACTIVE, MatchPhase.CLEANUP),
            MatchPhase.ACTIVE to setOf(MatchPhase.RESOLVING),
            MatchPhase.RESOLVING to setOf(MatchPhase.CLEANUP),
            MatchPhase.CLEANUP to setOf(MatchPhase.WAITING, MatchPhase.DISABLED)
        )
    }
}
