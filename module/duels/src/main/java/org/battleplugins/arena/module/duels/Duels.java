package org.battleplugins.arena.module.duels;

import org.battleplugins.arena.Arena;
import org.battleplugins.arena.ArenaPlayer;
import org.battleplugins.arena.competition.Competition;
import org.battleplugins.arena.competition.JoinResult;
import org.battleplugins.arena.competition.LiveCompetition;
import org.battleplugins.arena.competition.PlayerRole;
import org.battleplugins.arena.competition.map.LiveCompetitionMap;
import org.battleplugins.arena.competition.map.MapType;
import org.battleplugins.arena.competition.phase.CompetitionPhase;
import org.battleplugins.arena.competition.phase.phases.CountdownPhase;
import org.battleplugins.arena.competition.phase.phases.WaitingPhase;
import org.battleplugins.arena.event.arena.ArenaCreateExecutorEvent;
import org.battleplugins.arena.event.player.ArenaLeaveEvent;
import org.battleplugins.arena.event.player.ArenaPreJoinEvent;
import org.battleplugins.arena.messages.Messages;
import org.battleplugins.arena.module.ArenaModule;
import org.battleplugins.arena.module.ArenaModuleInitializer;
import org.battleplugins.arena.team.ArenaTeam;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * A module that adds duels to BattleArena.
 */
@ArenaModule(id = Duels.ID, name = "Duels", description = "Adds duels to BattleArena.", authors = "BattlePlugins")
public class Duels implements ArenaModuleInitializer {
    public static final String ID = "duels";
    public static final JoinResult PENDING_REQUEST = new JoinResult(false, DuelsMessages.PENDING_DUEL_REQUEST);

    private final Map<UUID, UUID> duelRequests = new HashMap<>();

    private enum DuelParticipant {
        INSTANCE
    }

    @EventHandler
    public void onCreateExecutor(ArenaCreateExecutorEvent event) {
        if (!event.getArena().isModuleEnabled(ID)) {
            return;
        }

        event.registerSubExecutor(new DuelsExecutor(this, event.getArena()));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID requested = this.duelRequests.remove(event.getPlayer().getUniqueId());
        if (requested == null) {
            return;
        }

        Player requestedPlayer = Bukkit.getPlayer(requested);
        if (requestedPlayer != null) {
            DuelsMessages.DUEL_REQUESTED_CANCELLED_QUIT.send(requestedPlayer, event.getPlayer().getName());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreJoin(ArenaPreJoinEvent event) {
        if (this.duelRequests.containsKey(event.getPlayer().getUniqueId())) {
            event.setResult(PENDING_REQUEST);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLeave(ArenaLeaveEvent event) {
        if (!event.getArena().isModuleEnabled(ID)
                || event.getCause() == ArenaLeaveEvent.Cause.PLUGIN
                || event.getArenaPlayer().getMetadata(DuelParticipant.class) == null) {
            return;
        }

        LiveCompetition<?> competition = event.getArenaPlayer().getCompetition();
        CompetitionPhase<?> phase = competition.getPhaseManager().getCurrentPhase();
        if (!(phase instanceof WaitingPhase<?>) && !(phase instanceof CountdownPhase<?>)) {
            return;
        }

        for (ArenaPlayer player : Set.copyOf(competition.getPlayers())) {
            competition.leave(player, ArenaLeaveEvent.Cause.PLUGIN);
        }
    }

    public Map<UUID, UUID> getDuelRequests() {
        return Map.copyOf(this.duelRequests);
    }

    public void addDuelRequest(UUID sender, UUID receiver) {
        this.duelRequests.put(sender, receiver);
    }

    public void removeDuelRequest(UUID sender) {
        this.duelRequests.remove(sender);
    }

    public CompletableFuture<?> acceptDuel(Arena arena, Player player, Player target) {
        return this.findOrJoinCompetition(arena).whenCompleteAsync((competition, e) -> {
            if (e != null) {
                Messages.ARENA_ERROR.send(player, e.getMessage());
                Messages.ARENA_ERROR.send(target, e.getMessage());

                arena.getPlugin().error("An error occurred while joining the arena", e);
                return;
            }

            if (competition == null) {
                Messages.NO_OPEN_ARENAS.send(player);
                Messages.NO_OPEN_ARENAS.send(target);
                return;
            }

            // Non-team game - just join regularly and let game calculate team. Winner will be
            // determined by the individual player who wins
            if (arena.getTeams().isNonTeamGame()) {
                competition.join(player, PlayerRole.PLAYING);
                markDuelParticipant(player);
                competition.join(target, PlayerRole.PLAYING);
                markDuelParticipant(target);
            } else {
                ArenaTeam team1 = competition.getTeamManager().getTeams().iterator().next();
                ArenaTeam team2 = competition.getTeamManager().getTeams().iterator().next();

                competition.join(player, PlayerRole.PLAYING, team1);
                markDuelParticipant(player);
                competition.join(target, PlayerRole.PLAYING, team2);
                markDuelParticipant(target);
            }
        }, Bukkit.getScheduler().getMainThreadExecutor(arena.getPlugin()));
    }

    private static void markDuelParticipant(Player player) {
        ArenaPlayer arenaPlayer = ArenaPlayer.getArenaPlayer(player);
        if (arenaPlayer != null) {
            arenaPlayer.setMetadata(DuelParticipant.class, DuelParticipant.INSTANCE);
        }
    }

    private CompletableFuture<LiveCompetition<?>> findOrJoinCompetition(Arena arena) {
        List<Competition<?>> openCompetitions = arena.getPlugin().getCompetitions(arena)
                .stream()
                .filter(competition -> competition instanceof LiveCompetition<?> liveCompetition
                        && liveCompetition.getPhaseManager().getCurrentPhase().canJoin()
                        && liveCompetition.getPlayers().isEmpty()
                )
                .toList();

        // Ensure we have found an open competition
        if (openCompetitions.isEmpty()) {
            List<LiveCompetitionMap> dynamicMaps = arena.getPlugin().getMaps(arena)
                    .stream()
                    .filter(map -> map.getType() == MapType.DYNAMIC)
                    .toList();

            if (dynamicMaps.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }

            LiveCompetitionMap map = dynamicMaps.iterator().next();

            CompletableFuture<LiveCompetition<?>> competitionFuture = map.createDynamicCompetition(arena);
            return competitionFuture.thenApplyAsync(competition -> {
                arena.getPlugin().addCompetition(arena, competition);
                return competition;
            }, Bukkit.getScheduler().getMainThreadExecutor(arena.getPlugin()));
        } else {
            return CompletableFuture.completedFuture((LiveCompetition<?>) openCompetitions.iterator().next());
        }
    }
}
