package org.battleplugins.arena.module.items.oraxen;

import io.th0rgal.oraxen.api.OraxenItems;
import io.th0rgal.oraxen.items.ItemBuilder;
import org.battleplugins.arena.feature.PluginFeature;
import org.battleplugins.arena.feature.items.ItemsFeature;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

public class OraxenFeature extends PluginFeature<ItemsFeature> implements ItemsFeature {

    public OraxenFeature() {
        super("Oraxen");
    }

    @Override
    public ItemStack createItem(NamespacedKey key) {
        return OraxenItems.getOptionalItemById(key.value())
                .map(ItemBuilder::build)
                .orElse(null);
    }
}
