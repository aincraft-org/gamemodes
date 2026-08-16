package dev.jlo.gamemodes.paper;

import java.util.List;
import java.util.UUID;

/** Pure command parser shared by Bukkit executor and fake-based tests. */
public final class CommandRouter {
    private final CommandService service;

    public CommandRouter(CommandService service) {
        this.service = service;
    }

    public String execute(UUID player, List<String> arguments) {
        if (arguments.isEmpty()) {
            return "Usage: join <opr|siege> [arena], leave, ready, status, team";
        }
        String command = arguments.getFirst().toLowerCase();
        return switch (command) {
            case "opr", "siege" -> join(player, Mode.parse(command), getOrNull(arguments, 1));
            case "join" -> {
                String modeValue = getOrNull(arguments, 1);
                Mode mode = modeValue == null ? null : Mode.parse(modeValue);
                if (mode == null) {
                    yield "Usage: join <opr|siege> [arena]";
                }
                yield join(player, mode, getOrNull(arguments, 2));
            }
            case "leave" -> service.leave(player) ? "left" : "not in a queue or match";
            case "ready" -> service.ready(player) ? "ready" : "not in a queue or match";
            case "status" -> service.status(player);
            case "team" -> {
                TeamAssignment assignment = service.team(player);
                yield assignment != null ? "team " + assignment.name() : "not in a queue or match";
            }
            default -> "Unknown command: " + command;
        };
    }

    private String join(UUID player, Mode mode, String arena) {
        service.join(player, mode, arena);
        return "joined " + mode.name();
    }

    private static <T> T getOrNull(List<T> values, int index) {
        return index < values.size() ? values.get(index) : null;
    }
}
