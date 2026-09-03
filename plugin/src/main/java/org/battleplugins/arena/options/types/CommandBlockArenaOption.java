package org.battleplugins.arena.options.types;

import org.battleplugins.arena.options.ArenaOption;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class CommandBlockArenaOption extends ArenaOption {
    private static final String ENABLED_KEY = "enabled";
    private static final String WHITELIST_KEY = "whitelist";

    private final Set<String> whitelist;

    public CommandBlockArenaOption(Map<String, String> params) {
        super(params, ENABLED_KEY);

        String whitelistValue = this.get(WHITELIST_KEY);
        this.whitelist = whitelistValue == null || whitelistValue.isBlank()
                ? Set.of()
                : Arrays.stream(whitelistValue.split(","))
                        .map(CommandBlockArenaOption::normalize)
                        .filter(command -> !command.isEmpty())
                        .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isEnabled() {
        return Boolean.parseBoolean(this.get(ENABLED_KEY));
    }

    public boolean isWhitelisted(String command) {
        return this.whitelist.contains(normalize(command));
    }

    private static String normalize(String command) {
        String trimmed = command.trim();
        if (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }

        return trimmed.toLowerCase(Locale.ROOT);
    }
}
