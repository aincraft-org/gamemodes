package dev.jlo.gamemodes.persistence

import java.sql.Connection
import java.sql.SQLException
import java.time.Duration
import java.time.Instant

/** Versioned opaque checkpoint suitable for deterministic reconstruction by a mode adapter. */
data class MatchSnapshot(
    val matchId: String,
    val sequence: Long,
    val version: Int,
    val payload: ByteArray,
    val deadline: Instant?
) {
    init {
        require(matchId.isNotBlank())
        require(sequence >= 0)
        require(version > 0)
    }

    override fun equals(other: Any?): Boolean = other is MatchSnapshot && matchId == other.matchId && sequence == other.sequence && version == other.version && payload.contentEquals(other.payload) && deadline == other.deadline
    override fun hashCode(): Int = 31 * (31 * (31 * (31 * matchId.hashCode() + sequence.hashCode()) + version) + payload.contentHashCode()) + (deadline?.hashCode() ?: 0)
}

class StaleMatchSequenceException(message: String) : IllegalStateException(message)

class MatchSnapshotStore(
    private val connection: Connection,
    private val migrations: SqliteMigrations = SqliteMigrations()
) {
    init { migrations.apply(connection) }

    @Synchronized
    fun load(matchId: String): MatchSnapshot? = connection.prepareStatement("SELECT sequence, snapshot_version, payload, deadline_epoch_ms FROM match_snapshots WHERE match_id = ?").use { ps ->
        ps.setString(1, matchId)
        ps.executeQuery().use { rs ->
            if (!rs.next()) null else MatchSnapshot(matchId, rs.getLong(1), rs.getInt(2), rs.getBytes(3), rs.getLong(4).let { if (rs.wasNull()) null else Instant.ofEpochMilli(it) })
        }
    }

    @Synchronized
    fun save(expectedSequence: Long, snapshot: MatchSnapshot) {
        require(snapshot.sequence == expectedSequence + 1) { "Snapshot sequence must increment expected sequence" }
        connection.autoCommit = false
        try {
            val updated = connection.prepareStatement("UPDATE match_snapshots SET sequence=?, snapshot_version=?, payload=?, deadline_epoch_ms=?, updated_at_epoch_ms=? WHERE match_id=? AND sequence=?").use { ps ->
                ps.setLong(1, snapshot.sequence); ps.setInt(2, snapshot.version); ps.setBytes(3, snapshot.payload); setDeadline(ps, 4, snapshot.deadline); ps.setLong(5, System.currentTimeMillis()); ps.setString(6, snapshot.matchId); ps.setLong(7, expectedSequence); ps.executeUpdate()
            }
            if (updated == 0) {
                val existing = load(snapshot.matchId)
                if (existing != null || expectedSequence != 0L) throw StaleMatchSequenceException("Expected sequence $expectedSequence for ${snapshot.matchId}")
                connection.prepareStatement("INSERT INTO match_snapshots(match_id, sequence, snapshot_version, payload, deadline_epoch_ms, updated_at_epoch_ms) VALUES (?, ?, ?, ?, ?, ?)").use { ps ->
                    ps.setString(1, snapshot.matchId); ps.setLong(2, snapshot.sequence); ps.setInt(3, snapshot.version); ps.setBytes(4, snapshot.payload); setDeadline(ps, 5, snapshot.deadline); ps.setLong(6, System.currentTimeMillis()); ps.executeUpdate()
                }
            }
            connection.commit()
        } catch (e: SQLException) { connection.rollback(); throw e } catch (e: Throwable) { connection.rollback(); throw e } finally { connection.autoCommit = true }
    }

    private fun setDeadline(ps: java.sql.PreparedStatement, index: Int, deadline: Instant?) { if (deadline == null) ps.setNull(index, java.sql.Types.INTEGER) else ps.setLong(index, deadline.toEpochMilli()) }
}

data class RecoveredDeadline(val deadline: Instant?, val remaining: Duration, val expired: Boolean)

object EpochDeadlineRecovery {
    fun recover(deadline: Instant?, now: Instant): RecoveredDeadline {
        if (deadline == null) return RecoveredDeadline(null, Duration.ZERO, false)
        val remaining = if (now.isBefore(deadline)) Duration.between(now, deadline) else Duration.ZERO
        return RecoveredDeadline(deadline, remaining, !now.isBefore(deadline))
    }
}
