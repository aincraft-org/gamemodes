package dev.jlo.gamemodes.persistence

import java.sql.Connection
import java.time.Clock
import java.util.regex.Pattern

/** Applies classpath SQL migrations exactly once, in version order. */
class SqliteMigrations(
    private val clock: Clock = Clock.systemUTC(),
    private val resourcePrefix: String = "db/migrations/"
) {
    private val migrationName = Pattern.compile("V(\\d+)__([A-Za-z0-9_.-]+)\\.sql")

    fun apply(connection: Connection) {
        connection.createStatement().use {
            it.executeUpdate("CREATE TABLE IF NOT EXISTS schema_migrations (version INTEGER PRIMARY KEY, name TEXT NOT NULL, applied_at_epoch_ms INTEGER NOT NULL)")
        }
        val migrations = discover()
        connection.autoCommit = false
        try {
            for ((version, name, sql) in migrations) {
                val exists = connection.prepareStatement("SELECT 1 FROM schema_migrations WHERE version = ?").use { ps ->
                    ps.setInt(1, version)
                    ps.executeQuery().use { rs -> rs.next() }
                }
                if (!exists) {
                    connection.createStatement().use { statement ->
                        sql.split(';')
                            .map(String::trim)
                            .filter(String::isNotEmpty)
                            .forEach(statement::executeUpdate)
                    }
                    connection.prepareStatement("INSERT INTO schema_migrations(version, name, applied_at_epoch_ms) VALUES (?, ?, ?)").use { ps ->
                        ps.setInt(1, version)
                        ps.setString(2, name)
                        ps.setLong(3, clock.millis())
                        ps.executeUpdate()
                    }
                }
            }
            connection.commit()
        } catch (t: Throwable) {
            connection.rollback()
            throw t
        } finally {
            connection.autoCommit = true
        }
    }

    private fun discover(): List<Triple<Int, String, String>> {
        val names = listOf("V1__initial.sql", "V2__reward_outbox_leases.sql")
        return names.map { file ->
            val match = migrationName.matcher(file)
            require(match.matches()) { "Invalid migration name: $file" }
            val sql = javaClass.classLoader.getResourceAsStream(resourcePrefix + file)?.bufferedReader()?.use { it.readText() }
                ?: error("Missing migration resource: $file")
            Triple(match.group(1).toInt(), file, sql)
        }.sortedBy { it.first }
    }
}
