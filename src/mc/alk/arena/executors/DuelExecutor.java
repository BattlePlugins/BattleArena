package mc.alk.arena.executors;

import mc.alk.arena.BattleArena;
import mc.alk.arena.objects.ArenaPlayer;
import mc.alk.arena.objects.MatchParams;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;


public class DuelExecutor{
	protected static final int SELF = CustomCommandExecutor.SELF;

	public DuelExecutor(){
		BattleArena.getBAExecutor().addMethods(this, this.getClass().getMethods());
	}

	@MCCommand( cmds = {"duel"}, inGame=true, online={1}, min=2, perm="arena.duel",usage="duel <player> [options]")
	public void duel(CommandSender sender, MatchParams mp, ArenaPlayer p1, ArenaPlayer p2, Object[] args){
		
	}

	@MCCommand( cmds = {"accept"}, min=2, usage="accept")
	public void accept(CommandSender sender, Command command, String label, Object[] args){
		
	}

	@MCCommand( cmds = {"retract"}, min=2, usage="retract")
	public void retract(CommandSender sender, Command command, String label, Object[] args){
		
	}

}
