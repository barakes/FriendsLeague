package com.friendsleague.league;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// Builds a double round-robin: every team plays every other team twice, once at home and once away.
// The "circle method": team 0 stays in place and the others rotate one step each round,
// like people around a table, so every pair meets exactly once in the first half of the season.
// The second half repeats the first with home and away swapped.
public final class ScheduleGenerator {

    // Stands for "no opponent this round" when the number of teams is odd.
    private static final int REST = -1;

    private ScheduleGenerator() {
    }

    public static List<Fixture> doubleRoundRobin(int teams) {
        if (teams < 2) {
            throw new IllegalArgumentException("A league needs at least 2 teams");
        }

        List<Integer> circle = new ArrayList<>();
        for (int team = 0; team < teams; team++) {
            circle.add(team);
        }
        if (teams % 2 == 1) {
            // With an odd number, add a "rest" place: whoever meets it doesn't play that round.
            circle.add(REST);
        }

        int size = circle.size();
        int roundsPerHalf = size - 1;
        List<Fixture> fixtures = new ArrayList<>();

        for (int round = 0; round < roundsPerHalf; round++) {
            for (int i = 0; i < size / 2; i++) {
                int first = circle.get(i);
                int second = circle.get(size - 1 - i);
                if (first == REST || second == REST) {
                    continue;
                }
                // Home and away swap every round, so a team rarely plays at the same venue many rounds in a row
                // (the first pair goes the opposite way, because team 0 never moves around the circle).
                boolean firstAtHome = (round % 2 == 0) == (i == 0);
                int home = firstAtHome ? first : second;
                int away = firstAtHome ? second : first;
                fixtures.add(new Fixture(round + 1, home, away));
                fixtures.add(new Fixture(round + 1 + roundsPerHalf, away, home));
            }
            // Rotate everyone except the first place one step.
            circle.add(1, circle.removeLast());
        }

        fixtures.sort(Comparator.comparingInt(Fixture::round).thenComparingInt(Fixture::home));
        return fixtures;
    }

    public static int rounds(int teams) {
        int size = teams % 2 == 0 ? teams : teams + 1;
        return 2 * (size - 1);
    }
}