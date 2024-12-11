package org.battleplugins.arena.module.party.paf;

import de.simonsator.partyandfriends.spigot.api.pafplayers.PAFPlayer;
import de.simonsator.partyandfriends.spigot.api.party.PartyManager;
import org.battleplugins.arena.feature.party.Party;
import org.battleplugins.arena.feature.party.PartyMember;

import java.util.UUID;

public class PAFPartyMember implements PartyMember {
    private final PAFPlayer impl;

    public PAFPartyMember(PAFPlayer impl) {
        this.impl = impl;
    }

    @Override
    public String getName() {
        return this.impl.getName();
    }

    @Override
    public UUID getUniqueId() {
        return this.impl.getUniqueId();
    }

    @Override
    public Party getParty() {
        return new PAFParty(PartyManager.getInstance().getParty(this.impl));
    }
}
