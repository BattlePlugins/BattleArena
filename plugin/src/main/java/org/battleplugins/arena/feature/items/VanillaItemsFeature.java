package org.battleplugins.arena.feature.items;

import org.battleplugins.arena.config.ItemStackParser;
import org.battleplugins.arena.config.ParseException;
import org.battleplugins.arena.config.SingularValueParser;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

class VanillaItemsFeature implements ItemsFeature {
    static final VanillaItemsFeature INSTANCE = new VanillaItemsFeature();

    private VanillaItemsFeature() {
    }

    @Override
    public ItemStack createItem(NamespacedKey key) {
        throw new UnsupportedOperationException("Cannot create vanilla item without arguments!");
    }

    @Override
    public ItemStack createItem(NamespacedKey key, SingularValueParser.ArgumentBuffer arguments) {
        try {
            return ItemStackParser.deserializeSingularVanilla(key, arguments);
        } catch (ParseException e) {
            ParseException.handle(e);
            return new ItemStack(Material.AIR);
        }
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
