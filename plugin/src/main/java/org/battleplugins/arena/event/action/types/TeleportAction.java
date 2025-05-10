package org.battleplugins.arena.event.action.types;

import org.battleplugins.arena.ArenaPlayer;
import org.battleplugins.arena.competition.Competition;
import org.battleplugins.arena.competition.map.MapType;
import org.battleplugins.arena.competition.map.options.Bounds;
import org.bukkit.util.Vector;
import org.battleplugins.arena.competition.map.options.TeamSpawns;
import org.battleplugins.arena.event.action.EventAction;
import org.battleplugins.arena.resolver.Resolvable;
import org.battleplugins.arena.util.PositionWithRotation;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class TeleportAction extends EventAction {
    private static final String LOCATION_KEY = "location";
    private static final String RANDOM = "random";

    private final Map<Competition<?>, Integer> spawnTeleportIndexQueue = new WeakHashMap<>();

    public TeleportAction(Map<String, String> params) {
        super(params, LOCATION_KEY);
    }

    @Override
    public void call(ArenaPlayer arenaPlayer, Resolvable resolvable) {
        Player player = arenaPlayer.getPlayer();
        TeleportLocation location = TeleportLocation.valueOf(this.get(LOCATION_KEY).toUpperCase(Locale.ROOT));
        boolean randomized = Boolean.parseBoolean(this.getOrDefault(RANDOM, "false"));

        boolean isDynamic = arenaPlayer.getCompetition().getMap().getType() == MapType.DYNAMIC;
        Bounds bounds = arenaPlayer.getCompetition().getMap().getBounds();

        Vector center = new Vector(
            (bounds.getMinX() + bounds.getMaxX()) / 2.0,
            (bounds.getMinY() + bounds.getMaxY()) / 2.0,
            (bounds.getMinZ() + bounds.getMaxZ()) / 2.0
        );

        PositionWithRotation pos = switch (location) {
            case LAST_LOCATION:
                Location lastLocation = arenaPlayer.getStorage().getLastLocation();
                if (lastLocation != null) {
                    yield new PositionWithRotation(lastLocation.getX(), lastLocation.getY(), lastLocation.getZ(), lastLocation.getYaw(), lastLocation.getPitch());
                } else {
                    yield null;
                }
            case WAITROOM:
                PositionWithRotation waitroomSpawn = arenaPlayer.getCompetition().getMap().getSpawns().getWaitroomSpawn();
                yield isDynamic ? offsetPosition(waitroomSpawn, center) : waitroomSpawn;
            case SPECTATOR:
                PositionWithRotation spectatorSpawn = arenaPlayer.getCompetition().getMap().getSpawns().getSpectatorSpawn();
                yield isDynamic ? offsetPosition(spectatorSpawn, center) : spectatorSpawn;
            case TEAM_SPAWN:
                Map<String, TeamSpawns> teamSpawns = arenaPlayer.getCompetition().getMap().getSpawns().getTeamSpawns();
                if (teamSpawns == null) {
                    throw new IllegalArgumentException("Team spawns not defined for competition");
                }

                if (arenaPlayer.getTeam() == null) {
                    throw new IllegalArgumentException("Team not defined for player. Ensure that the 'join-random-team' action is specified so players that are not on a team get placed on one.");
                }

                String teamName = arenaPlayer.getTeam().getName();
                if (!teamSpawns.containsKey(teamName)) {
                    throw new IllegalArgumentException("Team spawns not defined for team " + teamName);
                }

                List<PositionWithRotation> spawns = teamSpawns.get(teamName).getSpawns();

                // Fast track if there is only one spawn
                if (spawns.size() == 1) {
                    PositionWithRotation spawn = spawns.get(0);
                    yield isDynamic ? offsetPosition(spawn, center) : spawn;
                }

                if (randomized) {
                    PositionWithRotation spawn = spawns.get(ThreadLocalRandom.current().nextInt(spawns.size()));
                    yield isDynamic ? offsetPosition(spawn, center) : spawn;
                }

                // Get the spawn index for the team and increment it
                int spawnIndex = this.spawnTeleportIndexQueue.getOrDefault(arenaPlayer.getCompetition(), 0);
                this.spawnTeleportIndexQueue.put(arenaPlayer.getCompetition(), spawnIndex + 1);

                // Get the spawn at the index
                PositionWithRotation spawn = spawns.get(spawnIndex % spawns.size());
                yield isDynamic ? offsetPosition(spawn, center) : spawn;
        };

        if (pos == null) {
            throw new IllegalArgumentException("Position not defined for location " + location);
        }

        player.teleport(pos.toLocation(arenaPlayer.getCompetition().getMap().getWorld()));
    }

    public enum TeleportLocation {
        WAITROOM,
        SPECTATOR,
        TEAM_SPAWN,
        LAST_LOCATION
    }

    private PositionWithRotation offsetPosition(PositionWithRotation base, Vector center) {
        return new PositionWithRotation(
            base.getX() - center.getX(),
            base.getY() - center.getY(),
            base.getZ() - center.getZ(),
            base.getYaw(),
            base.getPitch()
        );
    }
}
