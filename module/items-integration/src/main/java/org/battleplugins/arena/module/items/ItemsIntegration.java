package org.battleplugins.arena.module.items;

import org.battleplugins.arena.BattleArena;
import org.battleplugins.arena.event.BattleArenaPostInitializeEvent;
import org.battleplugins.arena.feature.PluginFeature;
import org.battleplugins.arena.feature.items.Items;
import org.battleplugins.arena.feature.items.ItemsFeature;
import org.battleplugins.arena.module.ArenaModule;
import org.battleplugins.arena.module.ArenaModuleInitializer;
import org.battleplugins.arena.module.items.itemsadder.ItemsAdderFeature;
import org.battleplugins.arena.module.items.magic.MagicFeature;
import org.battleplugins.arena.module.items.mmoitems.MMOItemsFeature;
import org.battleplugins.arena.module.items.mythiccrucible.MythicCrucibleFeature;
import org.battleplugins.arena.module.items.oraxen.OraxenFeature;
import org.battleplugins.arena.module.items.qualityarmory.QualityArmoryFeature;
import org.battleplugins.arena.module.items.weaponmechanics.WeaponMechanicsFeature;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

import java.util.function.Supplier;

/**
 * A module that allows for hooking into various item provider plugins.
 */
@ArenaModule(id = ItemsIntegration.ID, priority = 100, name = "Items", description = "Adds support for hooking into various item provider plugins.", authors = "BattlePlugins")
public class ItemsIntegration implements ArenaModuleInitializer {
    public static final String ID = "items";

    @EventHandler(priority = EventPriority.LOWEST) // Load before all other modules listening on this event
    public void onPostInitialize(BattleArenaPostInitializeEvent event) {
        BattleArena plugin = event.getBattleArena();

        registerProvider(plugin, "QualityArmory", QualityArmoryFeature::new);
        registerProvider(plugin, "Oraxen", OraxenFeature::new);
        registerProvider(plugin, "ItemsAdder", ItemsAdderFeature::new);
        registerProvider(plugin, "MythicCrucible", MythicCrucibleFeature::new);
        registerProvider(plugin, "Magic", MagicFeature::new);
        registerProvider(plugin, "MMOItems", MMOItemsFeature::new);
        registerProvider(plugin, "WeaponMechanics", WeaponMechanicsFeature::new);
    }

    private static <T extends PluginFeature<ItemsFeature> & ItemsFeature> void registerProvider(BattleArena plugin, String pluginName, Supplier<T> feature) {
        if (Bukkit.getPluginManager().isPluginEnabled(pluginName)) {
            Items.register(feature.get());

            plugin.info("{} found. Registering item integration.", pluginName);
        }
    }
}
