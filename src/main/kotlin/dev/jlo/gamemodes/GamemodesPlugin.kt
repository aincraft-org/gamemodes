package dev.jlo.gamemodes

import dev.jlo.gamemodes.domain.common.DefaultMatchCoordinator
import dev.jlo.gamemodes.paper.PaperAdapter
import dev.jlo.gamemodes.player.PendingRestoreRepository
import java.sql.Connection
import java.sql.DriverManager
import java.nio.file.Files
import org.bukkit.plugin.java.JavaPlugin

class GamemodesPlugin : JavaPlugin() {
    lateinit var matchCoordinator: DefaultMatchCoordinator
        private set
    private lateinit var paperAdapter: PaperAdapter
    private lateinit var storageConnection: Connection

    override fun onEnable() {
        saveDefaultConfig()
        matchCoordinator = DefaultMatchCoordinator()
        storageConnection = configureStorage()
        paperAdapter = PaperAdapter(this, PendingRestoreRepository(storageConnection))
        paperAdapter.enable()
        logger.info("Gamemodes enabled")
    }

    private fun configureStorage(): Connection {
        val configured = requireNotNull(config.getString("global.storage")) {
            "global.storage must be configured"
        }
        val db = dataFolder.toPath().resolve(configured).normalize()
        require(db.startsWith(dataFolder.toPath().normalize())) { "Storage path escapes plugin data directory" }
        Files.createDirectories(requireNotNull(db.parent))
        return DriverManager.getConnection("jdbc:sqlite:$db").also {
            dev.jlo.gamemodes.persistence.SqliteMigrations().apply(it)
        }
    }

    override fun onDisable() {
        if (::paperAdapter.isInitialized) paperAdapter.disable()
        if (::storageConnection.isInitialized) storageConnection.close()
        logger.info("Gamemodes disabled")
    }
}
