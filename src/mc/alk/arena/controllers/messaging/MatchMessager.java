package mc.alk.arena.controllers.messaging;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import mc.alk.arena.competition.match.Match;
import mc.alk.arena.objects.MatchState;
import mc.alk.arena.objects.messaging.AnnouncementOptions;
import mc.alk.arena.objects.messaging.Channel;
import mc.alk.arena.objects.teams.Team;


public class MatchMessager {
	MatchMessageHandler impl;
	final AnnouncementOptions bos;
	boolean silent = false;

	public MatchMessager(Match match){
		this.impl = new MatchMessageImpl(match);
		this.bos = match.getParams().getAnnouncementOptions();
	}

	private Channel getChannel(MatchState state) {
		if (silent) return Channel.NullChannel;
		return bos != null && bos.hasOption(true,state) ? bos.getChannel(true,state) : AnnouncementOptions.getDefaultChannel(true,state);
	}

	public void sendOnBeginMsg(List<Team> teams) {
		try{impl.sendOnBeginMsg(getChannel(MatchState.ONBEGIN), teams);}catch(Exception e){e.printStackTrace();}
	}

	public void sendOnPreStartMsg(List<Team> teams) {
		try{impl.sendOnPreStartMsg(getChannel(MatchState.ONPRESTART), teams);}catch(Exception e){e.printStackTrace();}
	}

	public void sendOnStartMsg(List<Team> teams) {
		try{impl.sendOnStartMsg(getChannel(MatchState.ONSTART), teams);}catch(Exception e){e.printStackTrace();}
	}

	public void sendOnVictoryMsg(Team victor, Collection<Team> losers) {
		try{impl.sendOnVictoryMsg(getChannel(MatchState.ONVICTORY), victor,losers);}catch(Exception e){e.printStackTrace();}
	}

	public void sendYourTeamNotReadyMsg(Team t1) {
		try{impl.sendYourTeamNotReadyMsg(t1);}catch(Exception e){e.printStackTrace();}
	}

	public void sendOtherTeamNotReadyMsg(Team t1) {
		try{impl.sendOtherTeamNotReadyMsg(t1);}catch(Exception e){e.printStackTrace();}
	}

	public void sendOnIntervalMsg(int remaining, List<Team> currentLeaders) {
		try{impl.sendOnIntervalMsg(getChannel(MatchState.ONMATCHINTERVAL), currentLeaders, remaining);}catch(Exception e){e.printStackTrace();}
	}

	public void sendTimeExpired() {
		try{impl.sendTimeExpired(getChannel(MatchState.ONMATCHTIMEEXPIRED));}catch(Exception e){e.printStackTrace();}
	}

	public void setMessageHandler(MatchMessageHandler mc) {
		this.impl = mc;
	}

	public MatchMessageHandler getMessageHandler() {
		return impl;
	}
	public void setSilent(boolean silent){
		this.silent = silent;
	}

	public void sendOnDrawMessage(Set<Team> losers) {
		try{impl.sendOnDrawMsg(getChannel(MatchState.ONVICTORY), losers);}catch(Exception e){e.printStackTrace();}
	}
}
