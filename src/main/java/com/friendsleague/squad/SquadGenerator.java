package com.friendsleague.squad;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

// Creates the 18 "home players" a team starts with.
// Every squad has exactly the same total strength, so nobody starts with an advantage,
// but the spread differs: one team gets a few stars and weak players, another has everyone in the middle.
public class SquadGenerator {

    public static final int SQUAD_SIZE = 18;

    // 2 goalkeepers, 6 defenders, 6 midfielders, 4 forwards: enough to field every formation and have a bench.
    public static final Map<Position, Integer> COMPOSITION = composition();

    static final int LOWEST_RATING = 30;
    static final int HIGHEST_RATING = 90;
    static final int YOUNGEST = 18;
    static final int OLDEST = 33;

    // How far ratings spread around the average: small = everyone similar, large = stars and weak players.
    private static final double MIN_SPREAD = 4;
    private static final double MAX_SPREAD = 14;

    private final Random random;
    private final NameGenerator names;

    public SquadGenerator(Random random, NameGenerator names) {
        this.random = random;
        this.names = names;
    }

    public List<FootballerProfile> generate(int averageRating) {
        if (averageRating < LOWEST_RATING || averageRating > HIGHEST_RATING) {
            throw new IllegalArgumentException("Average rating must be between "
                    + LOWEST_RATING + " and " + HIGHEST_RATING);
        }

        int[] ratings = randomRatings(averageRating);
        balanceTo(ratings, averageRating * SQUAD_SIZE);

        List<FootballerProfile> squad = new ArrayList<>(SQUAD_SIZE);
        int index = 0;
        for (Map.Entry<Position, Integer> entry : COMPOSITION.entrySet()) {
            for (int i = 0; i < entry.getValue(); i++) {
                int age = YOUNGEST + random.nextInt(OLDEST - YOUNGEST + 1);
                squad.add(new FootballerProfile(names.next(), entry.getKey(), ratings[index++], age));
            }
        }
        return squad;
    }

    // Ratings around the average, with a spread that is chosen at random for each team.
    private int[] randomRatings(int averageRating) {
        double spread = MIN_SPREAD + random.nextDouble() * (MAX_SPREAD - MIN_SPREAD);
        int[] ratings = new int[SQUAD_SIZE];
        for (int i = 0; i < SQUAD_SIZE; i++) {
            ratings[i] = clamp((int) Math.round(averageRating + random.nextGaussian() * spread));
        }
        return ratings;
    }

    // Moves ratings up or down by one point at a time, on random players, until the total is exact.
    private void balanceTo(int[] ratings, int targetTotal) {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < ratings.length; i++) {
            order.add(i);
        }

        int total = sum(ratings);
        while (total != targetTotal) {
            int step = total < targetTotal ? 1 : -1;
            Collections.shuffle(order, random);
            for (int i : order) {
                int changed = ratings[i] + step;
                if (changed >= LOWEST_RATING && changed <= HIGHEST_RATING) {
                    ratings[i] = changed;
                    total += step;
                    break;
                }
            }
        }
    }

    private static int clamp(int rating) {
        return Math.max(LOWEST_RATING, Math.min(HIGHEST_RATING, rating));
    }

    private static int sum(int[] values) {
        int total = 0;
        for (int value : values) {
            total += value;
        }
        return total;
    }

    private static Map<Position, Integer> composition() {
        Map<Position, Integer> composition = new EnumMap<>(Position.class);
        composition.put(Position.GOALKEEPER, 2);
        composition.put(Position.DEFENDER, 6);
        composition.put(Position.MIDFIELDER, 6);
        composition.put(Position.FORWARD, 4);
        return Collections.unmodifiableMap(composition);
    }
}