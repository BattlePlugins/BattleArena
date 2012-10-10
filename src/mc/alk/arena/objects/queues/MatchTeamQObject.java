package mc.alk.arena.objects.queues;

import java.util.Collection;

import mc.alk.arena.objects.ArenaPlayer;
import mc.alk.arena.objects.teams.Team;
import mc.alk.arena.objects.tournament.Matchup;

public class MatchTeamQObject extends QueueObject{
	final Matchup matchup;
	final Integer priority;
	
	public MatchTeamQObject(Matchup matchup){
		mp = matchup.getMatchParams();
		this.matchup = matchup;
		this.priority = matchup.getPriority();
	}

	@Override
	public Integer getPriority() {
		return priority;
	}

	@Override
	public boolean hasMember(ArenaPlayer p) {
		return matchup.hasMember(p);
	}

	@Override
	public Team getTeam(ArenaPlayer p) {
		return matchup.getTeam(p);
	}

	@Override
	public int size() {
		return matchup.size();
	}

	@Override
	public String toString(){
		return priority+" " + matchup.toString();
	}

	@Override
	public Collection<Team> getTeams() {
		return matchup.getTeams();
	}

	public Matchup getMatchup() {
		return matchup;
	}
}
