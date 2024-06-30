package org.battleplugins.arena.competition.map.options;

import org.battleplugins.arena.config.ArenaOption;
import org.battleplugins.arena.util.PositionWithRotation;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class Spawns {
    @ArenaOption(name = "waitroom", description = "The waitroom spawn.")
    private PositionWithRotation waitroomSpawn;

    @ArenaOption(name = "spectator", description = "The spectator spawn.")
    private PositionWithRotation spectatorSpawn;

    @ArenaOption(name = "team-spawns", description = "The spawns options for each team.")
    private Map<String, TeamSpawns> teamSpawns;

    public Spawns() {
    }

    public Spawns(@Nullable PositionWithRotation waitroomSpawn, @Nullable PositionWithRotation spectatorSpawn, @Nullable Map<String, TeamSpawns> teamSpawns) {
        this.waitroomSpawn = waitroomSpawn;
        this.spectatorSpawn = spectatorSpawn;
        this.teamSpawns = teamSpawns;
    }

    @Nullable
    public final PositionWithRotation getWaitroomSpawn() {
        return this.waitroomSpawn;
    }

    @Nullable
    public final PositionWithRotation getSpectatorSpawn() {
        return this.spectatorSpawn;
    }

    @Nullable
    public final Map<String, TeamSpawns> getTeamSpawns() {
        return this.teamSpawns;
    }
}