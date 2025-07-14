package org.battleplugins.arena.competition;

import org.battleplugins.arena.Arena;
import org.battleplugins.arena.ArenaLike;
import org.battleplugins.arena.ArenaPlayer;
import org.battleplugins.arena.competition.map.CompetitionMap;
import org.battleplugins.arena.competition.map.LiveCompetitionMap;
import org.battleplugins.arena.competition.map.options.Spawns;
import org.battleplugins.arena.competition.phase.CompetitionPhase;
import org.battleplugins.arena.competition.phase.CompetitionPhaseType;
import org.battleplugins.arena.competition.phase.LiveCompetitionPhase;
import org.battleplugins.arena.competition.phase.PhaseManager;
import org.battleplugins.arena.competition.team.TeamManager;
import org.battleplugins.arena.competition.victory.VictoryManager;
import org.battleplugins.arena.event.player.ArenaJoinEvent;
import org.battleplugins.arena.event.player.ArenaLeaveEvent;
import org.battleplugins.arena.event.player.ArenaPreJoinEvent;
import org.battleplugins.arena.event.player.ArenaSpectateEvent;
import org.battleplugins.arena.options.ArenaOptionType;
import org.battleplugins.arena.options.TeamSelection;
import org.battleplugins.arena.options.Teams;
import org.battleplugins.arena.resolver.Resolvable;
import org.battleplugins.arena.resolver.Resolver;
import org.battleplugins.arena.resolver.ResolverKeys;
import org.battleplugins.arena.resolver.ResolverProvider;
import org.battleplugins.arena.team.ArenaTeam;
import org.battleplugins.arena.team.ArenaTeams;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * A {@link Competition} that is occurring on the same server
 * that this plugin is running on.
 */
public class LiveCompetition<T extends Competition<T>> implements ArenaLike, Competition<T>, Resolvable {
    private final Arena arena;
    private final CompetitionType type;
    private final LiveCompetitionMap map;

    private final Map<Player, ArenaPlayer> players = new HashMap<>();
    private final Map<PlayerRole, Set<ArenaPlayer>> playersByRole = new HashMap<>();

    private final PhaseManager<T> phaseManager;
    private final TeamManager teamManager;
    private final VictoryManager<T> victoryManager;

    private final CompetitionListener<T> competitionListener;
    private final OptionsListener<T> optionsListener;
    private final StatListener<T> statListener;
    
    private final int maxPlayers;

    public LiveCompetition(Arena arena, CompetitionType type, LiveCompetitionMap map) {
        this.arena = arena;
        this.type = type;
        this.map = map;

        this.phaseManager = new PhaseManager<>(arena, (T) this);
        this.teamManager = new TeamManager(this);
        this.victoryManager = new VictoryManager<>(arena, (T) this);

        arena.getEventManager().registerEvents(this.competitionListener = new CompetitionListener<>(this));
        arena.getEventManager().registerEvents(this.optionsListener = new OptionsListener<>(this));
        arena.getEventManager().registerEvents(this.statListener = new StatListener<>(this));

        // Set the initial phase
        CompetitionPhaseType<?, ?> initialPhase = arena.getInitialPhase();
        this.phaseManager.setPhase(initialPhase);

        // Calculate max players
        this.maxPlayers = this.calculateMaxPlayers();
    }

    // API methods

    @Override
    public CompletableFuture<JoinResult> canJoin(Player player, PlayerRole role) {
        return this.canJoin(Set.of(player), role);
    }

    @Override
    public CompletableFuture<JoinResult> canJoin(Collection<Player> players, PlayerRole role) {
        CompetitionPhase<T> currentPhase = this.phaseManager.getCurrentPhase();

        // Check if the player can join the competition in its current state
        if (role == PlayerRole.PLAYING) {
            if (!currentPhase.canJoin()) {
                return CompletableFuture.completedFuture(JoinResult.NOT_JOINABLE);
            }

            // See if the player will fit within the player limits
            Teams teams = this.arena.getTeams();

            // If team selection involves the player picking their own
            // team, or the game is not a team game, then we just need to check
            // the overall maximum number of players this competition can have
            if (teams.getTeamSelection() == TeamSelection.PICK || teams.isNonTeamGame()) {
                // Player cannot join - arena is full
                if ((this.getPlayers().size() + players.size()) > this.maxPlayers) {
                    return CompletableFuture.completedFuture(JoinResult.ARENA_FULL);
                }
            } else {
                List<ArenaTeam> availableTeams = teams.getAvailableTeams();
                // Otherwise, we need to go through all teams and see if there is room for the player

                int spaceAvailable = 0;
                for (ArenaTeam availableTeam : availableTeams) {
                    // Get the amount of space remaining on the team
                    int remainingSpace = this.teamManager.getRemainingSpace(availableTeam);
                    if (remainingSpace > 0) {
                        spaceAvailable += remainingSpace;
                    }
                }

                // No available teams - return that the arena is full
                if (spaceAvailable < players.size()) {
                    return CompletableFuture.completedFuture(JoinResult.ARENA_FULL);
                }
            }
        }

        // Check if the player can spectate the competition in its current phase
        if (role == PlayerRole.SPECTATING && !currentPhase.canSpectate()) {
            return CompletableFuture.completedFuture(JoinResult.NOT_SPECTATABLE);
        }

        for (Player player : players) {
            // Call the ArenaPreJoinEvent
            ArenaPreJoinEvent event = this.arena.getEventManager().callEvent(new ArenaPreJoinEvent(this.arena, this, role, JoinResult.SUCCESS, player));
            if (event.getResult() != JoinResult.SUCCESS) {
                return CompletableFuture.completedFuture(event.getResult());
            }
        }

        return CompletableFuture.completedFuture(JoinResult.SUCCESS);
    }

    /**
     * Finds a suitable team for the player to join and
     * joins them to that team, if applicable.
     *
     * @param player the player to find a team for
     */
    public void findAndJoinTeamIfApplicable(ArenaPlayer player) {
        Teams teams = this.arena.getTeams();

        // If the team selection is none, then we can just put
        // the player on the default team
        if (teams.isNonTeamGame()) {
            this.teamManager.joinTeam(player, ArenaTeams.DEFAULT);
            return;
        }

        if (teams.getTeamSelection() == TeamSelection.RANDOM) {
            this.teamManager.joinTeam(player, this.teamManager.findSuitableTeam());
        }
    }

    // Internal methods (cannot be overridden by extending plugins)

    @Override
    public final Arena getArena() {
        return this.arena;
    }

    @Override
    public CompetitionType getType() {
        return this.type;
    }

    @Override
    public final LiveCompetitionMap getMap() {
        return this.map;
    }

    @Override
    public final CompetitionPhaseType<T, ?> getPhase() {
        return this.phaseManager.getCurrentPhase().getType();
    }

    @Override
    public final void join(Player player, PlayerRole role) {
        this.join(Set.of(player), role);
    }

    @Override
    public final void join(Collection<Player> players, PlayerRole role) {
        this.join(players, role, null);
    }

    /**
     * Makes the player join the competition with the specified {@link PlayerRole}
     * and {@link ArenaTeam}.
     *
     * @param player the player to join
     * @param role the role of the player
     * @param team the team to join
     */
    public final void join(Player player, PlayerRole role, @Nullable ArenaTeam team) {
        this.join(Set.of(player), role, team);
    }

    /**
     * Makes the players join the competition with the specified {@link PlayerRole}
     * and {@link ArenaTeam}.
     *
     * @param players the players to join
     * @param role the role of the player
     * @param team the team to join
     */
    public final void join(Collection<Player> players, PlayerRole role, @Nullable ArenaTeam team) {
        for (Player player : players) {
            if (this.arena.getPlugin().isInArena(player)) {
                this.arena.getPlugin().error("Player {} is already in an arena! Please report this as it is a bug!", player.getName(), new IllegalStateException());
                continue;
            }

            ArenaPlayer arenaPlayer = this.createPlayer(player);
            arenaPlayer.setRole(role);

            this.join(arenaPlayer, team);
        }
    }

    private void join(ArenaPlayer player, @Nullable ArenaTeam team) {
        this.players.put(player.getPlayer(), player);
        this.playersByRole.computeIfAbsent(player.getRole(), e -> new HashSet<>()).add(player);

        if (team == null) {
            if (player.getRole() == PlayerRole.PLAYING) {
                this.findAndJoinTeamIfApplicable(player);
            }
        } else {
            this.teamManager.joinTeam(player, team);
        }

        if (player.getRole() == PlayerRole.PLAYING) {
            ArenaJoinEvent event = new ArenaJoinEvent(player);
            this.arena.getEventManager().callEvent(event);
        } else {
            ArenaSpectateEvent event = new ArenaSpectateEvent(player);
            this.arena.getEventManager().callEvent(event);
        }
    }

    @Override
    public final void leave(Player player, ArenaLeaveEvent.Cause cause) {
        ArenaPlayer arenaPlayer = this.players.get(player);
        if (arenaPlayer == null) {
            return;
        }

        this.leave(arenaPlayer, cause);
    }

    /**
     * Makes the {@link ArenaPlayer} leave the competition with the specified {@link ArenaLeaveEvent.Cause}.
     *
     * @param player the player to leave
     * @param cause the cause of the player leaving
     */
    public final void leave(ArenaPlayer player, ArenaLeaveEvent.Cause cause) {
        this.players.remove(player.getPlayer());
        this.playersByRole.get(player.getRole()).remove(player);

        this.teamManager.leaveTeam(player);

        ArenaLeaveEvent event = new ArenaLeaveEvent(player, cause);
        this.arena.getEventManager().callEvent(event);

        player.remove();
    }

    /**
     * Changes the role of the player to the specified role.
     *
     * @param player the player to change the role of
     * @param role the new role of the player
     */
    public final void changeRole(ArenaPlayer player, PlayerRole role) {
        if (role == player.getRole()) {
            return;
        }

        this.playersByRole.get(player.getRole()).remove(player);
        this.playersByRole.computeIfAbsent(role, e -> new HashSet<>()).add(player);

        player.setRole(role);
    }

    /**
     * Gets all the {@link ArenaPlayer players} in the competition.
     *
     * @return all players in the competition
     */
    public final Set<ArenaPlayer> getPlayers() {
        return Collections.unmodifiableSet(this.playersByRole.getOrDefault(PlayerRole.PLAYING, Set.of()));
    }

    /**
     * Gets all the {@link ArenaPlayer spectators} in the competition.
     *
     * @return all spectators in the competition
     */
    public final Set<ArenaPlayer> getSpectators() {
        return Collections.unmodifiableSet(this.playersByRole.getOrDefault(PlayerRole.SPECTATING, Set.of()));
    }

    @Override
    public final int getAlivePlayerCount() {
        return this.getPlayers().size();
    }

    @Override
    public final int getSpectatorCount() {
        return this.getSpectators().size();
    }
    
    @Override
    public final int getMaxPlayers() {
        return this.maxPlayers;
    }

    /**
     * Gets the {@link PhaseManager} responsible for managing the phases of the competition.
     *
     * @return the phase manager
     */
    public final PhaseManager<T> getPhaseManager() {
        return this.phaseManager;
    }

    /**
     * Gets the {@link TeamManager} responsible for managing the teams of the competition.
     *
     * @return the team manager
     */
    public final TeamManager getTeamManager() {
        return this.teamManager;
    }

    /**
     * Gets the {@link VictoryManager} responsible for managing the victory conditions
     * of the competition.
     *
     * @return the victory manager
     */
    public final VictoryManager<T> getVictoryManager() {
        return this.victoryManager;
    }

    /**
     * Gets the {@link org.battleplugins.arena.options.ArenaOption} of the specified type.
     *
     * @param type the type of option
     * @param <E> the type of option
     * @return the option of the specified type
     */
    public final <E extends org.battleplugins.arena.options.ArenaOption> Optional<E> option(ArenaOptionType<E> type) {
        return Optional.ofNullable(this.getOption(type));
    }

    /**
     * Gets the {@link org.battleplugins.arena.options.ArenaOption} of the specified type.
     *
     * @param type the type of option
     * @param <E> the type of option
     * @return the option of the specified type, or null if it does not exist
     */
    @Nullable
    public final <E extends org.battleplugins.arena.options.ArenaOption> E getOption(ArenaOptionType<E> type) {
        if (this.getPhaseManager().getCurrentPhase() instanceof LiveCompetitionPhase<?> livePhase) {
            return livePhase.getOption(type);
        }

        return this.arena.getOption(type);
    }

    protected final void destroy() {
        this.onDestroy();
    }

    protected void onDestroy() {
        this.arena.getEventManager().unregisterEvents(this.competitionListener);
        this.arena.getEventManager().unregisterEvents(this.optionsListener);
        this.arena.getEventManager().unregisterEvents(this.statListener);
    }

    private ArenaPlayer createPlayer(Player player) {
        return new ArenaPlayer(player, this.arena, this);
    }

    private int calculateMaxPlayers() {
        Teams teams = this.arena.getTeams();
        int teamAmount = teams.isNonTeamGame() ? teams.getTeamAmount().getMax() : this.teamManager.getTeams().size();
        int teamSizeMax = teams.getTeamSize().getMax();
        int maxPlayers;
        if (teamSizeMax == Integer.MAX_VALUE) {
            maxPlayers = Integer.MAX_VALUE;
        } else {
            maxPlayers = teamAmount * teamSizeMax;
        }

        Spawns spawns = this.map.getSpawns();

        // If spawn points are not shared, that means we only have a limited
        // amount of spawn points for each team. We need to check if the team
        // is full based on the amount of spawn points available.
        if (!teams.isSharedSpawnPoints() && spawns != null) {
            maxPlayers = Math.min(maxPlayers, spawns.getSpawnPointCount());
        }

        // If we have zero spawns in any situation, this competition cannot hold any players
        if (spawns == null || spawns.getSpawnPointCount() == 0) {
            maxPlayers = 0;
        }

        return maxPlayers;
    }

    @Override
    public Resolver resolve() {
        Resolver.Builder builder = this.arena.resolve().toBuilder()
                .define(ResolverKeys.ALIVE_PLAYERS, ResolverProvider.simple(this.getAlivePlayerCount(), String::valueOf))
                .define(ResolverKeys.COMPETITION, ResolverProvider.simple(this.getCompetition(), this.getMap()::getName))
                .define(ResolverKeys.ONLINE_PLAYERS, ResolverProvider.simple(this.getAlivePlayerCount() + this.getSpectatorCount(), String::valueOf))
                .define(ResolverKeys.MAP, ResolverProvider.simple(this.getMap(), CompetitionMap::getName))
                .define(ResolverKeys.MAX_PLAYERS, ResolverProvider.simple(this.getMaxPlayers(), String::valueOf))
                .define(ResolverKeys.PHASE, ResolverProvider.simple(this.getPhaseManager().getCurrentPhase(), p -> p.getType().getName()))
                .define(ResolverKeys.SPECTATORS, ResolverProvider.simple(this.getSpectatorCount(), String::valueOf));

        this.getVictoryManager().resolve().mergeInto(builder);
        if (this.getPhaseManager().getCurrentPhase() instanceof LiveCompetitionPhase<?> phase) {
            phase.resolve().mergeInto(builder);
        }
        
        return builder.build();
    }
}
