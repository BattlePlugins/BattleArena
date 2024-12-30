package org.battleplugins.arena.module.autoarena;

import org.battleplugins.arena.Arena;
import org.battleplugins.arena.ArenaPlayer;
import org.battleplugins.arena.BattleArenaApi;
import org.battleplugins.arena.competition.Competition;
import org.battleplugins.arena.competition.LiveCompetition;
import org.battleplugins.arena.competition.PlayerRole;
import org.battleplugins.arena.competition.map.options.Bounds;
import org.battleplugins.arena.event.player.ArenaLeaveEvent;
import org.battleplugins.arena.module.ArenaModule;
import org.battleplugins.arena.module.ArenaModuleInitializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.List;

/**
 * A module that automatically places players into an arena when they walk into an arena's bounds.
 */
@ArenaModule(id = AutoArena.ID, name = "Auto Arena", description = "Places players into an arena when they walk into an arena's bounds.", authors = "BattlePlugins")
public class AutoArena implements ArenaModuleInitializer {
    public static final String ID = "auto-arena";

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        ArenaPlayer player = ArenaPlayer.getArenaPlayer(event.getPlayer());

        // Player is in an arena - let's check if their current arena has this module
        // enabled and if so, remove them from the arena if they are outside the bounds
        if (player != null && player.getArena().isModuleEnabled(ID)) {
            Bounds bounds = player.getCompetition().getMap().getBounds();
            if (bounds != null && !bounds.isInside(event.getTo())) {
                player.getCompetition().leave(player, ArenaLeaveEvent.Cause.PLUGIN);
            }

            return;
        }

        // Player is not in an arena - let's check if they are in the bounds of a map
        for (Arena arena : BattleArenaApi.get().getArenas()) {
            if (!arena.isModuleEnabled(ID)) {
                continue;
            }

            List<Competition<?>> competitions = BattleArenaApi.get().getCompetitions(arena);
            for (Competition<?> competition : competitions) {
                if (!(competition instanceof LiveCompetition<?> liveCompetition)) {
                    continue;
                }

                Bounds bounds = liveCompetition.getMap().getBounds();
                if (liveCompetition.getMap().getWorld().equals(event.getPlayer().getWorld()) && bounds != null && bounds.isInside(event.getTo())) {
                    liveCompetition.join(event.getPlayer(), PlayerRole.PLAYING);
                    return;
                }
            }
        }
    }
}
