package org.battleplugins.arena.competition;

import org.battleplugins.arena.Arena;
import org.battleplugins.arena.ArenaPlayer;
import org.battleplugins.arena.competition.map.MapType;
import org.battleplugins.arena.competition.phase.phases.VictoryPhase;
import org.battleplugins.arena.event.ArenaEventHandler;
import org.battleplugins.arena.event.ArenaListener;
import org.battleplugins.arena.event.arena.ArenaPhaseCompleteEvent;
import org.battleplugins.arena.event.player.ArenaDeathEvent;
import org.battleplugins.arena.event.player.ArenaKillEvent;
import org.battleplugins.arena.event.player.ArenaLeaveEvent;
import org.battleplugins.arena.event.player.ArenaRespawnEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.Set;

class CompetitionListener<T extends Competition<T>> implements ArenaListener, CompetitionLike<T> {

    private final LiveCompetition<T> competition;

    public CompetitionListener(LiveCompetition<T> competition) {
        this.competition = competition;
    }

    @ArenaEventHandler(priority = EventPriority.HIGHEST)
    public void onPhaseComplete(ArenaPhaseCompleteEvent event) {
        if (!(event.getPhase() instanceof VictoryPhase<?>)) {
            return;
        }

        // Kick all spectators once the game is over
        if (event.getCompetition() instanceof LiveCompetition<?> liveCompetition) {
            for (ArenaPlayer spectator : Set.copyOf(liveCompetition.getSpectators())) {
                spectator.getCompetition().leave(spectator, ArenaLeaveEvent.Cause.GAME);
            }
        }

        if (event.getCompetition().getMap().getType() == MapType.DYNAMIC) {
            Arena arena = event.getArena();

            // Teardown if we are in a dynamic map
            arena.getPlugin().removeCompetition(arena, event.getCompetition());

            if (arena.getType() == CompetitionType.EVENT) {
                arena.getPlugin().getEventScheduler().eventEnded(arena, event.getCompetition());
            }
        }
    }

    @ArenaEventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event, ArenaPlayer player) {
        player.getStorage().markDisconnected();
        player.getCompetition().leave(player, ArenaLeaveEvent.Cause.DISCONNECT);
    }

    @ArenaEventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event, ArenaPlayer player) {
        if (event.isCancelled()) {
            return;
        }

        // Call the death event
        this.competition.getArena().getEventManager().callEvent(new ArenaDeathEvent(player));

        // Now see if the player was killed by another player
        // in this same arena
        Player killer = player.getPlayer().getKiller();
        if (killer == null) {
            return;
        }

        ArenaPlayer killerPlayer = ArenaPlayer.getArenaPlayer(killer);
        if (killerPlayer == null) {
            return;
        }

        // Check if the killer is in the same arena
        if (killerPlayer.getCompetition().equals(this.competition)) {
            this.competition.getArena().getEventManager().callEvent(new ArenaKillEvent(killerPlayer, player));
        }
    }

    @ArenaEventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event, ArenaPlayer player) {
        // Call the respawn event
        this.competition.getArena().getEventManager().callEvent(new ArenaRespawnEvent(player));
    }

    @SuppressWarnings("unchecked")
    @Override
    public T getCompetition() {
        return (T) this.competition;
    }
}
