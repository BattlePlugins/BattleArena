package org.battleplugins.arena.module.items.qualityarmory;

import me.zombie_striker.qg.api.QualityArmory;
import org.battleplugins.arena.feature.PluginFeature;
import org.battleplugins.arena.feature.items.ItemsFeature;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

public class QualityArmoryFeature extends PluginFeature<ItemsFeature> implements ItemsFeature {

    public QualityArmoryFeature() {
        super("QualityArmory");
    }

    @Override
    public ItemStack createItem(NamespacedKey key) {
        return QualityArmory.getCustomItemAsItemStack(key.value());
    }
}
