package org.battleplugins.arena.module.teamcolors;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.battleplugins.arena.ArenaPlayer;
import org.battleplugins.arena.BattleArena;
import org.battleplugins.arena.competition.LiveCompetition;
import org.battleplugins.arena.event.arena.ArenaPhaseStartEvent;
import org.battleplugins.arena.event.player.ArenaJoinEvent;
import org.battleplugins.arena.event.player.ArenaLeaveEvent;
import org.battleplugins.arena.event.player.ArenaTeamJoinEvent;
import org.battleplugins.arena.event.player.ArenaTeamLeaveEvent;
import org.battleplugins.arena.module.ArenaModule;
import org.battleplugins.arena.module.ArenaModuleInitializer;
import org.battleplugins.arena.options.ArenaOptionType;
import org.battleplugins.arena.options.NametagOption;
import org.battleplugins.arena.options.types.BooleanArenaOption;
import org.battleplugins.arena.options.types.EnumArenaOption;
import org.battleplugins.arena.team.ArenaTeam;
import org.battleplugins.arena.team.ArenaTeams;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.scoreboard.Team;

import org.battleplugins.arena.config.ArenaOption;
/**
 * A module that adds team colors to a player's name.
 */
@ArenaModule(id = TeamColors.ID, name = "Team Colors", description = "Adds player team colors to their name.", authors = "BattlePlugins")
public class TeamColors implements ArenaModuleInitializer {
    public static final String ID = "team-colors";

    public static final ArenaOptionType<BooleanArenaOption> TEAM_PREFIXES = ArenaOptionType.create("team-prefixes", BooleanArenaOption::new);
    public static final ArenaOptionType<EnumArenaOption<NametagOption>> NAME_TAG_VISIBILITY = ArenaOptionType.create("name-tag-visibility", params -> new EnumArenaOption<>(params, NametagOption.class, "option"));


    @EventHandler
    public void onJoin(ArenaJoinEvent event) {
        if (!event.getArena().isModuleEnabled(ID)) {
            return;
        }

        this.post(5, () -> {
            for (ArenaTeam team : event.getCompetition().getTeamManager().getTeams()) {
                // Register a new Bukkit team for each team in the competition
                Team bukkitTeam = event.getPlayer().getScoreboard().getTeam("ba-" + team.getName());
                if (bukkitTeam == null) {
                    bukkitTeam = event.getPlayer().getScoreboard().registerNewTeam("ba-" + team.getName());
                    bukkitTeam.displayName(team.getFormattedName());
                    bukkitTeam.color(NamedTextColor.nearestTo(team.getTextColor()));
                    if (showTeamPrefixes(event.getCompetition(), team)) {
                        bukkitTeam.prefix(Component.text("[" + team.getName() + "] ", team.getTextColor()));
                    }
                }

            // Apply NAME_TAG_VISIBILITY option
        final Team finalBukkitTeam = bukkitTeam;
        event.getCompetition().option(NAME_TAG_VISIBILITY).ifPresent(option -> {
            NametagOption nametagOption = option.getOption();
            switch (nametagOption) {
                case ALWAYS:
                    finalBukkitTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);
                    break;
                case NEVER:
                    finalBukkitTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
                    break;
                case FOR_OTHER_TEAMS:
                    finalBukkitTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.FOR_OTHER_TEAMS);
                    break;
                case FOR_OWN_TEAM:
                    finalBukkitTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.FOR_OWN_TEAM);
                    break;
            }
        });
                // If players are already on the team, add them to the Bukkit team
                for (ArenaPlayer teamPlayer : event.getCompetition().getTeamManager().getPlayersOnTeam(team)) {
                    bukkitTeam.addPlayer(teamPlayer.getPlayer());
                }
            }
        });
    }

    @EventHandler
    public void onPhaseStart(ArenaPhaseStartEvent event) {
        if (!event.getArena().isModuleEnabled(ID)) {
            return;
        }

        this.post(5, () -> {
            // Scoreboards may change when phases change, so update
            // team colors in player scoreboards when this happens
            if (event.getCompetition() instanceof LiveCompetition<?> liveCompetition) {
                for (ArenaPlayer arenaPlayer : liveCompetition.getPlayers()) {
                    Player player = arenaPlayer.getPlayer();
                    for (ArenaTeam team : liveCompetition.getTeamManager().getTeams()) {
                        Team bukkitTeam = player.getScoreboard().getTeam("ba-" + team.getName());
                        if (bukkitTeam == null) {
                            bukkitTeam = player.getScoreboard().registerNewTeam("ba-" + team.getName());
                            bukkitTeam.displayName(team.getFormattedName());
                            bukkitTeam.color(NamedTextColor.nearestTo(team.getTextColor()));
                            if (showTeamPrefixes(liveCompetition, team)) {
                                bukkitTeam.prefix(Component.text("[" + team.getName() + "] ", team.getTextColor()));
                            }
                        }

                        for (ArenaPlayer teamPlayer : arenaPlayer.getCompetition().getTeamManager().getPlayersOnTeam(team)) {
                            bukkitTeam.addPlayer(teamPlayer.getPlayer());
                        }
                    }
                }
            }
        });
    }

    @EventHandler
    public void onLeave(ArenaLeaveEvent event) {
        if (!event.getArena().isModuleEnabled(ID)) {
            return;
        }

        this.post(5, () -> {
            if (event.getArenaPlayer().getTeam() != null) {
                this.leaveTeam(event.getPlayer(), event.getCompetition(), event.getArenaPlayer().getTeam());
            }

            // Remove all teams for the player
            for (ArenaTeam team : event.getCompetition().getTeamManager().getTeams()) {
                Team bukkitTeam = event.getPlayer().getScoreboard().getTeam("ba-" + team.getName());
                if (bukkitTeam != null) {
                    bukkitTeam.unregister();
                }
            }
        });
    }

    @EventHandler
    public void onTeamJoin(ArenaTeamJoinEvent event) {
        if (!event.getArena().isModuleEnabled(ID)) {
            return;
        }

        this.post(6, () -> this.joinTeam(event.getPlayer(), event.getCompetition(), event.getTeam()));
    }

    @EventHandler
    public void onTeamLeave(ArenaTeamLeaveEvent event) {
        if (!event.getArena().isModuleEnabled(ID)) {
            return;
        }

        this.post(5, () -> this.leaveTeam(event.getPlayer(), event.getCompetition(), event.getTeam()));
    }

    private void joinTeam(Player player, LiveCompetition<?> competition, ArenaTeam arenaTeam) {
        for (ArenaPlayer arenaPlayer : competition.getPlayers()) {
            Player competitionPlayer = arenaPlayer.getPlayer();

            Team team = competitionPlayer.getScoreboard().getTeam("ba-" + arenaTeam.getName());
            if (team == null) {
                BattleArena.getInstance().warn("Team {} does not have a Bukkit team registered for {}!", arenaTeam.getName(), player.getName());
                continue;
            }

            team.addPlayer(player);
        }
    }

    private void leaveTeam(Player player, LiveCompetition<?> competition, ArenaTeam arenaTeam) {
        for (ArenaPlayer arenaPlayer : competition.getPlayers()) {
            Player competitionPlayer = arenaPlayer.getPlayer();
            Team team = competitionPlayer.getScoreboard().getTeam("ba-" + arenaTeam.getName());
            if (team != null) {
                team.removePlayer(player);
            }
        }
    }

    private void post(int ticks, Runnable runnable) {
        Bukkit.getScheduler().runTaskLater(BattleArena.getInstance(), runnable, ticks);
    }

    private static boolean showTeamPrefixes(LiveCompetition<?> competition, ArenaTeam team) {
        if (team == ArenaTeams.DEFAULT) {
            return false;
        }

        return competition.option(TEAM_PREFIXES)
                .map(BooleanArenaOption::isEnabled)
                .orElse(true);
    }
}
