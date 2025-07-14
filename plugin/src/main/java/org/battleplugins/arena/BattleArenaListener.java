package org.battleplugins.arena;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.battleplugins.arena.competition.PlayerStorage;
import org.battleplugins.arena.editor.ArenaEditorWizard;
import org.battleplugins.arena.event.BattleArenaPostInitializeEvent;
import org.battleplugins.arena.util.Util;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

class BattleArenaListener implements Listener {
    private final BattleArena plugin;

    public BattleArenaListener(BattleArena plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        // There is logic called later, however by this point all plugins
        // using the BattleArena API should have been loaded. As modules will
        // listen for this event to register their behavior, we need to ensure
        // they are fully initialized so any references to said modules in
        // arena config files will be valid.
        new BattleArenaPostInitializeEvent(this.plugin).callEvent();

        this.plugin.postInitialize();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        String message = PlainTextComponentSerializer.plainText().serialize(event.originalMessage());
        if (message.equalsIgnoreCase("cancel")) {
            ArenaEditorWizard.wizardContext(event.getPlayer()).ifPresent(ctx -> {
                event.setCancelled(true);

                ctx.getWizard().onCancel(ctx);
            });
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Check to see if the player has a last location stored from when they last logged off.
        // If so, we need to teleport them to that location when they join the server.
        PersistentDataContainer container = event.getPlayer().getPersistentDataContainer();
        if (container.has(PlayerStorage.LAST_LOCATION_KEY, PersistentDataType.STRING)) {
            String lastLocationStr = container.get(PlayerStorage.LAST_LOCATION_KEY, PersistentDataType.STRING);

            // Remove before we proceed - we do not want this data lingering in the case of an error
            container.remove(PlayerStorage.LAST_LOCATION_KEY);

            Location lastLocation = Util.stringToLocation(lastLocationStr);
            if (lastLocation.getWorld() == null) {
                // If the world is null, we cannot teleport the player
                // This can happen if the world was deleted or is not loaded

                BattleArena.getInstance().getSLF4JLogger().warn("Could not teleport player {} to their last location {} because the world is null.", event.getPlayer().getName(), lastLocationStr);
                return;
            }

            // Teleport the player to their last location
            event.getPlayer().teleport(lastLocation);
        }
    }
}
