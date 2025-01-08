package org.battleplugins.arena.module.items.magic;

import com.elmakers.mine.bukkit.api.magic.MagicAPI;
import org.battleplugins.arena.feature.PluginFeature;
import org.battleplugins.arena.feature.items.ItemsFeature;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

public class MagicFeature extends PluginFeature<ItemsFeature> implements ItemsFeature {
    private final MagicAPI magicAPI;

    public MagicFeature() {
        super("Magic");

        if (!(this.getPlugin() instanceof MagicAPI magicAPI)) {
            throw new IllegalStateException("MagicAPI not found!");
        }

        this.magicAPI = magicAPI;
    }

    @Override
    public ItemStack createItem(NamespacedKey key) {
        return this.magicAPI.createItem(key.value());
    }
}
