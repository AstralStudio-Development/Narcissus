package cn.starry.narcissus.functions.commands;

import cn.starry.narcissus.Narcissus;
import cn.starry.narcissus.functions.managers.NetworkManager;
import cn.starry.narcissus.functions.managers.PlayerManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Shared player-command dispatcher used by legacy and modern chat packets.
 *
 * <p>The current command set intentionally mirrors the historical v47 path.
 * Command output is returned as plain server messages so protocol-specific
 * packet encoding remains the responsibility of {@link NetworkManager}.</p>
 */
public final class CommandManager {

    private static final String UNKNOWN_COMMAND = "&fUnknown command.";
    private static final String CHAT_DISABLED = "&c通信功能在此服务器上不可用！";
    private static final String TIME_USAGE = "&e用法：/time <HH:mm|0-23999|sync>";

    private final Map<String, Command> commands = new LinkedHashMap<>();

    public CommandManager() {
        this.registerCommand("limbo", args -> {
            if (args.length != 0) {
                return null;
            }
            return Arrays.asList(
                    " ",
                    " ",
                    " ",
                    " ",
                    " ",
                    " ",
                    " ",
                    "&c你正在尝试加入的大厅人数已满或处于离线状态。",
                    "&e因此，你来到了Limbo，一个存在你想象中的服务器。",
                    "&d事实上，这个地方并不存在，所以你可以想呆多久就呆多久。",
                    "&6想要返回“现实”，请输入 &b/lobby&6。",
                    "&4不过，要小心，Limbo好像居住着什么东西。"
            );
        });
        this.registerCommand("version", args -> {
            if (args.length != 0) {
                return null;
            }
            return Arrays.asList(
                    "&bNarcissus &f- " + Narcissus.VERSION,
                    "&bPowered By Stalyer"
            );
        });
        // Context-aware permission checks and packet sending happen in handleChat.
        this.registerCommand("time", args -> Collections.singletonList(TIME_USAGE));
        // /lobby and /hub are intercepted by the Bungee proxy on every server
        // (including Limbo) and route players back to the configured lobby.
        // Registered here only for tab completion; the core fallback message
        // applies when no proxy handles the command.
        this.registerCommand("lobby", args -> Collections.singletonList("&e正在返回大厅…"));
        this.registerCommand("hub", args -> Collections.singletonList("&e正在返回大厅…"));
    }

    /**
     * Registers or replaces a command. Labels are matched case-insensitively.
     * A handler returns null when the supplied arguments are invalid.
     */
    public void registerCommand(String label, Command command) {
        if (label == null || label.trim().isEmpty()) {
            throw new IllegalArgumentException("Command label cannot be empty");
        }
        if (command == null) {
            throw new IllegalArgumentException("Command handler cannot be null");
        }
        this.commands.put(label.trim().toLowerCase(Locale.ROOT), command);
    }

    /**
     * Converts one client chat/command input into server feedback messages.
     * The returned list is immutable and can be tested without a live socket.
     */
    public List<String> dispatch(String input) {
        String message = input == null ? "" : input.trim();
        if (!message.startsWith("/")) {
            return Collections.singletonList(CHAT_DISABLED);
        }

        String commandLine = message.substring(1).trim();
        if (commandLine.isEmpty()) {
            return Collections.singletonList(UNKNOWN_COMMAND);
        }

        String[] parts = commandLine.split("\\s+");
        Command command = this.commands.get(parts[0].toLowerCase(Locale.ROOT));
        if (command == null) {
            return Collections.singletonList(UNKNOWN_COMMAND);
        }

        String[] args = Arrays.copyOfRange(parts, 1, parts.length);
        List<String> output = command.execute(args);
        return output == null ? Collections.singletonList(UNKNOWN_COMMAND) : output;
    }

    /** Returns the registered command labels in registration order. */
    public List<String> getCommandLabels() {
        return Collections.unmodifiableList(new ArrayList<>(this.commands.keySet()));
    }

    /**
     * Computes modern command-suggestion range and matches for one input line.
     * The returned range points at the current command token, excluding the
     * leading slash so it can be used directly by command_suggestions.
     */
    public Suggestion suggest(String input) {
        String value = input == null ? "" : input;
        int slashOffset = value.startsWith("/") ? 1 : 0;
        String commandLine = value.substring(slashOffset);
        if (commandLine.indexOf(' ') >= 0) {
            return new Suggestion(value.length(), 0, Collections.emptyList());
        }

        int start = Math.max(slashOffset, value.lastIndexOf(' ') + 1);
        String prefix = value.substring(start).toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String label : this.commands.keySet()) {
            if (label.startsWith(prefix)) {
                matches.add(label);
            }
        }
        return new Suggestion(start, value.length() - start, matches);
    }

    /** Returns legacy v47 completion strings, including the leading slash when requested. */
    public List<String> suggestLegacy(String input) {
        Suggestion suggestion = this.suggest(input);
        if (input != null && input.startsWith("/")) {
            List<String> matches = new ArrayList<>();
            for (String match : suggestion.getMatches()) {
                matches.add("/" + match);
            }
            return matches;
        }
        return suggestion.getMatches();
    }

    /** Dispatches input and sends every resulting line through the active protocol. */
    public void handleChat(NetworkManager networkManager, PlayerManager playerManager, String input) {
        if (networkManager.handleTimeCommand(playerManager, input)) {
            return;
        }
        for (String message : this.dispatch(input)) {
            networkManager.sendMessage(playerManager, message);
        }
    }

    public static final class Suggestion {
        private final int start;
        private final int length;
        private final List<String> matches;

        public Suggestion(int start, int length, List<String> matches) {
            this.start = start;
            this.length = length;
            this.matches = Collections.unmodifiableList(new ArrayList<>(matches));
        }

        public int getStart() {
            return this.start;
        }

        public int getLength() {
            return this.length;
        }

        public List<String> getMatches() {
            return this.matches;
        }
    }

    @FunctionalInterface
    public interface Command {
        /** Returns response lines, or {@code null} for invalid arguments. */
        List<String> execute(String[] args);
    }
}
