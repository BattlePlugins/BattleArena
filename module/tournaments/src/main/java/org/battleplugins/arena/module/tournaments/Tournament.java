package org.battleplugins.arena.module.tournaments;

import org.battleplugins.arena.Arena;
import org.battleplugins.arena.ArenaPlayer;
import org.battleplugins.arena.competition.Competition;
import org.battleplugins.arena.competition.CompetitionType;
import org.battleplugins.arena.competition.LiveCompetition;
import org.battleplugins.arena.competition.PlayerRole;
import org.battleplugins.arena.competition.map.LiveCompetitionMap;
import org.battleplugins.arena.competition.map.MapType;
import org.battleplugins.arena.module.tournaments.algorithm.SingleEliminationTournamentCalculator;
import org.battleplugins.arena.module.tournaments.algorithm.TournamentCalculator;
import org.battleplugins.arena.options.Teams;
import org.battleplugins.arena.team.ArenaTeam;
import org.battleplugins.arena.util.IntRange;
import org.battleplugins.arena.util.Util;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.battleplugins.arena.module.tournaments.TournamentMessages.NEXT_ROUND_STARTING;
import static org.battleplugins.arena.module.tournaments.TournamentMessages.NEXT_ROUND_STARTING_IN;
import static org.battleplugins.arena.module.tournaments.TournamentMessages.TOURNAMENT_ALREADY_STARTED;
import static org.battleplugins.arena.module.tournaments.TournamentMessages.TOURNAMENT_ARENA_NOT_EMPTY;
import static org.battleplugins.arena.module.tournaments.TournamentMessages.TOURNAMENT_COMPLETED;
import static org.battleplugins.arena.module.tournaments.TournamentMessages.TOURNAMENT_CONGRATULATIONS_TO_WINNERS;
import static org.battleplugins.arena.module.tournaments.TournamentMessages.TOURNAMENT_DRAW;
import static org.battleplugins.arena.module.tournaments.TournamentMessages.TOURNAMENT_FIRST_ROUND;
import static org.battleplugins.arena.module.tournaments.TournamentMessages.TOURNAMENT_LOST_ROUND;
import static org.battleplugins.arena.module.tournaments.TournamentMessages.TOURNAMENT_NOT_ENOUGH_ARENAS;
import static org.battleplugins.arena.module.tournaments.TournamentMessages.TOURNAMENT_NOT_ENOUGH_PLAYERS;
import static org.battleplugins.arena.module.tournaments.TournamentMessages.TOURNAMENT_SKIPPED_ROUND;
import static org.battleplugins.arena.module.tournaments.TournamentMessages.TOURNAMENT_TEAM_AMOUNT;
import static org.battleplugins.arena.module.tournaments.TournamentMessages.TOURNAMENT_TEAM_SIZE;
import static org.battleplugins.arena.module.tournaments.TournamentMessages.TOURNAMENT_WON_ROUND;

/**
 * A tournament is a competition where players compete against each other
 * in a series of matches until a winner is determined.
 * <p>
 * Tournaments are typically bracketed and have a set number of players.
 * They can also have multiple rounds. These act similar to an {@link CompetitionType#EVENT}
 * but differ in the sense that they run on top of existing arenas.
 * <p>
 * This varies slightly from a conventional bracket tournament as it allows
 * for one team to consist of multiple players. This means that a "contestant"
 * can be a team of multiple players.
 */
public class Tournament {
    private final Tournaments tournaments;
    private final Arena arena;
    private final int maxContestantSize;
    private final int requiredPlayersPerRound;
    private final int requiredContestantsPerRound;
    private final TournamentListener listener;
    private final TournamentCalculator calculator;

    private State state;
    private boolean advancing;

    private final List<Player> queuedPlayers = new ArrayList<>();
    private final List<Player> watchingPlayers = new ArrayList<>();

    private final Set<ContestantPair> currentContestants = new HashSet<>();
    private final Set<Contestant> winningContestants = new HashSet<>();

    private Tournament(Tournaments tournaments, Arena arena, int maxContestantSize, int requiredPlayersPerRound, int requiredContestantsPerRound) {
        this.tournaments = tournaments;
        this.arena = arena;
        this.maxContestantSize = maxContestantSize;
        this.requiredPlayersPerRound = requiredPlayersPerRound;
        this.requiredContestantsPerRound = requiredContestantsPerRound;
        this.listener = new TournamentListener(arena, this);
        this.calculator = new SingleEliminationTournamentCalculator();

        if (tournaments.getConfig().isBroadcastTournament()) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                TournamentMessages.TOURNAMENT_BEGINNING_BROADCAST.send(player, arena.getName(), arena.getName());
            }
        }

        this.state = State.WAITING;
    }

    public Arena getArena() {
        return this.arena;
    }

    public void join(Player player) {
        this.watchingPlayers.add(player);

        if (this.state == State.WAITING) {
            this.queuedPlayers.add(player);
        }
    }

    public void leave(Player player) {
        this.watchingPlayers.remove(player);

        if (this.state == State.WAITING) {
            this.queuedPlayers.remove(player);
            return;
        }

        Contestant contestant = this.getContestant(player);
        if (contestant == null) {
            return;
        }

        contestant.removePlayer(player);
        if (contestant.getPlayers().isEmpty()) {
            this.winningContestants.remove(contestant);
        }

        if (this.canAdvance()) {
            this.onAdvance(List.copyOf(this.winningContestants));
        }
    }

    public boolean canStart() {
        return this.queuedPlayers.size() >= this.requiredPlayersPerRound;
    }

    public boolean hasStarted() {
        return this.state != State.WAITING;
    }

    public void start() throws TournamentException {
        if (this.state != State.WAITING) {
            throw new TournamentException(TOURNAMENT_ALREADY_STARTED);
        }

        if (!this.canStart()) {
            throw new TournamentException(TOURNAMENT_NOT_ENOUGH_PLAYERS.withContext(Integer.toString(this.requiredPlayersPerRound)));
        }

        this.state = State.STARTING;
        Set<Player> players = new HashSet<>(this.queuedPlayers);
        this.queuedPlayers.clear();

        List<Contestant> contestants = calculateContestants(players, this.maxContestantSize, this.requiredContestantsPerRound);
        this.advance(contestants);
    }

    public void finish(@Nullable Contestant winner) {
        this.state = State.FINISHED;
        this.tournaments.removeTournament(this.arena);

        if (winner != null) {
            List<String> winnerNames = winner.getPlayers().stream().map(Player::getName).toList();
            String winners = String.join(", ", winnerNames);
            for (Player player : this.watchingPlayers) {
                TOURNAMENT_COMPLETED.send(player, TOURNAMENT_CONGRATULATIONS_TO_WINNERS.withContext(winners));
            }

            List<String> commandsOnWin = this.tournaments.getConfig().getCommandsOnWin();
            for (String command : commandsOnWin) {
                for (String winnerName : winnerNames) {
                    // TODO: Proper variable replacement
                    String playerName = command.replace("%player_name%", winnerName);

                    try {
                        Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), playerName);
                    } catch (Exception e) {
                        this.arena.getPlugin().error("Failed to run command {} on tournament win!", command, e);
                    }
                }
            }
        } else {
            for (Player player : this.watchingPlayers) {
                TOURNAMENT_COMPLETED.send(player, TOURNAMENT_DRAW);
            }
        }

        this.arena.getEventManager().unregisterEvents(this.listener);
        this.currentContestants.clear();
        this.winningContestants.clear();
        this.watchingPlayers.clear();
        this.queuedPlayers.clear();
    }

    public boolean canAdvance() {
        return this.winningContestants.size() >= this.currentContestants.size() && this.currentContestants.stream().allMatch(ContestantPair::isDone);
    }

    public void onAdvance(List<Contestant> contestants) throws TournamentException {
        if (this.advancing) {
            this.arena.getPlugin().warn("Tournament for arena {} is already advancing. Ignoring request to advance.", this.arena.getName());
            return;
        }

        this.advancing = true;

        if (contestants.size() <= 1) {
            this.finish(contestants.isEmpty() ? null : contestants.get(0));
            return;
        }

        Duration advanceTime = this.tournaments.getConfig().getAdvanceTime();
        if (advanceTime.isZero()) {
            this.advance(contestants);
        } else {
            long ticks = advanceTime.toMillis() / 50;
            Bukkit.getServer().getScheduler().runTaskLater(this.arena.getPlugin(), () -> {
                this.advance(contestants);
            }, ticks);

            for (Player watchingPlayer : this.watchingPlayers) {
                NEXT_ROUND_STARTING_IN.send(watchingPlayer, Util.toUnitString(advanceTime.toSeconds(), TimeUnit.SECONDS));
            }
        }
    }

    private void advance(List<Contestant> contestants) {
        this.advancing = false;
        this.currentContestants.clear();
        this.winningContestants.clear();
        if (contestants.size() <= 1) {
            this.finish(contestants.isEmpty() ? null : contestants.get(0));
            return;
        }

        if (this.state == State.STARTING) {
            for (Player watchingPlayer : this.watchingPlayers) {
                TOURNAMENT_FIRST_ROUND.send(watchingPlayer);
            }
        } else {
            for (Contestant contestant : contestants) {
                for (Player player : contestant.getPlayers()) {
                    NEXT_ROUND_STARTING.send(player);
                }
            }
        }

        TournamentCalculator.MatchResult result = this.calculator.advanceRound(contestants);

        List<ContestantPair> contestantPairs = result.contestantPairs().stream().filter(pair -> !pair.autoAdvance()).toList();
        int mapsNeeded = contestantPairs.size();

        List<Competition<?>> openCompetitions = this.arena.getPlugin().getCompetitions(this.arena)
                .stream()
                .filter(competition -> competition instanceof LiveCompetition<?> liveCompetition
                        && liveCompetition.getPlayers().isEmpty()
                        && liveCompetition.getSpectators().isEmpty())
                .toList();

        List<Competition<?>> allocatedCompetitions = new ArrayList<>(openCompetitions);

        // Ensure we have enough arenas to host the tournament
        if (openCompetitions.size() < mapsNeeded) {
            // Check to see if any of the open competitions are dynamic and allocate as needed
            int requiredCompetitions = mapsNeeded - openCompetitions.size();

            List<LiveCompetitionMap> dynamicMaps = this.arena.getPlugin().getMaps(this.arena)
                    .stream()
                    .filter(map -> map.getType() == MapType.DYNAMIC)
                    .toList();

            if (dynamicMaps.isEmpty()) {
                throw new TournamentException(TOURNAMENT_NOT_ENOUGH_ARENAS);
            }

            for (int i = 0; i < requiredCompetitions; i++) {
                // Now just walk through the dynamic maps and allocate them
                LiveCompetitionMap map = dynamicMaps.get(i % dynamicMaps.size());

                Competition<?> competition = map.createDynamicCompetition(this.arena);
                this.arena.getPlugin().addCompetition(this.arena, competition);

                allocatedCompetitions.add(competition);
            }
        }

        // Teleport players to arenas
        int i = 0;
        for (ContestantPair pair : result.contestantPairs()) {
            if (pair.autoAdvance()) {
                pair.contestant1().addBye();
                this.winningContestants.add(pair.contestant1());

                // Add as current contestant, even if they are skipped. Important
                // they are added here as canAdvance will check if all contestants
                // are done (in which case, the result will always be yes for this
                // contestant pair)
                this.currentContestants.add(pair);

                for (Player player : pair.contestant1().getPlayers()) {
                    TOURNAMENT_SKIPPED_ROUND.send(player);
                }

                continue;
            }

            LiveCompetition<?> competition = (LiveCompetition<?>) allocatedCompetitions.get(i++);

            // Non-team game - just join regularly and let game calculate team. Winner will be
            // determined by the individual player who wins
            if (this.arena.getTeams().isNonTeamGame()) {
                for (Player player : pair.contestant1().getPlayers()) {
                    competition.join(player, PlayerRole.PLAYING);
                }

                for (Player player : pair.contestant2().getPlayers()) {
                    competition.join(player, PlayerRole.PLAYING);
                }
            } else {
                List<ArenaTeam> teams = new ArrayList<>(competition.getTeamManager().getTeams());
                if (teams.size() < 2) {
                    this.arena.getPlugin().warn("Tournament for arena {} has less than 2 teams available on map {}. Falling back to default team assignment.",
                            this.arena.getName(), competition.getMap().getName());
                    for (Player player : pair.contestant1().getPlayers()) {
                        competition.join(player, PlayerRole.PLAYING);
                    }

                    for (Player player : pair.contestant2().getPlayers()) {
                        competition.join(player, PlayerRole.PLAYING);
                    }
                    continue;
                }

                ArenaTeam team1 = teams.get(0);
                ArenaTeam team2 = teams.get(1);
                for (Player player : pair.contestant1().getPlayers()) {
                    competition.join(player, PlayerRole.PLAYING, team1);
                }

                for (Player player : pair.contestant2().getPlayers()) {
                    competition.join(player, PlayerRole.PLAYING, team2);
                }
            }
        }

        this.currentContestants.addAll(contestantPairs);
        this.state = State.IN_PROGRESS;
    }

    public Set<ContestantPair> getCurrentContestants() {
        return Set.copyOf(this.currentContestants);
    }

    public List<Contestant> getWinningContestants() {
        return List.copyOf(this.winningContestants);
    }

    public boolean isInTournament(Player player) {
        if (this.state == State.WAITING) {
            return this.queuedPlayers.contains(player);
        }

        return this.currentContestants.stream()
                .anyMatch(pair -> pair.contestant1().getPlayers().contains(player)
                        || pair.contestant2() != null && pair.contestant2().getPlayers().contains(player)
                );
    }

    @Nullable
    public Contestant getContestant(Player player) {
        for (ContestantPair currentContestant : this.currentContestants) {
            if (currentContestant.contestant1().getPlayers().contains(player)) {
                return currentContestant.contestant1();
            }

            if (currentContestant.contestant2() != null && currentContestant.contestant2().getPlayers().contains(player)) {
                return currentContestant.contestant2();
            }
        }

        return null;
    }

    public void onVictory(Set<ArenaPlayer> victors) {
        Set<Contestant> contestants = new HashSet<>();
        for (ArenaPlayer victor : victors) {
            Contestant contestant = this.getContestant(victor.getPlayer());
            if (contestant == null) {
                this.arena.getPlugin().warn("Victor {} was not in the tournament for {} despite an active tournament running for their arena!", victor, this.arena.getName());
                continue;
            }

            contestant.addWin();
            contestants.add(contestant);

            TOURNAMENT_WON_ROUND.send(victor.getPlayer());
        }

        if (contestants.size() > 1) {
            this.arena.getPlugin().warn("Multiple victors in tournament {}. Advancing anyway but this could indicate a problem." + this);
        }

        this.winningContestants.addAll(contestants);
    }

    public void onLoss(Set<ArenaPlayer> losers) {
        for (ArenaPlayer loser : losers) {
            Contestant contestant = this.getContestant(loser.getPlayer());
            if (contestant == null) {
                this.arena.getPlugin().warn("Loser {} was not in the tournament for {} despite an active tournament running for their arena!", loser, this.arena.getName());
                continue;
            }

            contestant.addLoss();

            TOURNAMENT_LOST_ROUND.send(loser.getPlayer());
        }
    }

    public void onDraw(Set<ArenaPlayer> players) {
        if (this.currentContestants.size() == 1) {
            // If we're at the final matchup and it draws, the tournament ends in a draw.
            this.finish(null);
            return;
        }

        Set<Contestant> contestants = new HashSet<>();
        for (ArenaPlayer player : players) {
            Contestant contestant = this.getContestant(player.getPlayer());
            if (contestant == null) {
                this.arena.getPlugin().warn("Player {} was not in the tournament for {} despite an active tournament running for their arena!", player, this.arena.getName());
                continue;
            }

            TOURNAMENT_DRAW.send(player.getPlayer());
            contestants.add(contestant);
        }

        // Advance both contestants in the event of a draw
        this.winningContestants.addAll(contestants);
    }

    public static Tournament createTournament(Tournaments tournaments, Arena arena) throws TournamentException {
        Teams teams = arena.getTeams();
        IntRange teamAmount = teams.getTeamAmount();
        IntRange teamSize = teams.getTeamSize();

        // Check #1 - Ensure each team must be able to fit one player
        if (teamSize.getMin() < 1) {
            throw new TournamentException(TOURNAMENT_TEAM_SIZE);
        }

        // Check #2 - Make sure we have 2 teams
        if (teamAmount.getMax() != 2) {
            throw new TournamentException(TOURNAMENT_TEAM_AMOUNT);
        }

        // Check #3 - Ensure that all arenas of this type are empty
        for (Competition<?> competition : arena.getPlugin().getCompetitions(arena)) {
            if (!(competition instanceof LiveCompetition<?> liveCompetition)) {
                continue;
            }

            if (!liveCompetition.getPlayers().isEmpty()) {
                throw new TournamentException(TOURNAMENT_ARENA_NOT_EMPTY);
            }
        }

        int requiredPlayers;
        int requiredContestants = teamAmount.getMax();
        if (teams.isNonTeamGame()) {
            requiredPlayers = teamAmount.getMax();
        } else {
            requiredPlayers = teamAmount.getMax() * teamSize.getMin();
        }

        return new Tournament(tournaments, arena, teamSize.getMin() == Integer.MAX_VALUE ? teamSize.getMin() : teamSize.getMax(), requiredPlayers, requiredContestants);
    }

    private static List<Contestant> calculateContestants(Set<Player> queuedPlayers, int maxContestantSize, int requiredContestantsPerRound) {
        List<Player> playersList = new ArrayList<>(queuedPlayers);

        int safeRequiredContestants = Math.max(1, requiredContestantsPerRound);
        int totalPlayers = playersList.size();
        int playersPerContestant = Math.min(maxContestantSize, Math.max(1, totalPlayers / safeRequiredContestants));
        int numOfContestants = totalPlayers / playersPerContestant;

        while (numOfContestants < safeRequiredContestants && playersPerContestant > 1) {
            playersPerContestant--;
            numOfContestants = totalPlayers / playersPerContestant;
        }

        // Create the contestants
        List<Contestant> contestants = new ArrayList<>();
        for (int i = 0; i < numOfContestants; i++) {
            Set<Player> contestantPlayers = new HashSet<>(playersList.subList(i * playersPerContestant, (i + 1) * playersPerContestant));
            contestants.add(new Contestant(contestantPlayers));
        }

        // Handle any remaining players that didn't fit into a full contestant
        int remainingPlayersStartIndex = numOfContestants * playersPerContestant;
        if (remainingPlayersStartIndex < totalPlayers) {
            Set<Player> remainingPlayers = new HashSet<>(playersList.subList(remainingPlayersStartIndex, totalPlayers));
            if (remainingPlayers.size() >= requiredContestantsPerRound) {
                contestants.add(new Contestant(remainingPlayers));
            } else {
                // Distribute remaining players among the existing contestants
                int idx = 0;
                for (Player player : remainingPlayers) {
                    contestants.get(idx % contestants.size()).addPlayer(player);
                    idx++;
                }
            }
        }

        // Now spread out the number of players among contestants to ensure
        // we don't have a situation where one contestant has 1 player and
        // the rest have many more, only if the number of contestants is not
        // multiple of 2^k
        if (contestants.size() == 1 || Integer.bitCount(contestants.size()) == 1) {
            return contestants;
        }

        int minPlayersPerContestant = totalPlayers / contestants.size();
        int remainingPlayers = totalPlayers % contestants.size();
        for (int i = 0; i < contestants.size(); i++) {
            Contestant contestant = contestants.get(i);
            int start = i * minPlayersPerContestant;
            int end = start + minPlayersPerContestant;
            if (i < remainingPlayers) {
                end++;
            }

            contestant.clearPlayers();
            List<Player> players = playersList.subList(start, end);
            players.forEach(contestant::addPlayer);
        }

        return contestants;
    }
    
    enum State {
        WAITING,
        STARTING,
        IN_PROGRESS,
        FINISHED
    }
}
