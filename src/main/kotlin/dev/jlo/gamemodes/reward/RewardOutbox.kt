package dev.jlo.gamemodes.reward

import java.sql.Connection
import java.time.Clock
import java.util.UUID

enum class RewardState { PENDING, CLAIMED, APPLIED, ABORTED }
data class Reward(
    val matchId: String,
    val playerId: UUID,
    val type: String,
    val amount: Long,
    val state: RewardState,
    val attempts: Int = 0,
    val leaseUntilEpochMs: Long? = null
)

class RewardOutbox(private val connection: Connection, private val clock: Clock = Clock.systemUTC()) {
    init { dev.jlo.gamemodes.persistence.SqliteMigrations(clock).apply(connection) }

    @Synchronized fun enqueue(matchId: String, playerId: UUID, rewardType: String, amount: Long): Boolean {
        require(matchId.isNotBlank() && rewardType.isNotBlank() && amount >= 0)
        return connection.prepareStatement("INSERT INTO reward_outbox(match_id,player_id,reward_type,amount,state,created_at_epoch_ms) VALUES (?,?,?,?,'PENDING',?) ON CONFLICT(match_id,player_id,reward_type) DO NOTHING").use { ps ->
            ps.setString(1, matchId); ps.setString(2, playerId.toString()); ps.setString(3, rewardType)
            ps.setLong(4, amount); ps.setLong(5, clock.millis()); ps.executeUpdate() == 1
        }
    }

    /**
     * Atomically leases up to [limit] pending rewards. A claimed row is not
     * claimable again until its lease expires; each successful claim increments attempts.
     */
    @Synchronized fun claim(limit: Int = 100, leaseDurationMs: Long = DEFAULT_LEASE_DURATION_MS): List<Reward> {
        require(limit > 0 && leaseDurationMs > 0)
        val now = clock.millis()
        val until = Math.addExact(now, leaseDurationMs)
        connection.autoCommit = false
        try {
            val keys = connection.prepareStatement(
                "SELECT match_id,player_id,reward_type FROM reward_outbox " +
                    "WHERE state='PENDING' OR (state='CLAIMED' AND lease_until_epoch_ms<=?) " +
                    "ORDER BY created_at_epoch_ms,match_id,player_id,reward_type LIMIT ?"
            ).use { ps ->
                ps.setLong(1, now); ps.setInt(2, limit)
                ps.executeQuery().use { rs ->
                    buildList { while (rs.next()) add(Triple(rs.getString(1), rs.getString(2), rs.getString(3))) }
                }
            }
            if (keys.isNotEmpty()) {
                connection.prepareStatement(
                    "UPDATE reward_outbox SET state='CLAIMED', attempts=attempts+1, claimed_at_epoch_ms=?, lease_until_epoch_ms=? " +
                        "WHERE match_id=? AND player_id=? AND reward_type=? AND (state='PENDING' OR (state='CLAIMED' AND lease_until_epoch_ms<=?))"
                ).use { ps ->
                    keys.forEach { (matchId, playerId, type) ->
                        ps.setLong(1, now); ps.setLong(2, until); ps.setString(3, matchId); ps.setString(4, playerId)
                        ps.setString(5, type); ps.setLong(6, now); ps.addBatch()
                    }
                    ps.executeBatch()
                }
            }
            connection.commit()
            return keys.mapNotNull { get(it.first, it.second, it.third) }
        } catch (t: Throwable) {
            connection.rollback()
            throw t
        } finally {
            connection.autoCommit = true
        }
    }

    /** Compatibility alias: callers now receive leased, rather than read-only, rows. */
    @Synchronized fun pending(limit: Int = 100): List<Reward> = claim(limit)

    @Synchronized fun markApplied(reward: Reward): Boolean = connection.prepareStatement(
        "UPDATE reward_outbox SET state='APPLIED', applied_at_epoch_ms=?, lease_until_epoch_ms=NULL " +
            "WHERE match_id=? AND player_id=? AND reward_type=? AND state='CLAIMED'"
    ).use { ps ->
        ps.setLong(1, clock.millis()); ps.setString(2, reward.matchId); ps.setString(3, reward.playerId.toString())
        ps.setString(4, reward.type); ps.executeUpdate() == 1
    }

    @Synchronized fun abortMatch(matchId: String): Int = connection.prepareStatement(
        "UPDATE reward_outbox SET state='ABORTED', lease_until_epoch_ms=NULL WHERE match_id=? AND state IN ('PENDING','CLAIMED')"
    ).use { ps -> ps.setString(1, matchId); ps.executeUpdate() }

    private fun get(matchId: String, playerId: String, type: String): Reward? =
        connection.prepareStatement("SELECT amount,state,attempts,lease_until_epoch_ms FROM reward_outbox WHERE match_id=? AND player_id=? AND reward_type=?").use { ps ->
            ps.setString(1, matchId); ps.setString(2, playerId); ps.setString(3, type)
            ps.executeQuery().use { rs ->
                if (!rs.next()) null else Reward(matchId, UUID.fromString(playerId), type, rs.getLong(1), RewardState.valueOf(rs.getString(2)), rs.getInt(3), rs.getLong(4).let { if (rs.wasNull()) null else it })
            }
        }

    companion object { const val DEFAULT_LEASE_DURATION_MS: Long = 60_000 }
}
