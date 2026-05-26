package org.battleplugins.arena.module.tournaments.algorithm;

import org.battleplugins.arena.module.tournaments.Contestant;
import org.battleplugins.arena.module.tournaments.ContestantPair;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class SingleEliminationTournamentCalculatorTest {

    private final SingleEliminationTournamentCalculator calculator = new SingleEliminationTournamentCalculator();

    @Test
    void advanceRoundReturnsCompleteWhenContestantsAreZeroOrOne() {
        TournamentCalculator.MatchResult noContestants = this.calculator.advanceRound(List.of());
        assertTrue(noContestants.complete());
        assertTrue(noContestants.contestantPairs().isEmpty());

        TournamentCalculator.MatchResult oneContestant = this.calculator.advanceRound(List.of(createContestant(1, 0)));
        assertTrue(oneContestant.complete());
        assertTrue(oneContestant.contestantPairs().isEmpty());
    }

    @Test
    void advanceRoundPairsByByesThenTeamSizeAndAssignsByeToLast() {
        Contestant c1 = createContestant(1, 2);
        Contestant c2 = createContestant(3, 1);
        Contestant c3 = createContestant(1, 1);
        Contestant c4 = createContestant(4, 0);
        Contestant c5 = createContestant(2, 0);

        TournamentCalculator.MatchResult result = this.calculator.advanceRound(List.of(c1, c2, c3, c4, c5));

        assertFalse(result.complete());
        assertEquals(3, result.contestantPairs().size());

        ContestantPair pair1 = result.contestantPairs().get(0);
        ContestantPair pair2 = result.contestantPairs().get(1);
        ContestantPair pair3 = result.contestantPairs().get(2);

        assertEquals(c1, pair1.contestant1());
        assertEquals(c2, pair1.contestant2());

        assertEquals(c3, pair2.contestant1());
        assertEquals(c4, pair2.contestant2());

        assertEquals(c5, pair3.contestant1());
        assertTrue(pair3.autoAdvance());
    }

    @Test
    void advanceRoundDoesNotMutateInputOrder() {
        Contestant c1 = createContestant(1, 0);
        Contestant c2 = createContestant(2, 3);
        Contestant c3 = createContestant(3, 1);

        List<Contestant> input = new ArrayList<>(List.of(c1, c2, c3));
        List<Contestant> expectedOrder = List.copyOf(input);

        this.calculator.advanceRound(input);

        assertEquals(expectedOrder, input);
    }

    @Test
    void advanceRoundGivesByeToContestantWithFewestExistingByes() {
        Contestant highByes = createContestant(2, 4);
        Contestant mediumByes = createContestant(2, 2);
        Contestant lowByes = createContestant(2, 0);

        TournamentCalculator.MatchResult result = this.calculator.advanceRound(List.of(highByes, mediumByes, lowByes));

        assertEquals(2, result.contestantPairs().size());
        ContestantPair autoAdvancePair = result.contestantPairs().stream().filter(ContestantPair::autoAdvance).findFirst().orElseThrow();
        assertEquals(lowByes, autoAdvancePair.contestant1());
    }

    private static Contestant createContestant(int players, int byes) {
        Set<Player> members = createPlaceholderPlayers(players);

        Contestant contestant = new Contestant(members);
        for (int i = 0; i < byes; i++) {
            contestant.addBye();
        }

        return contestant;
    }

    private static Set<Player> createPlaceholderPlayers(int count) {
        Set<Player> rawPlayers = new HashSet<>();
        for (int i = 0; i < count; i++) {
            rawPlayers.add(mock(Player.class));
        }
        return rawPlayers;
    }
}
