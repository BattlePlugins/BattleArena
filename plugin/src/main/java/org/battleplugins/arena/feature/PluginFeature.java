package org.battleplugins.arena.feature;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Represents a {@link FeatureInstance} implemented by a plugin.
 *
 * @param <T> the feature instance
 */
public abstract class PluginFeature<T extends FeatureInstance> implements FeatureInstance {
    private final Plugin plugin;

    public PluginFeature(String pluginName) {
        this.plugin = Bukkit.getPluginManager().getPlugin(pluginName);
        if (this.plugin == null) {
            throw new IllegalArgumentException("Plugin " + pluginName + " does not exist!");
        }
    }

    public PluginFeature(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean isEnabled() {
        return this.plugin.isEnabled();
    }

    /**
     * Gets the plugin that this feature is implemented by.
     *
     * @return the plugin that this feature is implemented by
     */
    public Plugin getPlugin() {
        return this.plugin;
    }
}
