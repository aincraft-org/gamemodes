package dev.jlo.gamemodes.paper;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class PaperUi {
    private final Plugin plugin;
    private final Map<UUID, BossBar> bars = new HashMap<>();

    public PaperUi(Plugin plugin) {
        this.plugin = plugin;
    }

    public void update(Player player, MatchSession session) {
        String teamName = session.teamOf(player.getUniqueId()) != null
                ? session.teamOf(player.getUniqueId()).getName()
                : "?";
        player.sendActionBar(session.getMode().getName() + " • " + session.getPhase().getName() + " • team " + teamName);
        BossBar bar = bars.computeIfAbsent(player.getUniqueId(), ignored -> {
            BossBar created = Bukkit.createBossBar(
                    session.getMode().getName() + " — " + session.getArenaId(),
                    BarColor.BLUE,
                    BarStyle.SOLID);
            created.addPlayer(player);
            return created;
        });
        bar.setTitle(session.getMode().getName() + " — " + session.getPhase().getName());
        bar.setProgress(session.getPhase().getName().equals("ACTIVE") ? 1.0 : 0.5);
    }

    public void clear(Player player) {
        BossBar bar = bars.remove(player.getUniqueId());
        if (bar != null) {
            bar.removeAll();
        }
        if (Bukkit.getScoreboardManager() != null) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    public void clearAll() {
        for (BossBar bar : bars.values()) {
            bar.removeAll();
        }
        bars.clear();
    }
}
