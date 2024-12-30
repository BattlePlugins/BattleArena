package org.battleplugins.arena.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.battleplugins.arena.Arena;
import org.battleplugins.arena.ArenaPlayer;
import org.battleplugins.arena.competition.Competition;
import org.battleplugins.arena.competition.JoinResult;
import org.battleplugins.arena.competition.LiveCompetition;
import org.battleplugins.arena.competition.PlayerRole;
import org.battleplugins.arena.competition.map.CompetitionMap;
import org.battleplugins.arena.competition.map.LiveCompetitionMap;
import org.battleplugins.arena.competition.map.MapType;
import org.battleplugins.arena.competition.phase.CompetitionPhase;
import org.battleplugins.arena.competition.phase.CompetitionPhaseType;
import org.battleplugins.arena.competition.phase.PhaseManager;
import org.battleplugins.arena.editor.ArenaEditorWizards;
import org.battleplugins.arena.editor.WizardStage;
import org.battleplugins.arena.editor.context.MapCreateContext;
import org.battleplugins.arena.editor.type.MapOption;
import org.battleplugins.arena.event.player.ArenaLeaveEvent;
import org.battleplugins.arena.feature.party.Parties;
import org.battleplugins.arena.feature.party.Party;
import org.battleplugins.arena.feature.party.PartyMember;
import org.battleplugins.arena.messages.Messages;
import org.battleplugins.arena.options.ArenaOptionType;
import org.battleplugins.arena.options.TeamSelection;
import org.battleplugins.arena.options.types.BooleanArenaOption;
import org.battleplugins.arena.team.ArenaTeam;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class ArenaCommandExecutor extends BaseCommandExecutor {
    private static final CompetitionMap RANDOM_MAP_MARKER = new CompetitionMap() {

        @Override
        public String getName() {
            return "marker";
        }

        @Override
        public MapType getType() {
            return MapType.STATIC;
        }
    };

    protected final Arena arena;

    public ArenaCommandExecutor(Arena arena) {
        this(arena.getName().toLowerCase(Locale.ROOT), arena);
    }

    public ArenaCommandExecutor(String parentCommand, Arena arena) {
        super(parentCommand, arena.getName().toLowerCase(Locale.ROOT));

        this.arena = arena;
    }

    @ArenaCommand(commands = { "join", "j" }, description = "Join an arena.", permissionNode = "join")
    public void join(Player player) {
        this.join(player, RANDOM_MAP_MARKER);
    }

    @ArenaCommand(commands = { "join", "j" }, description = "Join an arena.", permissionNode = "join.map")
    public void join(Player player, @Argument(name = "map") CompetitionMap map) {
        if (ArenaPlayer.getArenaPlayer(player) != null) {
            Messages.ALREADY_IN_ARENA.send(player);
            return;
        }

        if (map == null) {
            Messages.NO_ARENA_WITH_NAME.send(player);
            return;
        }

        Set<Player> players;
        Party party = Parties.getParty(player.getUniqueId());
        if (party != null) {
            players = new HashSet<>();
            PartyMember leader = party.getLeader();
            if (leader != null) {
                // If player is not the leader, deny them entry
                if (!leader.getUniqueId().equals(player.getUniqueId())) {
                    Messages.CANNOT_JOIN_ARENA_NOT_PARTY_LEADER.send(player);
                    return;
                }

                players.add(player);
            }

            for (PartyMember member : party.getMembers()) {
                Player memberPlayer = Bukkit.getPlayer(member.getUniqueId());
                if (memberPlayer != null) {
                    players.add(memberPlayer);
                }
            }

            // If we get into a weird state where the party is empty,
            // just add the player
            if (players.isEmpty()) {
                players.add(player);
            }
        } else {
            players = Set.of(player);
        }

        // If any player is already in an arena, deny them entry
        for (Player toJoin : players) {
            if (ArenaPlayer.getArenaPlayer(toJoin) != null) {
                Messages.CANNOT_JOIN_ARENA_MEMBER_IN_ARENA.send(player);
                return;
            }
        }

        List<Competition<?>> competitions = map == RANDOM_MAP_MARKER ? this.arena.getPlugin().getCompetitions(this.arena) : this.arena.getPlugin().getCompetitions(this.arena, map.getName());
        this.arena.getPlugin().findJoinableCompetition(competitions, players, PlayerRole.PLAYING).whenCompleteAsync((result, e) -> {
            if (e != null) {
                Messages.ARENA_ERROR.send(player, e.getMessage());
                this.arena.getPlugin().error("An error occurred while joining the arena", e);
                return;
            }

            Competition<?> competition = result.competition();
            if (competition != null) {
                competition.join(players, PlayerRole.PLAYING);

                for (Player toJoin : players) {
                    Messages.ARENA_JOINED.send(toJoin, competition.getMap().getName());
                }
            } else {
                List<LiveCompetitionMap> maps = this.arena.getPlugin().getMaps(this.arena);
                if (maps.isEmpty()) {
                    Messages.NO_MAPS_FOR_ARENA.send(player);
                    return;
                }

                String mapName = map.getName();
                if (map == RANDOM_MAP_MARKER) {
                    // Select a random map
                    mapName = maps.get(ThreadLocalRandom.current().nextInt(maps.size())).getName();
                }

                // Try and create a dynamic competition if possible
                this.arena.getPlugin()
                        .getOrCreateCompetition(this.arena, players, PlayerRole.PLAYING, mapName)
                        .whenComplete((newResult, ex) -> {
                            if (ex != null) {
                                Messages.ARENA_ERROR.send(player, ex.getMessage());
                                this.arena.getPlugin().error("An error occurred while joining the arena", ex);
                                return;
                            }

                            if (newResult.competition() == null) {
                                // No competition - something happened that stopped the
                                // dynamic arena from being created. Not much we can do here,
                                // but info will be in console in the event of an error
                                if (newResult.result() != JoinResult.NOT_JOINABLE && newResult.result().message() != null) {
                                    newResult.result().message().send(player);
                                } else if (result.result() != JoinResult.NOT_JOINABLE && result.result().message() != null) {
                                   result.result().message().send(player);
                                } else {
                                    Messages.ARENA_NOT_JOINABLE.send(player);
                                }

                                return;
                            }

                            for (Player toJoin : players) {
                                newResult.competition().join(toJoin, PlayerRole.PLAYING);
                                Messages.ARENA_JOINED.send(toJoin, newResult.competition().getMap().getName());
                            }
                        });
            }
        }, Bukkit.getScheduler().getMainThreadExecutor(this.arena.getPlugin()));
    }

    @ArenaCommand(commands = "kick", description = "Kick a player from the arena.", permissionNode = "kick")
    public void kick(Player player, Player target) {
        ArenaPlayer arenaPlayer = ArenaPlayer.getArenaPlayer(target);
        if (arenaPlayer == null) {
            Messages.NOT_IN_ARENA.send(player);
            return;
        }

        if (player == target) {
            Messages.ARENA_CANNOT_KICK_SELF.send(player, this.parentCommand);
            return;
        }

        arenaPlayer.getCompetition().leave(target, ArenaLeaveEvent.Cause.KICKED);
        Messages.ARENA_KICKED.send(player, target.getName());
        Messages.ARENA_KICKED_PLAYER.send(target);
    }

    @ArenaCommand(commands = { "spectate", "s" }, description = "Spectate an arena.", permissionNode = "spectate")
    public void spectate(Player player) {
        List<Competition<?>> competitions = this.arena.getPlugin().getCompetitions(this.arena);
        if (competitions.isEmpty()) {
            Messages.NO_OPEN_ARENAS.send(player);
            return;
        }

        Competition<?> competition = competitions.iterator().next();
        this.spectate(player, competition);
    }

    @ArenaCommand(commands = { "spectate", "s" }, description = "Spectate an arena.", permissionNode = "spectate")
    public void spectate(Player player, Competition<?> competition) {
        if (ArenaPlayer.getArenaPlayer(player) != null) {
            Messages.ALREADY_IN_ARENA.send(player);
            return;
        }

        if (competition == null) {
            Messages.NO_ARENA_WITH_NAME.send(player);
            return;
        }

        competition.canJoin(player, PlayerRole.SPECTATING).whenComplete((result, e) -> {
            if (e != null) {
                Messages.ARENA_ERROR.send(player, e.getMessage());
                this.arena.getPlugin().error("An error occurred while spectating the arena", e);
                return;
            }

            if (result.canJoin()) {
                competition.join(player, PlayerRole.SPECTATING);

                Messages.ARENA_SPECTATE.send(player, competition.getMap().getName());
            } else {
                if (result.message() != null) {
                    result.message().send(player);
                } else {
                    Messages.ARENA_NOT_SPECTATABLE.send(player);
                }
            }
        });
    }

    @ArenaCommand(commands = "list", description = "List all maps and competitions in them.", permissionNode = "list")
    public void list(CommandSender sender) {
        Map<CompetitionMap, List<Competition<?>>> competitionMap = new HashMap<>();
        for (LiveCompetitionMap map : this.arena.getPlugin().getMaps(this.arena)) {
            List<Competition<?>> comps = this.arena.getPlugin().getCompetitions(this.arena, map.getName());
            competitionMap.put(map, comps);
        }

        if (competitionMap.isEmpty()) {
            Messages.NO_MAPS_FOR_ARENA.send(sender);
            return;
        }

        this.sendHeader(sender);
        for (Map.Entry<CompetitionMap, List<Competition<?>>> entry : competitionMap.entrySet()) {
            CompetitionMap map = entry.getKey();
            List<Competition<?>> competitions = entry.getValue();

            if (competitions.isEmpty()) {
                sender.sendMessage(Component.text("- ", NamedTextColor.GRAY)
                        .append(Component.text(map.getName() + ": ", NamedTextColor.WHITE))
                        .append(Messages.NO_RUNNING_COMPETITIONS.toComponent())
                );

                continue;
            }

            if (competitions.size() == 1) {
                Competition<?> competition = competitions.get(0);
                Component competitionInfo = Component.text("- ", NamedTextColor.GRAY)
                        .append(Component.text(map.getName() + ": ", NamedTextColor.WHITE))
                        .append(Messages.PHASE.withContext(competition.getPhase().getName()).toComponent());

                if (competition instanceof LiveCompetition<?> liveCompetition) {
                    competitionInfo = competitionInfo.append(Component.space())
                            .append(Messages.PLAYERS.withContext(String.valueOf(liveCompetition.getPlayers().size()), liveCompetition.getMaxPlayers() == Integer.MAX_VALUE ? "∞" : String.valueOf(liveCompetition.getMaxPlayers())).toComponent());
                }

                sender.sendMessage(competitionInfo);
                continue;
            }

            Component mapInfo = Component.text("- ", NamedTextColor.GRAY)
                    .append(Component.text(map.getName() + ": ", NamedTextColor.WHITE));

            sender.sendMessage(mapInfo);
            int i = 1;
            for (Competition<?> competition : competitions) {
                Component competitionInfo = Component.text("   - ", NamedTextColor.GRAY)
                        .append(Component.text("[" + i++ + "] ", Messages.SECONDARY_COLOR))
                        .append(Messages.PHASE.withContext(competition.getPhase().getName()).toComponent());

                if (competition instanceof LiveCompetition<?> liveCompetition) {
                    competitionInfo = competitionInfo.append(Component.space())
                            .append(Messages.PLAYERS.withContext(String.valueOf(liveCompetition.getPlayers().size()), liveCompetition.getMaxPlayers() == Integer.MAX_VALUE ? "∞" : String.valueOf(liveCompetition.getMaxPlayers())).toComponent());
                }

                sender.sendMessage(competitionInfo);
            }
        }
    }

    @ArenaCommand(commands = { "leave", "l" }, description = "Leave an arena.", permissionNode = "leave")
    public void leave(Player player) {
        ArenaPlayer arenaPlayer = ArenaPlayer.getArenaPlayer(player);
        if (arenaPlayer == null) {
            Messages.NOT_IN_ARENA.send(player);
            return;
        }

        arenaPlayer.getCompetition().leave(player, ArenaLeaveEvent.Cause.COMMAND);
        Messages.ARENA_LEFT.send(player, arenaPlayer.getCompetition().getMap().getName());
    }

    @ArenaCommand(commands = "create", description = "Create a new map.", permissionNode = "create")
    public void create(Player player) {
        ArenaEditorWizards.MAP_CREATION.openWizard(player, this.arena);
    }
    
    @ArenaCommand(commands = { "remove", "delete" }, description = "Removes a map.", permissionNode = "remove")
    public void remove(Player player, CompetitionMap map) {
        if (!(map instanceof LiveCompetitionMap liveMap)) {
            Messages.NO_ARENA_WITH_NAME.send(player);
            return;
        }

        List<Competition<?>> activeCompetitions = this.arena.getPlugin().getCompetitions(this.arena, map.getName());
        for (Competition<?> activeCompetition : activeCompetitions) {
            // Empty out the competition
            if (activeCompetition instanceof LiveCompetition<?> competition) {
                for (ArenaPlayer arenaPlayer : Set.copyOf(competition.getPlayers())) {
                    competition.leave(arenaPlayer.getPlayer(), ArenaLeaveEvent.Cause.REMOVED);
                }
            }
        }

        this.arena.getPlugin().removeArenaMap(this.arena, liveMap);
        Messages.ARENA_REMOVED.send(player, map.getName());
    }

    @ArenaCommand(commands = "edit", description = "Edit an arena map.", permissionNode = "edit")
    public void map(Player player, CompetitionMap map, @Argument(name = "map option") MapOption option) {
        if (!(map instanceof LiveCompetitionMap liveMap)) {
            Messages.NO_ARENA_WITH_NAME.send(player);
            return;
        }

        WizardStage<MapCreateContext> stage = ArenaEditorWizards.MAP_CREATION.getStage(option);
        ArenaEditorWizards.MAP_CREATION.openSingleWizardStage(player, this.arena, stage, context -> context.reconstructFrom(liveMap));
    }

    @ArenaCommand(commands = "advance", description = "Advances to arena to the next phase.", permissionNode = "advance")
    public void advance(Player player) {
        ArenaPlayer arenaPlayer = ArenaPlayer.getArenaPlayer(player);
        if (arenaPlayer == null) {
            Messages.NOT_IN_ARENA.send(player);
            return;
        }

        LiveCompetition<?> competition = arenaPlayer.getCompetition();
        PhaseManager<?> phaseManager = competition.getPhaseManager();
        CompetitionPhaseType<?, ? extends CompetitionPhase<?>> nextPhase = phaseManager.getCurrentPhase().getNextPhase();
        if (nextPhase != null) {
            Messages.ADVANCED_PHASE.send(player, nextPhase.getName());

            phaseManager.setPhase(nextPhase);
        } else {
            Messages.NO_PHASES.send(player);
        }
    }

    @ArenaCommand(commands = "team", subCommands = { "join", "j" }, description = "Joins a team.", permissionNode = "team.join")
    public void joinTeam(Player player, ArenaTeam team) {
        ArenaPlayer arenaPlayer = ArenaPlayer.getArenaPlayer(player);
        if (arenaPlayer == null) {
            Messages.NOT_IN_ARENA.send(player);
            return;
        }

        if (this.arena.getTeams().isNonTeamGame()) {
            Messages.CANNOT_JOIN_TEAM_SOLO.send(player);
            return;
        }

        if (this.arena.getTeams().getTeamSelection() != TeamSelection.PICK) {
            Messages.TEAM_SELECTION_NOT_AVAILABLE.send(player);
            return;
        }

        if (!arenaPlayer.getCompetition().option(ArenaOptionType.TEAM_SELECTION)
                .map(BooleanArenaOption::isEnabled)
                .orElse(true)) {
            Messages.TEAM_SELECTION_NOT_AVAILABLE.send(player);
            return;
        }

        if (team.equals(arenaPlayer.getTeam())) {
            Messages.ALREADY_ON_THIS_TEAM.send(player, team.getFormattedName());
            return;
        }

        LiveCompetition<?> competition = arenaPlayer.getCompetition();
        if (!competition.getTeamManager().canJoinTeam(team)) {
            Messages.TEAM_FULL.send(player, team.getFormattedName());
            return;
        }

        competition.getTeamManager().joinTeam(arenaPlayer, team);
        Messages.TEAM_JOINED.send(player, team.getFormattedName());
    }

    @ArenaCommand(commands = "team", subCommands = { "leave", "l" }, description = "Leaves your current team.", permissionNode = "team.leave")
    public void leaveTeam(Player player) {
        ArenaPlayer arenaPlayer = ArenaPlayer.getArenaPlayer(player);
        if (arenaPlayer == null) {
            Messages.NOT_IN_ARENA.send(player);
            return;
        }

        if (this.arena.getTeams().getTeamSelection() != TeamSelection.PICK) {
            Messages.TEAM_SELECTION_NOT_AVAILABLE.send(player);
            return;
        }

        if (!arenaPlayer.getCompetition().option(ArenaOptionType.TEAM_SELECTION)
                .map(BooleanArenaOption::isEnabled)
                .orElse(true)) {
            Messages.TEAM_SELECTION_NOT_AVAILABLE.send(player);
            return;
        }

        ArenaTeam team = arenaPlayer.getTeam();
        if (team == null) {
            Messages.NOT_ON_TEAM.send(player);
            return;
        }

        LiveCompetition<?> competition = arenaPlayer.getCompetition();
        competition.getTeamManager().leaveTeam(arenaPlayer);

        Messages.TEAM_LEFT.send(player, team.getFormattedName());
    }

    @ArenaCommand(commands = "team", subCommands = "list", description = "List all available teams.", permissionNode = "team.list")
    public void listTeams(Player player) {
        ArenaPlayer arenaPlayer = ArenaPlayer.getArenaPlayer(player);
        if (arenaPlayer == null) {
            Messages.NOT_IN_ARENA.send(player);
            return;
        }

        LiveCompetition<?> competition = arenaPlayer.getCompetition();
        Set<ArenaTeam> availableTeams = competition.getTeamManager().getTeams();
        if (availableTeams.isEmpty() || this.arena.getTeams().isNonTeamGame()) {
            Messages.NO_TEAMS.send(player);
            return;
        }

        Messages.HEADER.sendCentered(player, "Teams");
        for (ArenaTeam team : availableTeams) {
            int players = competition.getTeamManager().getNumberOfPlayersOnTeam(team);
            int max = competition.getTeamManager().getMaximumTeamSize(team);
            if (max == 0) {
                continue; // Don't display empty teams
            }

            String playersText = max == Integer.MAX_VALUE ? " (" + players + ")" : " (" + players + "/" + max + ")";
            player.sendMessage(Component.text("- ", NamedTextColor.GRAY).append(team.getFormattedName()).append(Component.text(playersText, Messages.PRIMARY_COLOR)));
        }
    }

    @Override
    protected Object onVerifyArgument(CommandSender sender, String arg, Class<?> parameter) {
        switch (parameter.getSimpleName().toLowerCase()) {
            case "competition" -> {
                List<Competition<?>> openCompetitions = this.arena.getPlugin().getCompetitions(this.arena, arg);
                if (openCompetitions.isEmpty()) {
                    return null;
                }

                return openCompetitions.get(0);
            }
            case "competitionmap" -> {
                return this.arena.getPlugin().getMap(this.arena, arg);
            }
            case "arenateam" -> {
                return this.arena.getTeams().getAvailableTeams().stream()
                        .filter(team -> team.getName().equalsIgnoreCase(arg))
                        .findFirst()
                        .orElse(null);
            }
        }

        return super.onVerifyArgument(sender, arg, parameter);
    }

    @Override
    protected boolean onInvalidArgument(CommandSender sender, Class<?> parameter, String input) {
        switch (parameter.getSimpleName().toLowerCase()) {
            case "competition", "competitionmap" -> {
                Messages.NO_ARENA_WITH_NAME.send(sender);
                return true;
            }
            case "arenateam" -> {
                Messages.NO_TEAM_WITH_NAME.send(sender, input);
                return true;
            }
        }

        return super.onInvalidArgument(sender, parameter, input);
    }

    @Override
    protected List<String> onVerifyTabComplete(String arg, Class<?> parameter) {
        if (parameter.getSimpleName().equalsIgnoreCase("competition")) {
            return this.arena.getPlugin().getCompetitions(this.arena)
                    .stream()
                    .map(competition -> competition.getMap().getName())
                    .toList();
        } else if (parameter.getSimpleName().equalsIgnoreCase("competitionmap")) {
            return this.arena.getPlugin().getMaps(this.arena)
                    .stream()
                    .map(CompetitionMap::getName)
                    .distinct()
                    .toList();
        } else if (parameter.getSimpleName().equalsIgnoreCase("arenateam")) {
            return this.arena.getTeams().getAvailableTeams().stream()
                    .map(ArenaTeam::getName)
                    .toList();
        }

        return super.onVerifyTabComplete(arg, parameter);
    }

    @Override
    protected String onGetUsageString(Class<?> parameter) {
        return switch (parameter.getSimpleName().toLowerCase()) {
            case "competition" -> "<map> "; // Best name for player-facing values
            case "competitionmap" -> "<map> ";
            case "arenateam" -> "<team> ";
            default -> super.onGetUsageString(parameter);
        };
    }

    @Override
    public final void sendHeader(CommandSender sender) {
        Messages.HEADER.sendCentered(sender, this.arena.getName());
    }
}
