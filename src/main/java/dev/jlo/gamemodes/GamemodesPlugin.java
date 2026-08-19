package dev.jlo.gamemodes;

import dev.jlo.gamemodes.domain.common.DefaultMatchCoordinator;
import dev.jlo.gamemodes.paper.PaperAdapter;
import dev.jlo.gamemodes.persistence.SqliteMigrations;
import dev.jlo.gamemodes.player.PendingRestoreRepository;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;

public class GamemodesPlugin extends JavaPlugin {
    private DefaultMatchCoordinator matchCoordinator;
    private PaperAdapter paperAdapter;
    private Connection storageConnection;

    public DefaultMatchCoordinator getMatchCoordinator() {
        if (matchCoordinator == null) {
            throw new IllegalStateException("Property matchCoordinator has not been initialized");
        }
        return matchCoordinator;
    }

    private void setMatchCoordinator(DefaultMatchCoordinator matchCoordinator) {
        this.matchCoordinator = matchCoordinator;
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        setMatchCoordinator(new DefaultMatchCoordinator());
        storageConnection = configureStorage();
        paperAdapter = new PaperAdapter(this, new PendingRestoreRepository(storageConnection));
        paperAdapter.enable();
        getLogger().info("Gamemodes enabled");
    }

    private Connection configureStorage() {
        String configured = getConfig().getString("global.storage");
        if (configured == null) {
            throw new IllegalArgumentException("global.storage must be configured");
        }
        Path dataDirectory = getDataFolder().toPath().normalize();
        Path db = dataDirectory.resolve(configured).normalize();
        if (!db.startsWith(dataDirectory)) {
            throw new IllegalArgumentException("Storage path escapes plugin data directory");
        }
        try {
            Files.createDirectories(Objects.requireNonNull(db.getParent(), "db parent"));
            Connection connection = DriverManager.getConnection("jdbc:sqlite:" + db);
            try {
                new SqliteMigrations().apply(connection);
                return connection;
            } catch (SQLException exception) {
                try {
                    connection.close();
                } catch (SQLException closeException) {
                    exception.addSuppressed(closeException);
                }
                throw new IllegalStateException("Failed to configure storage", exception);
            }
        } catch (IOException | SQLException exception) {
            throw new IllegalStateException("Failed to configure storage", exception);
        }
    }

    @Override
    public void onDisable() {
        if (paperAdapter != null) {
            paperAdapter.disable();
        }
        if (storageConnection != null) {
            try {
                storageConnection.close();
            } catch (SQLException exception) {
                getLogger().severe("Failed to close storage connection: " + exception.getMessage());
            }
        }
        getLogger().info("Gamemodes disabled");
    }
}
