package org.battleplugins.arena.module.tournaments.algorithm;

import org.battleplugins.arena.module.tournaments.Contestant;
import org.battleplugins.arena.module.tournaments.ContestantPair;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class SingleEliminationTournamentCalculator implements TournamentCalculator {

    @Override
    public MatchResult advanceRound(List<Contestant> contestants) {
        if (contestants.size() <= 1) {
            return new MatchResult(true, List.of());
        }

        // Copy contestants to avoid modifying the original list
        contestants = new ArrayList<>(contestants);

        List<ContestantPair> pairs = new ArrayList<>();

        // Sort contestants based on size of players (if there are fewer players, make them
        // more likely to receive a bye as they are at a disadvantage already)
        // Sort contestants based on number of byes received
        //fixed so both are used, and not one overriding the other.
        contestants.sort(
            Comparator
                .comparingInt((Contestant c) -> c.getPlayers().size()).reversed()
                .thenComparingInt((Contestant c) -> c.getByes()).reversed()
        );

        // Determine how many matches we need to create
        int matches = (int) Math.ceil(contestants.size() / 2.0);

        for (int i = 0; i < matches; i++) {
            Contestant contestant1 = contestants.get(i * 2);
            Contestant contestant2 = (i * 2 + 1 < contestants.size()) ? contestants.get(i * 2 + 1) : null;

            if (contestant2 == null) {
                // Contestant 1 receives a bye
                pairs.add(new ContestantPair(contestant1, null));
            } else {
                // Contestant 1 and 2 are paired together
                pairs.add(new ContestantPair(contestant1, contestant2));
            }
        }
        
        return new MatchResult(false, pairs);
    }
}
