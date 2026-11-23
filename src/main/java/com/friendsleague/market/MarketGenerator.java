package com.friendsleague.market;

import com.friendsleague.squad.FootballerProfile;
import com.friendsleague.squad.NameGenerator;
import com.friendsleague.squad.Position;
import com.friendsleague.squad.SquadGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// Makes up the free agents on a league's transfer market.
// Most are ordinary footballers, some are good, and a few are stars that a team has to save up for.
public class MarketGenerator {

    // Free agents per team in the league: a 6-team league starts with 18 on the market, a 12-team league with 36.
    public static final int PER_TEAM = 3;

    // Rating groups: 70% ordinary (45-69), 25% good (70-82), 5% stars (83-90).
    static final int ORDINARY_LOWEST = 45;
    static final int GOOD_LOWEST = 70;
    static final int STAR_LOWEST = 83;
    static final int STAR_HIGHEST = 90;
    static final double GOOD_SHARE = 0.25;
    static final double STAR_SHARE = 0.05;

    static final int YOUNGEST = 18;
    static final int OLDEST = 33;

    private final Random random;
    private final NameGenerator names;

    public MarketGenerator(Random random, NameGenerator names) {
        this.random = random;
        this.names = names;
    }

    public List<FootballerProfile> generate(int teams) {
        int count = teams * PER_TEAM;
        List<FootballerProfile> market = new ArrayList<>(count);
        // The first four cover every position, so the market always has a goalkeeper to buy.
        for (Position position : Position.values()) {
            market.add(newFootballer(position));
        }
        while (market.size() < count) {
            market.add(newFootballer(randomPosition()));
        }
        return market;
    }

    private FootballerProfile newFootballer(Position position) {
        int age = YOUNGEST + random.nextInt(OLDEST - YOUNGEST + 1);
        return new FootballerProfile(names.next(), position, randomRating(), age);
    }

    private int randomRating() {
        double group = random.nextDouble();
        if (group < STAR_SHARE) {
            return between(STAR_LOWEST, STAR_HIGHEST);
        }
        if (group < STAR_SHARE + GOOD_SHARE) {
            return between(GOOD_LOWEST, STAR_LOWEST - 1);
        }
        return between(ORDINARY_LOWEST, GOOD_LOWEST - 1);
    }

    // The same mix of positions as a squad: 2 goalkeepers, 6 defenders, 6 midfielders and 4 forwards out of 18.
    private Position randomPosition() {
        int pick = random.nextInt(SquadGenerator.SQUAD_SIZE);
        for (Position position : Position.values()) {
            pick -= SquadGenerator.COMPOSITION.get(position);
            if (pick < 0) {
                return position;
            }
        }
        throw new IllegalStateException("The squad composition does not add up to " + SquadGenerator.SQUAD_SIZE);
    }

    private int between(int lowest, int highest) {
        return lowest + random.nextInt(highest - lowest + 1);
    }
}
