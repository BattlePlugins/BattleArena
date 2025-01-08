package org.battleplugins.arena.feature.party;

import org.battleplugins.arena.feature.FeatureController;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * API for parties used in BattleArena.
 */
@ApiStatus.Experimental
public final class Parties extends FeatureController<PartiesFeature> {
    private static PartiesFeature instance;

    /**
     * Gets the party of the given player UUID.
     *
     * @param uuid the uuid of the party
     * @return the party of the given player UUID
     */
    @Nullable
    public static Party getParty(UUID uuid) {
        PartiesFeature instance = instance();
        if (instance == null) {
            return null;
        }

        return instance.getParty(uuid);
    }

    /**
     * Gets the instance of the {@link PartiesFeature}.
     *
     * @return the instance of the {@link PartiesFeature}
     */
    public static PartiesFeature instance() {
        if (instance == null) {
            instance = createInstance(PartiesFeature.class);
        }

        return instance;
    }

    /**
     * Registers a {@link PartiesFeature} to the feature controller.
     *
     * @param feature the feature to register
     */
    public static void register(PartiesFeature feature) {
        registerFeature(PartiesFeature.class, feature);
    }
}
