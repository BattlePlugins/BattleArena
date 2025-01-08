package org.battleplugins.arena.module.items.mmoitems;

import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.ItemTier;
import net.Indyuce.mmoitems.api.Type;
import org.battleplugins.arena.BattleArena;
import org.battleplugins.arena.config.ItemStackParser;
import org.battleplugins.arena.config.ParseException;
import org.battleplugins.arena.config.SingularValueParser;
import org.battleplugins.arena.feature.PluginFeature;
import org.battleplugins.arena.feature.items.ItemsFeature;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayDeque;
import java.util.Locale;
import java.util.Queue;

public class MMOItemsFeature extends PluginFeature<ItemsFeature> implements ItemsFeature {

    public MMOItemsFeature() {
        super("MMOItems");
    }

    @Override
    public ItemStack createItem(NamespacedKey key) {
        throw new UnsupportedOperationException("Cannot create MMOItem without arguments!");
    }

    @Override
    public ItemStack createItem(NamespacedKey key, SingularValueParser.ArgumentBuffer arguments) {
        Queue<SingularValueParser.Argument> argumentQueue = new ArrayDeque<>();
        while (arguments.hasNext()) {
            argumentQueue.add(arguments.pop());
        }

        String type = null;
        Integer itemLevel = null;
        String itemTier = null;
        for (SingularValueParser.Argument argument : argumentQueue) {
            switch (argument.key()) {
                case "type" -> type = argument.value();
                case "level" -> itemLevel = Integer.parseInt(argument.value());
                case "tier" -> itemTier = argument.value();
            }
        }

        if (type == null) {
            BattleArena.getInstance().warn("No type provided for MMOItem {}!", key);
            return new ItemStack(Material.AIR);
        }

        Type mmoType = Type.get(type.toUpperCase(Locale.ROOT));
        if (mmoType == null) {
            BattleArena.getInstance().warn("Invalid type {} provided for MMOItem {}!", type, key);
            return new ItemStack(Material.AIR);
        }

        ItemStack itemStack;
        if (itemLevel != null) {
            ItemTier tier = itemTier == null ? null : MMOItems.plugin.getTiers().get(itemTier.toUpperCase(Locale.ROOT));
            itemStack = MMOItems.plugin.getItem(mmoType, key.value(), itemLevel, tier);
        } else {
            itemStack = MMOItems.plugin.getItem(mmoType, key.value());
        }

        if (itemStack == null) {
            BattleArena.getInstance().warn("No MMOItem found for key {}!", key);
            return new ItemStack(Material.AIR);
        }

        try {
            return ItemStackParser.applyItemProperties(itemStack, arguments);
        } catch (ParseException e) {
            ParseException.handle(e);

            return new ItemStack(Material.AIR);
        }
    }
}
