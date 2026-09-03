package org.battleplugins.arena.options.types;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandBlockArenaOptionTest {

    @Test
    void whitelistedCommandIsAllowed() {
        CommandBlockArenaOption option = new CommandBlockArenaOption(Map.of(
                "enabled", "true",
                "whitelist", "msg,tpa"
        ));

        assertTrue(option.isWhitelisted("msg"));
    }

    @Test
    void nonWhitelistedCommandIsNotAllowed() {
        CommandBlockArenaOption option = new CommandBlockArenaOption(Map.of(
                "enabled", "true",
                "whitelist", "msg,tpa"
        ));

        assertFalse(option.isWhitelisted("gamemode"));
    }

    @Test
    void onlyConfiguredCommandsAreWhitelisted() {
        CommandBlockArenaOption option = new CommandBlockArenaOption(Map.of(
                "enabled", "true",
                "whitelist", "spawn"
        ));

        assertTrue(option.isWhitelisted("spawn"));
        assertFalse(option.isWhitelisted("msg"));
    }

    @Test
    void whitelistMatchIsCaseInsensitive() {
        CommandBlockArenaOption option = new CommandBlockArenaOption(Map.of(
                "enabled", "true",
                "whitelist", "MSG"
        ));

        assertTrue(option.isWhitelisted("msg"));
    }

    @Test
    void whitelistEntriesWithLeadingSlashAreNormalized() {
        CommandBlockArenaOption option = new CommandBlockArenaOption(Map.of(
                "enabled", "true",
                "whitelist", "/msg"
        ));

        assertTrue(option.isWhitelisted("msg"));
    }

    @Test
    void missingWhitelistMeansNothingIsWhitelisted() {
        CommandBlockArenaOption option = new CommandBlockArenaOption(Map.of(
                "enabled", "true"
        ));

        assertFalse(option.isWhitelisted("msg"));
    }

    @Test
    void isEnabledReflectsEnabledParam() {
        CommandBlockArenaOption enabled = new CommandBlockArenaOption(Map.of("enabled", "true"));
        CommandBlockArenaOption disabled = new CommandBlockArenaOption(Map.of("enabled", "false"));

        assertTrue(enabled.isEnabled());
        assertFalse(disabled.isEnabled());
    }
}
