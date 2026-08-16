package dev.jlo.gamemodes.paper;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class PaperCommandExecutor implements CommandExecutor, TabCompleter {
    private final PaperMatchService service;
    private final CommandRouter router;

    public PaperCommandExecutor(PaperMatchService service) {
        this.service = service;
        this.router = new CommandRouter(service);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            if (args.length > 0 && args[0].toLowerCase(Locale.ROOT).equals("debug")) {
                sender.sendMessage(service.admin(null, "debug", Arrays.asList(args).subList(1, args.length)));
            } else {
                sender.sendMessage("This command is player-only");
            }
            return true;
        }

        List<String> input;
        String lowerLabel = label.toLowerCase(Locale.ROOT);
        if (Set.of("opr", "siege").contains(lowerLabel)) {
            input = new java.util.ArrayList<>(args.length + 1);
            input.add(lowerLabel);
            input.addAll(Arrays.asList(args));
        } else {
            input = Arrays.asList(args);
        }

        try {
            String first = input.isEmpty() ? null : input.get(0);
            if (first != null && Set.of("start", "stop", "reload", "debug", "arena")
                    .contains(first.toLowerCase(Locale.ROOT))) {
                if (!player.hasPermission("gamemodes.admin")) {
                    player.sendMessage("You do not have permission");
                    return true;
                }
                player.sendMessage(service.admin(player.getUniqueId(), first, input.subList(1, input.size())));
            } else {
                player.sendMessage(router.execute(player.getUniqueId(), input));
            }
        } catch (RuntimeException failure) {
            String message = failure.getMessage();
            player.sendMessage(message != null ? message : "Gamemode operation failed");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("join", "leave", "ready", "status", "team", "opr", "siege", "start", "stop", "reload", "debug", "arena")
                    .stream()
                    .filter(value -> value.startsWith(args[0]))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("join")) {
            return List.of("opr", "siege");
        }
        return Collections.emptyList();
    }
}
