package org.battleplugins.arena.competition.victory.types;

import org.battleplugins.arena.ArenaPlayer;
import org.battleplugins.arena.competition.LiveCompetition;
import org.battleplugins.arena.competition.team.TeamStatHolder;
import org.battleplugins.arena.competition.victory.VictoryCondition;
import org.battleplugins.arena.config.ArenaOption;
import org.battleplugins.arena.event.ArenaEventHandler;
import org.battleplugins.arena.event.player.ArenaStatChangeEvent;
import org.battleplugins.arena.stat.ArenaStat;
import org.battleplugins.arena.stat.ArenaStats;
import org.battleplugins.arena.stat.StatHolder;
import org.battleplugins.arena.team.ArenaTeam;
import org.bukkit.Bukkit;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class HighestStatCondition<T extends LiveCompetition<T>> extends VictoryCondition<T> {

    @ArenaOption(name = "stat-name", description = "The name of the stat to check for.", required = true)
    private String statName;

    @ArenaOption(name = "win-after", description = "The threshold of stats to win.")
    private int winAfter = -1;

    @ArenaOption(name = "team-stats", description = "Whether to check team stats instead of player stats.")
    private boolean teamStats = false;

    private ArenaStat<Number> stat;

    @ArenaEventHandler
    public void onStatChange(ArenaStatChangeEvent<Number> event) {
        if (!event.getStat().equals(this.stat)) {
            return;
        }

        // Check for team stats. Some games may have modes in which a team wins
        // after the team has collectively retrieved a stat, or a stat that is
        // incremented by team (i.e. control points).
        StatHolder statHolder = event.getStatHolder();
        if (this.teamStats) {
            if (statHolder instanceof TeamStatHolder teamHolder) {
                if (this.winAfter != -1 && event.getNewValue().intValue() >= this.winAfter) {
                    this.advanceToNextPhase(this.competition.getTeamManager().getPlayersOnTeam(teamHolder.getTeam()));
                }
            } else if (statHolder instanceof ArenaPlayer player) {
                // Check for stats across all players on the team
                ArenaTeam team = player.getTeam();
                if (team == null) {
                    return;
                }

                Set<ArenaPlayer> players = this.competition.getTeamManager().getPlayersOnTeam(team);
                int score = 0;
                for (ArenaPlayer teamPlayer : players) {
                    if (teamPlayer == player) {
                        score += event.getNewValue().intValue();
                        continue;
                    }

                    score += teamPlayer.stat(this.stat).orElse(0).intValue();
                }

                if (this.winAfter != -1 && score >= this.winAfter) {
                    this.advanceToNextPhase(players);
                }
            }

            return;
        }

        if (statHolder instanceof ArenaPlayer player) {
            if (this.winAfter != -1 && event.getNewValue().intValue() >= this.winAfter) {
                // Still need to check if the player is on a team, since we grant
                // the victory based on whether the team one. If the player is to
                // win individually, their team should just contain them, or be empty.
                ArenaTeam team = player.getTeam();
                if (team == null) {
                    this.advanceToNextPhase(Set.of(player));
                    return;
                }

                Set<ArenaPlayer> playersOnTeam = this.competition.getTeamManager().getPlayersOnTeam(team);
                this.advanceToNextPhase(playersOnTeam);
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onStart() {
        ArenaStat<Number> stat = (ArenaStat<Number>) ArenaStats.get(this.statName);
        if (stat == null) {
            throw new IllegalArgumentException("Invalid stat name: " + this.statName);
        }

        this.stat = stat;
    }

    @Override
    public Set<ArenaPlayer> identifyPotentialVictors() {
        return this.competition.getPlayers().stream()
                .sorted((player1, player2) ->
                        player2.stat(this.stat).orElse(0).intValue() - player1.stat(this.stat).orElse(0).intValue()
                )
                // No need to check win after here, since it will be done earlier if they should win
                .filter(player -> (int) player.stat(this.stat).orElse(0) > 0)
                .limit(1) // Limit number of victors to 1
                .flatMap(player -> {
                    // Still need to check if the player is on a team, since we grant
                    // the victory based on whether the team won. If the player is to
                    // win individually, their team should just contain them, or be empty.
                    ArenaTeam team = player.getTeam();
                    if (team == null || this.getCompetition().getArena().getTeams().isNonTeamGame() || !this.teamStats) {
                        //デバック用のログ出力
                        Bukkit.getLogger().info("[IPV]: F4A online...");
                        return Stream.of(player);
                    }

                    Set<ArenaPlayer> playersOnTeam = this.competition.getTeamManager().getPlayersOnTeam(team);
                    return playersOnTeam.stream();
                })
                .collect(Collectors.toSet());
    }
}
