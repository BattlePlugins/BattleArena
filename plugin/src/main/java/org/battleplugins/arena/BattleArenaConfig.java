package org.battleplugins.arena;

import org.battleplugins.arena.competition.event.EventOptions;
import org.battleplugins.arena.config.ArenaOption;
import org.battleplugins.arena.config.Updater;
import org.battleplugins.arena.config.updater.ConfigUpdater;
import org.battleplugins.arena.config.updater.UpdaterStep;

import java.util.List;
import java.util.Map;

/**
 * Represents the BattleArena configuration.
 */
@Updater(BattleArenaConfig.Updater.class)
public class BattleArenaConfig {

    @ArenaOption(name = "config-version", description = "The version of the config.", required = true)
    private String configVersion;

    @ArenaOption(name = "backup-inventories", description = "Whether player inventories should be backed up when joining competitions.", required = true)
    private boolean backupInventories;

    @ArenaOption(name = "max-backups", description = "The maximum number of backups to save for each player.", required = true)
    private int maxBackups;

    @ArenaOption(name = "max-dynamic-maps", description = "The maximum number of dynamic maps an Arena can have allocated at once.", required = true)
    private int maxDynamicMaps;

    @ArenaOption(name = "randomized-arena-join", description = "Whether players should be randomly placed in an Arena when joining without specifying a map.", required = true)
    private boolean randomizedArenaJoin;

    @ArenaOption(name = "use-schematic", description = "Whether creating a dynamic arena should try to use a schematic if one is available first.", required = true)
    private boolean schematicUsage;

    @ArenaOption(name = "center-dynamic-arena", description = "If true, pastes the dynamic arena centered at 0,0,0.", required = true)
    private boolean centerDynamicArena;

    @ArenaOption(name = "disabled-modules", description = "Modules that are disabled by default.")
    private List<String> disabledModules;

    @ArenaOption(name = "events", description = "The configured events.", required = true)
    private Map<String, List<EventOptions>> events;

    @ArenaOption(name = "debug-mode", description = "Whether debug mode is enabled.")
    private boolean debugMode;

    public String getConfigVersion() {
        return this.configVersion;
    }

    public boolean isBackupInventories() {
        return this.backupInventories;
    }

    public int getMaxBackups() {
        return this.maxBackups;
    }

    public int getMaxDynamicMaps() {
        return this.maxDynamicMaps;
    }

    public boolean isRandomizedArenaJoin() {
        return this.randomizedArenaJoin;
    }

    public boolean isSchematicUsage() {
        return this.schematicUsage;
    }

    public boolean centerDynamicArena() {
        return this.centerDynamicArena;
    }

    public List<String> getDisabledModules() {
        return this.disabledModules == null ? List.of() : List.copyOf(this.disabledModules);
    }

    public Map<String, List<EventOptions>> getEvents() {
        return Map.copyOf(this.events);
    }

    public boolean isDebugMode() {
        return this.debugMode;
    }

    public static class Updater implements ConfigUpdater<BattleArenaConfig> {

        @Override
        public Map<String, UpdaterStep<BattleArenaConfig>> buildUpdaters() {
            return Map.of(
                    "3.1", (config, instance) -> {
                        config.set("randomized-arena-join", false);
                        config.setComments("randomized-arena-join", List.of(
                                "Whether joining an arena using /<arena> join without specifying a map should",
                                "randomly pick an arena, rather than joining the most convenient one. Competitions",
                                "with players waiting will always be prioritized though, even with this setting",
                                "enabled."
                        ));
                    });
        }
    }
}
