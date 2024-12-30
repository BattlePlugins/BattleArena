package org.battleplugins.arena.feature.hologram;

import org.battleplugins.arena.competition.Competition;
import org.battleplugins.arena.event.arena.ArenaRemoveCompetitionEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.ArrayList;

class HologramListener implements Listener {
    private final HologramFeature feature;

    public HologramListener(HologramFeature feature) {
        this.feature = feature;
    }

    @EventHandler
    public void onRemoveCompetition(ArenaRemoveCompetitionEvent event) {
        if (!this.feature.isEnabled()) {
            return;
        }

        this.clearHolograms(event.getCompetition());
    }

    private void clearHolograms(Competition<?> competition) {
        for (Hologram hologram : new ArrayList<>(this.feature.getHolograms())) {
            if (hologram.getCompetition().equals(competition)) {
                this.feature.removeHologram(hologram);
            }
        }
    }
}
