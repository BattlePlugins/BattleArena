package mc.alk.arena.events.matches;

import java.util.List;

import mc.alk.arena.competition.match.Match;
import mc.alk.arena.objects.teams.ArenaTeam;

/**
 * Event that is called when a match begins
 *
 */
public class MatchBeginEvent extends MatchEvent {

    final List<ArenaTeam> teams;

    public MatchBeginEvent(Match match, List<ArenaTeam> teams) {
        super(match);
        this.teams = teams;
    }

    /**
     * Returns the teams for this match
     *
     * @return The teams for this match
     */
    public List<ArenaTeam> getTeams() {
        return teams;
    }

}
