package dev.jlo.gamemodes.persistence

import dev.jlo.gamemodes.player.LocationSnapshot
import dev.jlo.gamemodes.player.PendingRestoreRepository
import dev.jlo.gamemodes.player.PlayerSnapshot
import dev.jlo.gamemodes.player.RestoreState
import dev.jlo.gamemodes.reward.RewardOutbox
import dev.jlo.gamemodes.reward.RewardState
import java.nio.file.Files
import java.sql.DriverManager
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SqlitePersistenceIntegrationTest {
    private val clock = Clock.fixed(Instant.parse("2026-08-11T12:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `reward lease survives close and reopen with CLAIMED schema state`() {
        val database = Files.createTempFile("gamemodes-reward", ".sqlite")
        Files.deleteIfExists(database)
        val player = UUID.randomUUID()
        try {
            DriverManager.getConnection("jdbc:sqlite:${database.absolutePathString()}").use { connection ->
                val outbox = RewardOutbox(connection, clock)
                assertTrue(outbox.enqueue("match-1", player, "WIN_TOKEN", 1))
                val claimed = outbox.claim(1, 60_000)
                assertEquals(1, claimed.size)
                assertEquals(RewardState.CLAIMED, claimed.single().state)
            }

            DriverManager.getConnection("jdbc:sqlite:${database.absolutePathString()}").use { connection ->
                val outbox = RewardOutbox(connection, clock)
                val claimed = outbox.claim(1, 60_000)
                assertTrue(claimed.isEmpty(), "an unexpired lease must not be claimed twice")
                val state = connection.prepareStatement(
                    "SELECT state FROM reward_outbox WHERE match_id='match-1'"
                ).use { statement -> statement.executeQuery().use { rows -> rows.next(); rows.getString(1) } }
                assertEquals("CLAIMED", state)
            }
        } finally {
            Files.deleteIfExists(database)
        }
    }

    @Test
    fun `pending player restore survives close and reopen`() {
        val database = Files.createTempFile("gamemodes-restore", ".sqlite")
        Files.deleteIfExists(database)
        val player = UUID.randomUUID()
        val snapshot = PlayerSnapshot(
            playerId = player,
            inventory = emptyList(),
            cursor = null,
            armor = emptyList(),
            offhand = null,
            effects = emptyList(),
            attributes = mapOf("minecraft:max_health" to 20.0),
            health = 17.0,
            food = 16,
            saturation = 4.0,
            experience = 0.25f,
            level = 12,
            gameMode = "SURVIVAL",
            allowFlight = false,
            flying = false,
            returnLocation = LocationSnapshot("world", 1.0, 65.0, 2.0, 90f, 0f)
        )
        try {
            DriverManager.getConnection("jdbc:sqlite:${database.absolutePathString()}").use { connection ->
                PendingRestoreRepository(connection, clock).put(snapshot)
            }
            DriverManager.getConnection("jdbc:sqlite:${database.absolutePathString()}").use { connection ->
                val repository = PendingRestoreRepository(connection, clock)
                val claimed = assertNotNull(repository.claim(player))
                assertEquals(RestoreState.CLAIMED, claimed.state)
                assertEquals(snapshot, claimed.snapshot)
                assertTrue(repository.markRestored(player))
                assertEquals(RestoreState.RESTORED, repository.get(player)?.state)
            }
        } finally {
            Files.deleteIfExists(database)
        }
    }
}
