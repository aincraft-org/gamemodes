package dev.jlo.gamemodes.domain.common

import java.time.Clock
import java.time.Duration
import java.util.UUID

class DefaultMatchCoordinator(
    private val clock: Clock = Clock.systemUTC()
) : MatchCoordinator {
    private val matches = linkedMapOf<MatchId, Match>()
    private val ownership = PlayerOwnership()

    @Synchronized
    override fun register(match: Match) {
        check(match.id !in matches) { "Match is already registered: ${match.id.value}" }
        matches[match.id] = match
    }

    @Synchronized
    override fun unregister(id: MatchId) {
        matches.remove(id)
        ownership.releaseOwner(Ownership.Match(id.value))
    }

    @Synchronized
    override fun ownerOf(player: UUID): Ownership? = ownership.ownerOf(player, clock.instant())

    @Synchronized
    fun claim(player: UUID, owner: Ownership) = ownership.claim(player, owner)

    @Synchronized
    fun leave(player: UUID): Boolean = ownership.releaseQueue(player)

    @Synchronized
    fun disconnect(player: UUID, reservationDuration: Duration) {
        ownership.disconnect(player, clock.instant(), reservationDuration)
    }

    @Synchronized
    fun dispatch(id: MatchId, event: MatchEvent): List<MatchEvent> = matches[id]?.handle(event)
        ?: error("Unknown match: ${id.value}")
}
