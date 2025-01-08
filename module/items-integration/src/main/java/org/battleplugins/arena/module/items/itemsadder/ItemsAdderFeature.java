package org.battleplugins.arena.module.items.itemsadder;

import dev.lone.itemsadder.api.CustomStack;
import org.battleplugins.arena.feature.PluginFeature;
import org.battleplugins.arena.feature.items.ItemsFeature;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

public class ItemsAdderFeature extends PluginFeature<ItemsFeature> implements ItemsFeature {

    public ItemsAdderFeature() {
        super("ItemsAdder");
    }

    @Override
    public ItemStack createItem(NamespacedKey key) {
        CustomStack customStack = CustomStack.getInstance(key.value());
        return customStack == null ? null : customStack.getItemStack();
    }
}
