package dev.jlo.gamemodes.player
import dev.jlo.gamemodes.persistence.SqliteMigrations

import java.sql.Connection
import java.time.Clock
import java.time.Instant
import java.util.UUID

/** Core-only player state; all values are plain serializable primitives, never Bukkit objects. */
data class PlayerSnapshot(
    val playerId: UUID,
    val inventory: List<ItemStackSnapshot>,
    val cursor: ItemStackSnapshot?,
    val armor: List<ItemStackSnapshot>,
    val offhand: ItemStackSnapshot?,
    val effects: List<EffectSnapshot>,
    val attributes: Map<String, Double>,
    val health: Double,
    val food: Int,
    val saturation: Double,
    val experience: Float,
    val level: Int,
    val gameMode: String,
    val allowFlight: Boolean,
    val flying: Boolean,
    val returnLocation: LocationSnapshot?
) {
    fun encode(): ByteArray = PlayerSnapshotCodec.encode(this)
    companion object { fun decode(bytes: ByteArray): PlayerSnapshot = PlayerSnapshotCodec.decode(bytes) }
}

data class ItemStackSnapshot(val material: String, val amount: Int, val metadata: String = "")
data class EffectSnapshot(val type: String, val amplifier: Int, val durationTicks: Int, val ambient: Boolean, val particles: Boolean, val icon: Boolean)
data class LocationSnapshot(val world: String, val x: Double, val y: Double, val z: Double, val yaw: Float, val pitch: Float)

object PlayerSnapshotCodec {
    private const val VERSION = 1
    fun encode(snapshot: PlayerSnapshot): ByteArray {
        val fields = listOf(snapshot.playerId.toString(), snapshot.inventory.joinToString(";") { item(it) }, snapshot.cursor?.let(::item) ?: "", snapshot.armor.joinToString(";") { item(it) }, snapshot.offhand?.let(::item) ?: "", snapshot.effects.joinToString(";") { effect(it) }, snapshot.attributes.toSortedMap().entries.joinToString(";") { "${esc(it.key)}=${it.value}" }, snapshot.health.toString(), snapshot.food.toString(), snapshot.saturation.toString(), snapshot.experience.toString(), snapshot.level.toString(), esc(snapshot.gameMode), snapshot.allowFlight.toString(), snapshot.flying.toString(), snapshot.returnLocation?.let(::location) ?: "")
        return ("$VERSION|" + fields.joinToString("|", transform = ::esc)).toByteArray(Charsets.UTF_8)
    }
    fun decode(bytes: ByteArray): PlayerSnapshot {
        val raw = bytes.toString(Charsets.UTF_8); val parts = split(raw); require(parts.size == 17 && parts[0] == VERSION.toString()) { "Unsupported player snapshot" }
        val attributes: Map<String, Double> = if (parts[7].isEmpty()) emptyMap() else parts[7].split(';').associate { val p = it.split('=', limit = 2); unesc(p[0]) to p[1].toDouble() }
        val location = parts[16].ifEmpty { null }?.let { val p = split(it, ','); require(p.size == 6); LocationSnapshot(unesc(p[0]), p[1].toDouble(), p[2].toDouble(), p[3].toDouble(), p[4].toFloat(), p[5].toFloat()) }
        return PlayerSnapshot(UUID.fromString(parts[1]), parseItems(parts[2]), parts[3].ifEmpty { null }?.let(::parseItem), parseItems(parts[4]), parts[5].ifEmpty { null }?.let(::parseItem), if (parts[6].isEmpty()) emptyList() else parts[6].split(';').map(::parseEffect), attributes, parts[8].toDouble(), parts[9].toInt(), parts[10].toDouble(), parts[11].toFloat(), parts[12].toInt(), unesc(parts[13]), parts[14].toBooleanStrict(), parts[15].toBooleanStrict(), location)
    }
    private fun parseItem(s: String): ItemStackSnapshot { val p = split(s, ','); require(p.size == 3); return ItemStackSnapshot(unesc(p[0]), p[1].toInt(), unesc(p[2])) }
    private fun location(l: LocationSnapshot) = listOf(l.world, l.x.toString(), l.y.toString(), l.z.toString(), l.yaw.toString(), l.pitch.toString()).joinToString(",", transform = { esc(it) })
    private fun parseItems(s: String) = if (s.isEmpty()) emptyList() else s.split(';').map(::parseItem)
    private fun item(i: ItemStackSnapshot) = listOf(i.material, i.amount.toString(), i.metadata).joinToString(",", transform = { esc(it) })
    private fun parseEffect(s: String): EffectSnapshot { val p = split(s, ','); require(p.size == 6); return EffectSnapshot(unesc(p[0]), p[1].toInt(), p[2].toInt(), p[3].toBooleanStrict(), p[4].toBooleanStrict(), p[5].toBooleanStrict()) }
    private fun effect(e: EffectSnapshot) = listOf(e.type, e.amplifier.toString(), e.durationTicks.toString(), e.ambient.toString(), e.particles.toString(), e.icon.toString()).joinToString(",", transform = { esc(it) })
    private fun esc(s: String) = java.net.URLEncoder.encode(s, Charsets.UTF_8)
    private fun unesc(s: String) = java.net.URLDecoder.decode(s, Charsets.UTF_8)
    private fun split(s: String, delimiter: Char = '|'): List<String> = s.split(delimiter).map(::unesc)
}

enum class RestoreState { PENDING, CLAIMED, RESTORED, FAILED, CANCELLED }

data class PendingRestore(val playerId: UUID, val snapshot: PlayerSnapshot, val state: RestoreState, val attempts: Int)

class PendingRestoreRepository(private val connection: Connection, private val clock: Clock = Clock.systemUTC()) {
    init { SqliteMigrations(clock).apply(connection) }
    @Synchronized fun put(snapshot: PlayerSnapshot) { connection.prepareStatement("INSERT INTO player_restores(player_id, restore_version, payload, state, attempts, available_at_epoch_ms, updated_at_epoch_ms) VALUES (?,1,?,'PENDING',0,?,?) ON CONFLICT(player_id) DO UPDATE SET payload=excluded.payload,state='PENDING',attempts=0,available_at_epoch_ms=excluded.available_at_epoch_ms,updated_at_epoch_ms=excluded.updated_at_epoch_ms").use { ps -> val now=clock.millis(); ps.setString(1,snapshot.playerId.toString()); ps.setBytes(2,snapshot.encode()); ps.setLong(3,now); ps.setLong(4,now); ps.executeUpdate() } }
    @Synchronized fun claim(playerId: UUID): PendingRestore? = connection.prepareStatement("UPDATE player_restores SET state='CLAIMED', attempts=attempts+1, claimed_at_epoch_ms=?, updated_at_epoch_ms=? WHERE player_id=? AND state IN ('PENDING','FAILED') AND available_at_epoch_ms<=?").use { ps -> val now=clock.millis(); ps.setLong(1,now); ps.setLong(2,now); ps.setString(3,playerId.toString()); ps.setLong(4,now); if(ps.executeUpdate()==0) null else get(playerId) }
    @Synchronized fun markRestored(playerId: UUID): Boolean = transition(playerId,"RESTORED")
    @Synchronized fun markFailed(playerId: UUID, error: String): Boolean = connection.prepareStatement("UPDATE player_restores SET state='FAILED', last_error=?, updated_at_epoch_ms=? WHERE player_id=? AND state='CLAIMED'").use { ps -> ps.setString(1,error); ps.setLong(2,clock.millis()); ps.setString(3,playerId.toString()); ps.executeUpdate()==1 }
    @Synchronized fun cancel(playerId: UUID): Boolean = transition(playerId,"CANCELLED")
    fun get(playerId: UUID): PendingRestore? = connection.prepareStatement("SELECT payload,state,attempts FROM player_restores WHERE player_id=?").use { ps -> ps.setString(1,playerId.toString()); ps.executeQuery().use { rs -> if(!rs.next()) null else PendingRestore(playerId,PlayerSnapshot.decode(rs.getBytes(1)),RestoreState.valueOf(rs.getString(2)),rs.getInt(3)) } }
    private fun transition(id: UUID, state: String) = connection.prepareStatement("UPDATE player_restores SET state=?, completed_at_epoch_ms=?, updated_at_epoch_ms=? WHERE player_id=? AND state='CLAIMED'").use { ps -> val now=clock.millis(); ps.setString(1,state); ps.setLong(2,now); ps.setLong(3,now); ps.setString(4,id.toString()); ps.executeUpdate()==1 }
}
