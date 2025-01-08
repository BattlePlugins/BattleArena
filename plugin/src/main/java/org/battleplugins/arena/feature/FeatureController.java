package org.battleplugins.arena.feature;

import org.battleplugins.arena.BattleArena;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class FeatureController<T extends FeatureInstance> {
    private static final Map<Class<?>, List<FeatureInstance>> FEATURES = new HashMap<>();

    protected static <T extends FeatureInstance> void registerFeature(Class<T> clazz, T feature) {
        FEATURES.computeIfAbsent(clazz, k -> new ArrayList<>()).add(feature);
    }

    protected static <T extends FeatureInstance> T createInstance(Class<T> clazz) {
        T instance = getBestFeature(clazz);
        if (instance != null) {
            Listener listener = instance.createListener();
            if (listener != null) {
                Bukkit.getPluginManager().registerEvents(listener, BattleArena.getInstance());
            }
        }

        return instance;
    }

    private static <T extends FeatureInstance> T getBestFeature(Class<T> clazz) {
        List<T> features = getFeatures(clazz);
        if (features.isEmpty()) {
            return null;
        }

        for (T feature : features) {
            if (!feature.isEnabled()) {
                continue;
            }

            return feature;
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    protected static <T extends FeatureInstance> List<T> getFeatures(Class<T> clazz) {
        return (List<T>) FEATURES.getOrDefault(clazz, List.of());
    }
}
