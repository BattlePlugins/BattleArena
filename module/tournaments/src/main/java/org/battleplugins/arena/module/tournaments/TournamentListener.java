package org.battleplugins.arena.module.tournaments;

import org.battleplugins.arena.Arena;
import org.battleplugins.arena.competition.JoinResult;
import org.battleplugins.arena.competition.LiveCompetition;
import org.battleplugins.arena.competition.phase.CompetitionPhaseType;
import org.battleplugins.arena.event.ArenaEventHandler;
import org.battleplugins.arena.event.ArenaListener;
import org.battleplugins.arena.event.arena.ArenaDrawEvent;
import org.battleplugins.arena.event.arena.ArenaLoseEvent;
import org.battleplugins.arena.event.arena.ArenaPhaseCompleteEvent;
import org.battleplugins.arena.event.arena.ArenaVictoryEvent;
import org.battleplugins.arena.event.player.ArenaLeaveEvent;
import org.battleplugins.arena.event.player.ArenaPreJoinEvent;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;

import static org.battleplugins.arena.module.tournaments.TournamentMessages.TOURNAMENT_CANNOT_JOIN_ARENA;
import static org.battleplugins.arena.module.tournaments.TournamentMessages.TOURNAMENT_CANNOT_JOIN_ARENA_IN_TOURNAMENT;

public class TournamentListener implements ArenaListener {
    private static final JoinResult TOURNAMENT = new JoinResult(false, TOURNAMENT_CANNOT_JOIN_ARENA);
    private static final JoinResult IN_TOURNAMENT = new JoinResult(false, TOURNAMENT_CANNOT_JOIN_ARENA_IN_TOURNAMENT);

    private final Tournament tournament;

    public TournamentListener(Arena arena, Tournament tournament) {
        this.tournament = tournament;

        arena.getEventManager().registerEvents(this);
        Bukkit.getPluginManager().registerEvents(this, arena.getPlugin());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (this.tournament.isInTournament(event.getPlayer())) {
            this.tournament.leave(event.getPlayer());
        }
    }

    @EventHandler
    public void onArenaPreJoinUnscoped(ArenaPreJoinEvent event) {
        if (this.tournament.isInTournament(event.getPlayer())) {
            event.setResult(IN_TOURNAMENT);
        }
    }

    @ArenaEventHandler
    public void onArenaPreJoin(ArenaPreJoinEvent event) {
        event.setResult(TOURNAMENT);
    }

    @ArenaEventHandler
    public void onArenaLeave(ArenaLeaveEvent event) {
        if (this.tournament.isInTournament(event.getPlayer())) {
            if (event.getCause() != ArenaLeaveEvent.Cause.GAME && event.getCause() != ArenaLeaveEvent.Cause.SHUTDOWN) {
                this.tournament.leave(event.getPlayer());
            }
        }
    }

    @ArenaEventHandler
    public void onVictory(ArenaVictoryEvent event) {
        this.tournament.onVictory(event.getVictors());

        if (this.tournament.canAdvance()) {
            this.tournament.onAdvance(this.tournament.getWinningContestants());
        } else {
            this.tournament.tryStartPendingMatches();
        }
    }

    @ArenaEventHandler
    public void onLoss(ArenaLoseEvent event) {
        this.tournament.onLoss(event.getLosers());

        if (this.tournament.canAdvance()) {
            this.tournament.onAdvance(this.tournament.getWinningContestants());
        } else {
            this.tournament.tryStartPendingMatches();
        }
    }

    @ArenaEventHandler
    public void onDraw(ArenaDrawEvent event, LiveCompetition<?> competition) {
        this.tournament.onDraw(competition.getPlayers());

        if (this.tournament.canAdvance()) {
            this.tournament.onAdvance(this.tournament.getWinningContestants());
        } else {
            this.tournament.tryStartPendingMatches();
        }
    }

    @ArenaEventHandler
    public void onPhaseComplete(ArenaPhaseCompleteEvent event) {
        // Check if the tournament can advance at the end of the victory phase.
        // Since arenas may be in a victory phase once a victor is declared, it
        // means that a contestant may not be marked as "done", so we must check
        // at the end of the victory phase too.
        if (CompetitionPhaseType.VICTORY.equals(event.getPhase().getType())) {
            // Post until next tick to ensure all players are marked as complete
            Bukkit.getServer().getScheduler().runTask(this.tournament.getArena().getPlugin(), () -> {
                if (this.tournament.canAdvance()) {
                    this.tournament.onAdvance(this.tournament.getWinningContestants());
                } else {
                    this.tournament.tryStartPendingMatches();
                }
            });
        }
    }
}
