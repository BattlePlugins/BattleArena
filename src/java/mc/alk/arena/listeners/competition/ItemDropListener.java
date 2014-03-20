package mc.alk.arena.listeners.competition;

import mc.alk.arena.listeners.PlayerHolder;
import mc.alk.arena.objects.StateGraph;
import mc.alk.arena.objects.arenas.ArenaListener;
import mc.alk.arena.objects.events.ArenaEventHandler;
import mc.alk.arena.objects.events.EventPriority;
import mc.alk.arena.objects.options.TransitionOption;

import org.bukkit.event.player.PlayerDropItemEvent;

public class ItemDropListener implements ArenaListener{
	StateGraph transitionOptions;
	PlayerHolder match;

	public ItemDropListener(PlayerHolder match){
		this.transitionOptions = match.getParams().getStateGraph();
		this.match = match;
	}

	@ArenaEventHandler(priority=EventPriority.HIGH)
	public void onPlayerDropItem(PlayerDropItemEvent event){
		if (transitionOptions.hasInArenaOrOptionAt(match.getState(), TransitionOption.ITEMDROPOFF)){
			event.setCancelled(true);}
	}
}
