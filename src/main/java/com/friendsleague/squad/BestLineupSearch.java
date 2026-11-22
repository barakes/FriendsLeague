package com.friendsleague.squad;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Finds the strongest way to fill the places of a formation from a squad.
// For each player in turn: either leave him on the bench, or put him in a line that still has a free place.
// The best total for "from player i on, with these places still free" is remembered, so it is worked out only once.
final class BestLineupSearch {

    private static final double IMPOSSIBLE = -1e9;
    private static final Position[] LINES = Position.values();

    private final List<FootballerProfile> squad;
    private final Map<Long, Double> memory = new HashMap<>();

    BestLineupSearch(List<FootballerProfile> squad) {
        this.squad = List.copyOf(squad);
    }

    // "places" = how many free places each line has (index = Position.ordinal()).
    Map<Position, List<FootballerProfile>> choose(int[] places) {
        Map<Position, List<FootballerProfile>> chosen = new EnumMap<>(Position.class);
        for (Position line : LINES) {
            chosen.put(line, new ArrayList<>());
        }

        int[] free = places.clone();
        for (int i = 0; i < squad.size(); i++) {
            // Follow the decision that gave the best total.
            Position decision = null;
            double bestTotal = bestFrom(i + 1, free);
            for (Position line : LINES) {
                if (free[line.ordinal()] > 0) {
                    free[line.ordinal()]--;
                    double total = Lineup.effectiveRating(squad.get(i), line) + bestFrom(i + 1, free);
                    free[line.ordinal()]++;
                    if (total > bestTotal + 1e-9) {
                        bestTotal = total;
                        decision = line;
                    }
                }
            }
            if (decision != null) {
                chosen.get(decision).add(squad.get(i));
                free[decision.ordinal()]--;
            }
        }
        return chosen;
    }

    private double bestFrom(int index, int[] free) {
        int remaining = 0;
        for (int count : free) {
            remaining += count;
        }
        if (remaining == 0) {
            return 0;
        }
        if (squad.size() - index < remaining) {
            return IMPOSSIBLE;
        }

        long key = key(index, free);
        Double known = memory.get(key);
        if (known != null) {
            return known;
        }

        // Option 1: this player stays on the bench.
        double best = bestFrom(index + 1, free);
        // Option 2: he takes a free place in one of the lines.
        for (Position line : LINES) {
            if (free[line.ordinal()] > 0) {
                free[line.ordinal()]--;
                best = Math.max(best, Lineup.effectiveRating(squad.get(index), line) + bestFrom(index + 1, free));
                free[line.ordinal()]++;
            }
        }

        memory.put(key, best);
        return best;
    }

    // Packs the player index and the free places of the four lines into one number (each fits in 8 bits).
    private static long key(int index, int[] free) {
        long key = index;
        for (int count : free) {
            key = key * 256 + count;
        }
        return key;
    }
}