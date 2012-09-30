package mc.alk.arena.executors;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import mc.alk.arena.BattleArena;
import mc.alk.arena.Defaults;
import mc.alk.arena.competition.events.Event;
import mc.alk.arena.controllers.EventController;
import mc.alk.arena.controllers.PlayerController;
import mc.alk.arena.controllers.TeamController;
import mc.alk.arena.objects.ArenaPlayer;
import mc.alk.arena.objects.teams.FormingTeam;
import mc.alk.arena.objects.teams.Team;
import mc.alk.arena.objects.teams.TeamHandler;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.alk.virtualPlayer.VirtualPlayers;

public class TeamExecutor extends CustomCommandExecutor {
	TeamController teamc; 
	BAExecutor bae;
	public TeamExecutor(BAExecutor bae) {
		super();
		this.teamc = BattleArena.getTeamController();
		this.bae = bae;
	}

	@MCCommand(cmds={"list"},admin=true,usage="list")
	public boolean teamList(CommandSender sender) {
		StringBuilder sb = new StringBuilder();
		Map<Team,CopyOnWriteArrayList<TeamHandler>> teams = teamc.getTeams();

		for (Team t: teams.keySet()){
			sb.append(t.getTeamInfo(null)+"\n");}
		sb.append("&e# of players = &6" + teams.size());
		return sendMessage(sender,sb.toString());
	}

	@MCCommand(cmds={"listDetailed"},op=true,usage="listDetailed")
	public boolean teamListDetails(CommandSender sender) {
		StringBuilder sb = new StringBuilder();
		Map<Team,CopyOnWriteArrayList<TeamHandler>> teams = teamc.getTeams();

		for (Team t: teams.keySet()){
			sb.append(t.getTeamInfo(null));
			sb.append(" &5Handlers:");
			if (teams.get(t) == null){
				sb.append("null");
				continue;
			}
			for (TeamHandler th: teams.get(t)){
				sb.append(th);}
			sb.append("\n");
		}
		sb.append("&e# of players = &6" + teams.size());
		return sendMessage(sender,sb.toString());
	}

	@MCCommand(cmds={"join"},inGame=true,usage="join")
	public boolean teamJoin(ArenaPlayer player) {

		Team t = teamc.getSelfTeam(player);
		if (t != null && t.size() >1){
			return sendMessage(player, "&cYou are already part of a team with &6" + t.getOtherNames(player));			
		}
		List<TeamHandler> handlers = teamc.getHandlers(t);
		if (handlers != null && !handlers.isEmpty()){
			for (TeamHandler th: handlers){
				return sendMessage(player, "&cYou cant join until the &6"+th+"&c ends");}
		}
		
//		Event ae = EventController.insideEvent(p);
//		if (ae != null){
//			return sendMessage(sender, "&eYou need to leave the event first. &6/" + ae.getCommand()+" leave");
//		}

		if (!teamc.inFormingTeam(player)){
			sendMessage(player,ChatColor.RED + "You are not part of a forming team");
			return sendMessage(player,ChatColor.YELLOW + "Usage: &6/team create <player2> [player3]...");
		}
		FormingTeam ft = teamc.getFormingTeam(player);

		ft.sendJoinedPlayersMessage(ChatColor.YELLOW + player.getName() + " has joined the team");
		ft.joinTeam(player);
		sendMessage(player,ChatColor.YELLOW + "You have joined the team with");
		sendMessage(player,ChatColor.GOLD + ft.toString());

		if (ft.hasAllPlayers()){
			ft.sendMessage("&2Your team is now complete.  you can now join an event or arena");
			teamc.removeFormingTeam(ft);
			teamc.addSelfTeam(ft);
		}
		return true;
	}

	@MCCommand(cmds={"create"},inGame=true,usage="create <player 1> <player 2>...<player x>")
	public boolean teamCreate(ArenaPlayer player, String[] args) {
		if (args.length<2){
			sendMessage(player,ChatColor.YELLOW + "create <player 1> <player 2>...<player x>");
			sendMessage(player,ChatColor.YELLOW + "You need to have at least 1 person in the team");
			return true;
		}
		if (!bae.canJoin(player)){
			return true;
		}
		Set<String> players = new HashSet<String>();
		Set<Player> foundplayers = new HashSet<Player>();
		Set<String> unfoundplayers = new HashSet<String>();
		for (int i=1;i<args.length;i++){
			players.add((String)args[i]);}
		if (players.contains(player.getName()))
			return sendMessage(player,ChatColor.YELLOW + "You can not invite yourself to a team");

		findOnlinePlayers(players, foundplayers,unfoundplayers);
		if (foundplayers.size() < players.size()){
			sendMessage(player,ChatColor.YELLOW + "The following teammates were not found or were not online");
			StringBuilder sb = new StringBuilder();
			boolean first = true;
			for (String n : unfoundplayers){
				if (!first) sb.append(",");
				sb.append(n);
				first = false;
			}
			sendMessage(player,ChatColor.YELLOW + sb.toString());
			return true;
		}
		Set<ArenaPlayer> foundArenaPlayers = PlayerController.toArenaPlayerSet(foundplayers);
		for (ArenaPlayer p: foundArenaPlayers){
			
			if (Defaults.DEBUG){System.out.println("player=" + player.getName());}
			Team t = teamc.getSelfTeam(p);
			if (t!= null || !bae.canJoin(p)){
				sendMessage(player,"&6"+ p.getName() + "&e is already part of a team or is in an Event");
				return sendMessage(player,"&eCreate team &4cancelled!");
			}
			if (teamc.inFormingTeam(p)){
				sendMessage(player,"&6"+ p.getName() + "&e is already part of a forming team");
				return sendMessage(player,"&eCreate team &4cancelled!");
			}
		}
		foundArenaPlayers.add(player);
		if (Defaults.DEBUG){System.out.println(player.getName() + "  players=" + foundArenaPlayers.size());}

		if (!ac.hasArenaSize(foundArenaPlayers.size())){
			sendMessage(player,"&6[Warning]&eAn arena for that many players has not been created yet!");
		}
		/// Finally ready to create a team
		FormingTeam ft = new FormingTeam(player, foundArenaPlayers);
		teamc.addFormingTeam(ft);
		sendMessage(player,ChatColor.YELLOW + "You are now forming a team. The others must accept by using &6/team join");

		/// Send a message to the other teammates
		for (ArenaPlayer p: ft.getPlayers()){
			if (player.equals(p))
				continue;
			sendMessage(p, "&eYou have been invited to a team with &6" + ft.getOtherNames(player));
			sendMessage(p, "&6/team join&e : to accept: &6/team decline&e to refuse ");
		}
		return true;
	}

	@MCCommand(cmds={"info"},inGame=true, usage="info")
	public boolean teamInfo(ArenaPlayer player) {
		Map<TeamHandler,Team> teams = teamc.getTeamMap(player);
		if (teams == null || teams.isEmpty()){
			return sendMessage(player,"&eYou are not in a team");}
		final int size = teams.size();
		for (TeamHandler th : teams.keySet()){
			if (size == 1){
				return sendMessage(player,teams.get(th).getTeamInfo(null));}
			else {
				sendMessage(player,teams.get(th).getTeamInfo(null));
			}
		}
		return true;
	}

	@MCCommand(cmds={"info"}, min=2, admin=true, usage="info <player>", order=1)
	public boolean teamInfoOther(CommandSender sender,ArenaPlayer player) {
		Map<TeamHandler,Team> teams = teamc.getTeamMap(player);
		if (teams == null || teams.isEmpty()){
			return sendMessage(sender,"&ePlayer &6" + player.getName() +"&e is not in a team");}

		for (TeamHandler th : teams.keySet()){
			sendMessage(sender,th +" " + teams.get(th).getTeamInfo(null));
		}
		return true;
	}

	@MCCommand(cmds={"disband","leave"},usage="disband")
	public boolean teamDisband(ArenaPlayer player) {
		/// Try to disband a forming team first
		FormingTeam ft = teamc.getFormingTeam(player);
		if (ft != null){
			teamc.removeFormingTeam(ft);
			ft.sendMessage("&eYour team has been disbanded by " + player.getName());
			return true;
		}
		
		/// If in a self made team, let them disband it regardless
		/// This will cause the team to try and leave the event, queue, or whatever
		Team t = teamc.getSelfTeam(player);
		if (t== null){
			return sendMessage(player,"&eYou aren't part of a team");}

		teamc.removeSelfTeam(t);
		t.sendMessage("&eYour team has been disbanded by " + player.getName());

		/// Iterate over the handlers
		/// try and leave each one (hopefully they are only in 1)
		List<TeamHandler> handlers = teamc.getHandlers(t);
		if (handlers != null && !handlers.isEmpty()){
			for (TeamHandler th: handlers){
				for (ArenaPlayer p : t.getPlayers()){
					if (!th.canLeave(p)){
						sendMessage(p, "&cYou have disbanded your team but still cant leave the &6" + th);
					} else {
						sendMessage(p, "&2You are leaving the &6" + th);
						th.leave(p);
					}
				}				
			}
		}
//		if (ac.removeFromQue(t) != null){
//			t.sendMessage(ChatColor.YELLOW + "You have left the queue");
//		}

//		Event ae = EventController.insideEvent(pl);
//		if (ae != null){
//			if (ae.canLeave(pl)){
//				ae.leave(pl);
//				t.sendMessage("&eThe team has left the &6" + ae.getName());
//			} else {
//				return MatchMessager.sendMessage(pl, "&eYou can't leave the &6" +ae.getName()+"&e while its " + ae.getState());
//			}
//		} 
//		if (!bae.canLeave(sender, pl)){
//			return true;
//		}

		return true;
	}

	@MCCommand(cmds={"delete"},admin=true,online={1}, usage="delete")
	public boolean teamDelete(CommandSender sender, ArenaPlayer player) {
		Team t = teamc.getSelfTeam(player);
		if (t== null){
			return sendMessage(sender,ChatColor.YELLOW + player.getName() + " is not part of a team");}
		Event ae = EventController.insideEvent(player);
		if (ae != null){
			ae.leave(player);
		} else {
			teamc.removeSelfTeam(t);	
		}
		t.sendMessage(ChatColor.YELLOW + "The team has been disbanded ");
		return true;
	}


	@MCCommand(cmds={"decline"},inGame=true, usage="decline")
	public boolean teamDecline(ArenaPlayer p) {
		FormingTeam t = teamc.getFormingTeam(p);
		if (t== null){
			sendMessage(p,ChatColor.YELLOW + "You are not part of a forming team");
			return true;
		} 
		t.sendMessage(ChatColor.YELLOW + "The team has been disbanded as " + p.getDisplayName() +" has declined");
		teamc.removeFormingTeam(t);
		return true;
	}

	protected void findOnlinePlayers(Set<String> names, Set<Player> foundplayers,Set<String> unfoundplayers) {
		Player[] online = Bukkit.getOnlinePlayers();
		if (Defaults.DEBUG_VIRTUAL){online =  VirtualPlayers.getOnlinePlayers();}
		for (String name : names){
			Player lastPlayer = null;
			for (Player player : online) {
				String playerName = player.getName();
				if (playerName.equalsIgnoreCase(name)) {
					lastPlayer = player;
					break;
				}

				if (playerName.toLowerCase().indexOf(name.toLowerCase()) != -1) { /// many names match the one given
					if (lastPlayer != null) {
						lastPlayer = null;
						break;
					}
					lastPlayer = player;
				}
			}
			if (lastPlayer != null){
				foundplayers.add(lastPlayer);
			} else{
				unfoundplayers.add(name);
			}
		}
	}

	@MCCommand( cmds = {"help","?"})
	public void help(CommandSender sender, Command command, String label, Object[] args){
		super.help(sender, command, args);
	}

}