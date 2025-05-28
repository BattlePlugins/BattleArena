package org.battleplugins.arena.editor;

import io.papermc.paper.math.Position;
import org.battleplugins.arena.BattleArena;
import org.battleplugins.arena.competition.Competition;
import org.battleplugins.arena.competition.CompetitionType;
import org.battleplugins.arena.competition.map.LiveCompetitionMap;
import org.battleplugins.arena.competition.map.MapType;
import org.battleplugins.arena.competition.map.options.Bounds;
import org.battleplugins.arena.competition.map.options.Spawns;
import org.battleplugins.arena.competition.map.options.TeamSpawns;
import org.battleplugins.arena.config.ParseException;
import org.battleplugins.arena.editor.context.MapCreateContext;
import org.battleplugins.arena.editor.stage.EnumTextInputStage;
import org.battleplugins.arena.editor.stage.PositionInputStage;
import org.battleplugins.arena.editor.stage.SpawnInputStage;
import org.battleplugins.arena.editor.stage.TeamSpawnInputStage;
import org.battleplugins.arena.editor.stage.TextInputStage;
import org.battleplugins.arena.editor.type.MapOption;
import org.battleplugins.arena.messages.Messages;
import org.battleplugins.arena.team.ArenaTeam;
import org.battleplugins.arena.util.PositionWithRotation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ArenaEditorWizards {
    public static final ArenaEditorWizard<MapCreateContext> MAP_CREATION = createWizard(MapCreateContext::new)
            .addStage(MapOption.NAME, new TextInputStage<>(
                    Messages.MAP_CREATE_NAME,
                    Messages.MAP_EXISTS,
                    (ctx, name) -> BattleArena.getInstance().getMap(ctx.getArena(), name) == null,
                    ctx -> ctx::setMapName
                    )
            )
            .addStage(MapOption.TYPE, new EnumTextInputStage<>(
                    Messages.MAP_SET_TYPE,
                    MapType.class,
                    ctx -> ctx::setMapType
            ))
            .addStage(MapOption.MIN_POS, new PositionInputStage<>(Messages.MAP_SET_MIN_POSITION, ctx -> ctx::setMin))
            .addStage(MapOption.MAX_POS, new PositionInputStage<>(Messages.MAP_SET_MAX_POSITION, ctx -> ctx::setMax))
            .addStage(MapOption.WAITROOM_SPAWN, new SpawnInputStage<>(Messages.MAP_SET_WAITROOM_SPAWN, "waitroom", ctx -> ctx::setWaitroomSpawn))
            .addStage(MapOption.SPECTATOR_SPAWN, new SpawnInputStage<>(Messages.MAP_SET_SPECTATOR_SPAWN, "spectator", ctx -> ctx::setSpectatorSpawn))
            .addStage(MapOption.TEAM_SPAWNS, new TeamSpawnInputStage<>(
                    Messages.MAP_ADD_TEAM_SPAWN,
                    "spawn",
                    ctx -> {
                        if (ctx.hasValidTeamSpawns()) {
                            return true;
                        }

                        // Send message to player that they need to finish adding spawns
                        List<ArenaTeam> missingTeams = ctx.getMissingTeams();
                        List<String> missingTeamNames = missingTeams.stream().map(ArenaTeam::getName).toList();
                        Messages.MAP_MISSING_TEAM_SPAWNS.send(ctx.getPlayer(), String.join(", ", missingTeamNames));
                        return false;
                    },
                    ctx -> (team, loc) -> ctx.addSpawn(team, new PositionWithRotation(loc)),
                    ctx -> team -> {
                        List<PositionWithRotation> teams = ctx.getSpawns().get(team);
                        if (teams == null) {
                            return;
                        }

                        teams.clear();
                    }
            ))
            .onEditComplete(ctx -> {
                LiveCompetitionMap map = BattleArena.getInstance().getMap(ctx.getArena(), ctx.getMapName());
                if (map == null) {
                    // Should not get here but *just* incase
                    Messages.NO_ARENA_WITH_NAME.send(ctx.getPlayer());
                    return;
                }

                ctx.saveTo(map);

                try {
                    map.save();
                } catch (ParseException | IOException e) {
                    BattleArena.getInstance().error("Failed to save map file for arena {}", ctx.getArena().getName(), e);
                    Messages.MAP_FAILED_TO_SAVE.send(ctx.getPlayer(), map.getName());
                    return;
                }

                Messages.MAP_EDITED.send(ctx.getPlayer(), map.getName());
            })
            .onCreationComplete(ctx -> {
                Position min = ctx.getMin();
                Position max = ctx.getMax();

                Bounds bounds = new Bounds(min, max);

                Map<String, TeamSpawns> teamSpawns = new HashMap<>(ctx.getSpawns().size());
                ctx.getSpawns().forEach((team, spawns) -> teamSpawns.put(team, new TeamSpawns(spawns)));
                Spawns spawns = new Spawns(ctx.getWaitroomSpawn(), ctx.getSpectatorSpawn(), teamSpawns);

                LiveCompetitionMap map = ctx.getArena().getMapFactory().create(ctx.getMapName(), ctx.getArena(), ctx.getMapType(), ctx.getPlayer().getWorld().getName(), bounds, spawns);
                map.postProcess(); // Call post process to ensure all data is loaded

                BattleArena.getInstance().addArenaMap(ctx.getArena(), map);

                // If our competition is a match, create it
                if (ctx.getArena().getType() == CompetitionType.MATCH && map.getType() == MapType.STATIC) {
                    Competition<?> competition = map.createCompetition(ctx.getArena());
                    BattleArena.getInstance().addCompetition(ctx.getArena(), competition);
                }

                Path mapPath = ctx.getArena().getMapPath().resolve(map.getName().toLowerCase(Locale.ROOT) + ".yml");
                if (Files.exists(mapPath)) {
                    // Should not get here but *just* incase
                    Messages.MAP_EXISTS.send(ctx.getPlayer(), map.getName());
                    return;
                }

                try {
                    map.save();
                } catch (ParseException | IOException e) {
                    BattleArena.getInstance().error("Failed to create map file for arena {}", ctx.getArena().getName(), e);
                    Messages.MAP_FAILED_TO_SAVE.send(ctx.getPlayer(), map.getName());
                    return;
                }

                Messages.MAP_CREATED.send(ctx.getPlayer(), map.getName(), ctx.getArena().getName());
            });

    public static <E extends EditorContext<E>> ArenaEditorWizard<E> createWizard(ArenaEditorWizard.ContextFactory<E> contextFactory) {
        return new ArenaEditorWizard<>(BattleArena.getInstance(), contextFactory);
    }
}
