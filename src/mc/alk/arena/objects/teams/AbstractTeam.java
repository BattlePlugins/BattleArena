package mc.alk.arena.objects.teams;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import mc.alk.arena.objects.ArenaPlayer;
import mc.alk.arena.util.MessageUtil;

import org.bukkit.entity.Player;

public abstract class AbstractTeam implements Team{
	static int count = 0;
	final int id = count++; /// id

	protected Set<ArenaPlayer> players = Collections.synchronizedSet(new HashSet<ArenaPlayer>());
	protected Set<ArenaPlayer> deadplayers = Collections.synchronizedSet(new HashSet<ArenaPlayer>());
	protected Set<ArenaPlayer> leftplayers = Collections.synchronizedSet(new HashSet<ArenaPlayer>());

	protected boolean nameManuallySet = false;
	protected boolean nameChanged = true;
	protected String name =null; /// Internal name of this team
	protected String displayName =null; /// Display name

	HashMap<ArenaPlayer, Integer> kills = new HashMap<ArenaPlayer,Integer>();
	HashMap<ArenaPlayer, Integer> deaths = new HashMap<ArenaPlayer,Integer>();

	/// Pickup teams are transient in nature, once the match end they disband
	protected boolean isPickupTeam = false;

	/**
	 * Default Constructor
	 */
	public AbstractTeam(){}

	protected AbstractTeam(ArenaPlayer p) {
		players.add(p);
		nameChanged = true;
	}

	protected AbstractTeam(Collection<ArenaPlayer> teammates) {
		this.players.addAll(teammates);
		nameChanged = true;
	}

	protected AbstractTeam(ArenaPlayer p, Collection<ArenaPlayer> teammates) {
		players.add(p);
		players.addAll(teammates);
		nameChanged = true;
	}

	@Override
	public void init(){
		reset();
	}

	public void reset() {
		deaths.clear();
		kills.clear();
		setAlive();
		for (ArenaPlayer ap: players){
			if (leftplayers.contains(ap))
				continue;
			ap.reset();
		}
	}

	protected void createName() {
		if (nameManuallySet || !nameChanged){ ///
			return;}
		/// Sort the names and then append them together
		ArrayList<String> list = new ArrayList<String>(players.size());
		for (ArenaPlayer p:players){list.add(p.getName());}
		if (list.size() > 1)
			Collections.sort(list);
		StringBuilder sb = new StringBuilder();
		boolean first = true;
		for (String s: list){
			if (!first) sb.append(", ");
			sb.append(s);
			first = false;
		}
		name= sb.toString();
		nameChanged = false;
	}

	public Set<ArenaPlayer> getPlayers() {
		return players;
	}
	public Set<Player> getBukkitPlayers() {
		Set<Player> ps = new HashSet<Player>();
		for (ArenaPlayer ap: players){
			ps.add(ap.getPlayer());
		}
		return ps;
	}
	public Set<ArenaPlayer> getDeadPlayers() {return deadplayers;}
	public Set<ArenaPlayer> getLivingPlayers() {
		Set<ArenaPlayer> living = new HashSet<ArenaPlayer>();
		for (ArenaPlayer p : players){
			if (hasAliveMember(p)){
				living.add(p);}
		}
		return living;
	}
	public boolean wouldBeDeadWithout(ArenaPlayer p) {
		Set<ArenaPlayer> living = getLivingPlayers();
		living.remove(p);
		int offline = 0;
		for (ArenaPlayer ap: living){
			if (!ap.isOnline())
				offline++;
		}
		return living.isEmpty() || living.size() <= offline;
	}

	public boolean hasMember(ArenaPlayer p) {return players.contains(p) && !leftplayers.contains(p);}
	public boolean hasLeft(ArenaPlayer p) {return leftplayers.contains(p);}
	public boolean hasAliveMember(ArenaPlayer p) {return hasMember(p) && !deadplayers.contains(p);}
	public boolean isPickupTeam() {return isPickupTeam;}
	public void setPickupTeam(boolean isPickupTeam) {this.isPickupTeam = isPickupTeam;}
	public void setHealth(int health) {for (ArenaPlayer p: players){p.setHealth(health);}}
	public void setHunger(int hunger) {for (ArenaPlayer p: players){p.setFoodLevel(hunger);}}
	public String getName() {
		createName();
		return name;
	}
	public int getId(){ return id;}
	public void setName(String name) {
		this.name = name;
		this.nameManuallySet = true;
	}
	public void setAlive() {deadplayers.clear();}

	@Override
	public void setAlive(ArenaPlayer player){deadplayers.remove(player);}

	public boolean isDead() {
		if (deadplayers.size() >= players.size())
			return true;
		Set<ArenaPlayer> living = getLivingPlayers();
		if (living.isEmpty())
			return true;
		int offline = 0;
		for (ArenaPlayer ap: living){
			if (!ap.isOnline()){
				offline++;}
		}
		return living.size() <= offline;
	}

	@Override
	public boolean isReady() {
		for (ArenaPlayer ap: getLivingPlayers()){
			if (!ap.isReady())
				return false;
		}
		return true;
	}

	public int size() {return players.size()-leftplayers.size();}



	public void addDeath(ArenaPlayer teamMemberWhoDied) {
		Integer d = deaths.get(teamMemberWhoDied);
		if (d == null){
			d = 0;}
		deaths.put(teamMemberWhoDied, ++d);
	}

	public void addKill(ArenaPlayer teamMemberWhoKilled){
		Integer d = kills.get(teamMemberWhoKilled);
		if (d == null){
			d = 0;}
		kills.put(teamMemberWhoKilled, ++d);
	}

	public int getNKills() {
		int nkills = 0;
		for (Integer i: kills.values()) nkills+=i;
		return nkills;
	}

	public int getNDeaths() {
		int nkills = 0;
		for (Integer i: deaths.values()) nkills+=i;
		return nkills;
	}

	public Integer getNDeaths(ArenaPlayer p) {
		return deaths.get(p);
	}

	public Integer getNKills(ArenaPlayer p) {
		return kills.get(p);
	}

	/**
	 *
	 * @param p
	 * @return whether all players are dead
	 */
	public boolean killMember(ArenaPlayer p) {
		if (!hasMember(p))
			return false;
		deadplayers.add(p);
		return deadplayers.size() == players.size();
	}

	/**
	 *
	 * @param p
	 * @return whether all players are dead
	 */
	public void playerLeft(ArenaPlayer p) {
		if (!hasMember(p))
			return ;
		leftplayers.add(p);
	}

	public boolean allPlayersOffline() {
		for (ArenaPlayer p: players){
			if (p.isOnline())
				return false;
		}
		return true;
	}

	public void sendMessage(String message) {
		for (ArenaPlayer p: players){
			if (!leftplayers.contains(p)){
				MessageUtil.sendMessage(p, message);}}
	}
	public void sendToOtherMembers(ArenaPlayer player, String message) {
		for (ArenaPlayer p: players){
			if (!p.equals(player))
				MessageUtil.sendMessage(p, message);}
	}

	public String getDisplayName(){return displayName == null ? getName() : displayName;}
	public void setDisplayName(String n){displayName = n;}

	@Override
	public boolean equals(Object other) {
		if (this == other) return true;
		if (!(other instanceof AbstractTeam)) return false;
		return this.hashCode() == ((AbstractTeam) other).hashCode();
	}

	@Override
	public int hashCode() { return id;}

	@Override
	public String toString(){return "["+getDisplayName()+"]";}

	public boolean hasTeam(Team team){
		if (team instanceof CompositeTeam){
			for (Team t: ((CompositeTeam)team).getOldTeams()){
				if (this.hasTeam(t))
					return true;
			}
			return false;
		} else {
			return this.equals(team);
		}
	}

	public String getTeamInfo(Set<String> insideMatch){
		StringBuilder sb = new StringBuilder("&eTeam: ");
		if (displayName != null) sb.append(displayName);
		sb.append( " " + (isDead() ? "&4dead" : "&aalive")+"&e, ");

		for (ArenaPlayer p: players){
			sb.append("&6"+p.getName());
			boolean isAlive = hasAliveMember(p);
			boolean online = p.isOnline();
			final String inmatch = insideMatch == null? "": ((insideMatch.contains(p.getName())) ? "&e(in)" : "&4(out)");
			final int k = kills.containsKey(p) ? kills.get(p) : 0;
			final int d = deaths.containsKey(p) ? deaths.get(p) : 0;
			sb.append("&e(&c"+k+"&e,&7"+d+"&e)");
			sb.append("&e:" + (isAlive ? "&ah="+p.getHealth() : "&40") +
					((!online) ? "&4(O)" : "")+inmatch+"&e ");
		}
		return sb.toString();
	}

	public String getTeamSummary() {
		StringBuilder sb = new StringBuilder("&6"+getDisplayName());
		for (ArenaPlayer p: players){
			final int k = kills.containsKey(p) ? kills.get(p) : 0;
			final int d = deaths.containsKey(p) ? deaths.get(p) : 0;
			sb.append("&e(&c"+k+"&e,&7"+d+"&e)");
		}
		return sb.toString();
	}

	public String getOtherNames(ArenaPlayer player) {
		StringBuilder sb = new StringBuilder();
		boolean first = true;
		for (ArenaPlayer p: players){
			if (p.equals(player))
				continue;
			if (!first) sb.append(", ");
			sb.append(p.getName());
			first = false;
		}
		return sb.toString();
	}

	public boolean hasSetName() {
		return this.nameManuallySet;
	}

	public int getPriority() {
		int priority = Integer.MAX_VALUE;
		for (ArenaPlayer ap: players){
			if (ap.getPriority() < priority)
				priority = ap.getPriority();
		}
		return priority;
	}

	@Override
	public void addPlayer(ArenaPlayer player) {
		this.players.add(player);
		this.leftplayers.remove(player);
		this.nameChanged = true;
	}

	@Override
	public void removePlayer(ArenaPlayer player) {
		this.players.remove(player);
		this.deadplayers.remove(player);
		this.kills.remove(player);
		this.deaths.remove(player);
		this.nameChanged = true;
	}

	@Override
	public void addPlayers(Collection<ArenaPlayer> players) {
		this.players.addAll(players);
		this.nameChanged = true;
	}

	@Override
	public void removePlayers(Collection<ArenaPlayer> players) {
		this.players.removeAll(players);
		this.deadplayers.removeAll(players);
		for (ArenaPlayer ap: players){
			this.kills.remove(ap);
			this.deaths.remove(ap);
		}
		this.nameChanged = true;
	}

}

