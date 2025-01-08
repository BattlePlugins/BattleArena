package org.battleplugins.arena.feature.items;

import org.battleplugins.arena.config.SingularValueParser;
import org.battleplugins.arena.feature.FeatureController;
import org.battleplugins.arena.feature.PluginFeature;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Locale;

/**
 * API for items used in BattleArena.
 * <p>
 * This API serves as a service provider to allow for
 * accessing and creating items from Minecraft as well
 * as third parties.
 */
@ApiStatus.Experimental
public final class Items extends FeatureController<PluginFeature<ItemsFeature>> {

    /**
     * Creates an {@link ItemStack} from the given key and arguments.
     *
     * @param key the key of the item
     * @param arguments the arguments to create the item with
     * @return the created item
     */
    public static ItemStack createItem(NamespacedKey key, SingularValueParser.ArgumentBuffer arguments) {
        return getFeature(key).createItem(key, arguments);
    }

    /**
     * Registers a {@link ItemsFeature} to the feature controller.
     *
     * @param feature the feature to register
     */
    public static <T extends PluginFeature<ItemsFeature> & ItemsFeature> void register(T feature) {
        registerFeature(ItemsFeature.class, feature);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static <T extends PluginFeature<ItemsFeature> & ItemsFeature> ItemsFeature getFeature(NamespacedKey key) {
        // Fast-track vanilla
        if (key.getNamespace().equals(NamespacedKey.MINECRAFT)) {
            return VanillaItemsFeature.INSTANCE;
        }

        List<T> features = (List) getFeatures(ItemsFeature.class);
        for (T feature : features) {
            if (!feature.isEnabled()) {
                continue;
            }

            String pluginNamespace = feature.getPlugin().getName().toLowerCase(Locale.ROOT);
            if (key.getNamespace().equals(pluginNamespace)) {
                return feature;
            }
        }

        return VanillaItemsFeature.INSTANCE;
    }
}
