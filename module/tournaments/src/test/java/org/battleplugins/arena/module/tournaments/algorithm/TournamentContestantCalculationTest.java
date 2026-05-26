package org.battleplugins.arena.module.tournaments.algorithm;

import org.battleplugins.arena.module.tournaments.Contestant;
import org.battleplugins.arena.module.tournaments.Tournament;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class TournamentContestantCalculationTest {

    @Test
    void calculateContestantsBalancesPlayerCountsForNonPowerOfTwoContestants() throws Exception {
        Set<Player> players = createPlaceholderPlayers(10);

        List<Contestant> contestants = Tournament.calculateContestants(players, 3, 2);

        assertEquals(3, contestants.size());
        assertContestantSizes(contestants, List.of(4, 3, 3));
        assertAllPlayersAreAssignedExactlyOnce(players, contestants);
    }

    @Test
    void calculateContestantsKeepsNaturalDistributionForPowerOfTwoContestants() throws Exception {
        Set<Player> players = createPlaceholderPlayers(5);

        List<Contestant> contestants = Tournament.calculateContestants(players, 2, 2);

        assertEquals(2, contestants.size());
        assertContestantSizes(contestants, List.of(3, 2));
        assertAllPlayersAreAssignedExactlyOnce(players, contestants);
    }

    @Test
    void calculateContestantsHandlesVeryHighRequiredContestantsSafely() throws Exception {
        Set<Player> players = createPlaceholderPlayers(3);

        List<Contestant> contestants = Tournament.calculateContestants(players, 5, 8);

        assertEquals(3, contestants.size());
        assertContestantSizes(contestants, List.of(1, 1, 1));
        assertAllPlayersAreAssignedExactlyOnce(players, contestants);
    }

    @Test
    void calculateContestantsCreatesAdditionalContestantWhenRemainderMeetsRoundRequirement() throws Exception {
        Set<Player> players = createPlaceholderPlayers(5);

        List<Contestant> contestants = Tournament.calculateContestants(players, 2, 1);

        assertEquals(3, contestants.size());
        assertContestantSizes(contestants, List.of(2, 2, 1));
        assertAllPlayersAreAssignedExactlyOnce(players, contestants);
    }

    @Test
    void calculateContestantsTreatsNonPositiveRequiredContestantsAsOne() {
        Set<Player> players = createPlaceholderPlayers(7);

        List<Contestant> contestants = Tournament.calculateContestants(players, 3, 0);

        assertEquals(3, contestants.size());
        assertContestantSizes(contestants, List.of(3, 2, 2));
        assertAllPlayersAreAssignedExactlyOnce(players, contestants);
    }

    @Test
    void calculateContestantsWithMaxContestantSizeOneCreatesOneContestantPerPlayer() {
        Set<Player> players = createPlaceholderPlayers(6);

        List<Contestant> contestants = Tournament.calculateContestants(players, 1, 2);

        assertEquals(6, contestants.size());
        assertContestantSizes(contestants, List.of(1, 1, 1, 1, 1, 1));
        assertAllPlayersAreAssignedExactlyOnce(players, contestants);
    }

    @Test
    void calculateContestantsWithLargeMaxContestantSizeStillTargetsRequiredContestants() {
        Set<Player> players = createPlaceholderPlayers(6);

        List<Contestant> contestants = Tournament.calculateContestants(players, 100, 2);

        assertEquals(2, contestants.size());
        assertContestantSizes(contestants, List.of(3, 3));
        assertAllPlayersAreAssignedExactlyOnce(players, contestants);
    }

    @Test
    void calculateContestantsReturnsEmptyListForEmptyInput() {
        List<Contestant> contestants = Tournament.calculateContestants(Set.of(), 4, 2);

        assertTrue(contestants.isEmpty());
    }

    private static void assertAllPlayersAreAssignedExactlyOnce(Set<Player> expectedPlayers, List<Contestant> contestants) {
        Set<Player> expectedByIdentity = Collections.newSetFromMap(new IdentityHashMap<>());
        expectedByIdentity.addAll(expectedPlayers);

        Set<Player> assignedByIdentity = Collections.newSetFromMap(new IdentityHashMap<>());
        int totalAssigned = 0;
        for (Contestant contestant : contestants) {
            Set<Player> members = contestant.getPlayers();
            totalAssigned += members.size();
            assignedByIdentity.addAll(members);
        }

        assertEquals(expectedByIdentity.size(), totalAssigned);
        assertEquals(expectedByIdentity.size(), assignedByIdentity.size());
        assertTrue(assignedByIdentity.containsAll(expectedByIdentity));
    }

    private static Set<Player> createPlaceholderPlayers(int count) {
        Set<Player> rawPlayers = new HashSet<>();
        for (int i = 0; i < count; i++) {
            rawPlayers.add(mock(Player.class));
        }
        return rawPlayers;
    }

    private static void assertContestantSizes(List<Contestant> contestants, List<Integer> expectedSizes) {
        List<Integer> actualSizes = contestants.stream().map(c -> c.getPlayers().size()).sorted((a, b) -> b - a).toList();
        List<Integer> sortedExpected = expectedSizes.stream().sorted((a, b) -> b - a).toList();

        assertEquals(sortedExpected, actualSizes);

        int minSize = actualSizes.get(actualSizes.size() - 1);
        int maxSize = actualSizes.get(0);
        assertTrue(maxSize - minSize <= 1 || actualSizes.size() == 2);
    }
}
