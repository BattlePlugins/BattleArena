package org.battleplugins.arena.module.items.weaponmechanics;

import me.deecaad.weaponmechanics.WeaponMechanicsAPI;
import org.battleplugins.arena.feature.PluginFeature;
import org.battleplugins.arena.feature.items.ItemsFeature;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

public class WeaponMechanicsFeature extends PluginFeature<ItemsFeature> implements ItemsFeature {

    public WeaponMechanicsFeature() {
        super("WeaponMechanics");
    }

    @Override
    public ItemStack createItem(NamespacedKey key) {
        return WeaponMechanicsAPI.generateWeapon(key.value());
    }
}
