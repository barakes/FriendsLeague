package com.friendsleague.season;

import com.friendsleague.market.SquadLimits;
import com.friendsleague.squad.FootballerProfile;
import com.friendsleague.squad.Position;
import com.friendsleague.squad.SquadGenerator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

// What happens to footballers between two seasons: they get a year older, their rating changes with their age,
// the oldest retire, and a squad that got too small gets young players from the academy.
public final class SeasonRules {

    public static final int RETIREMENT_AGE = 36;

    public static final int YOUTH_AGE = 18;
    public static final int YOUTH_LOWEST = 45;
    public static final int YOUTH_HIGHEST = 55;

    private SeasonRules() {
    }

    // The same footballer one season later. The change depends on the age he had during the season that ended.
    public static FootballerProfile olderBySeason(FootballerProfile footballer, Random random) {
        int rating = footballer.rating() + ratingChange(footballer.age(), random);
        rating = Math.clamp(rating, FootballerProfile.MIN_RATING, FootballerProfile.MAX_RATING);
        return new FootballerProfile(footballer.name(), footballer.position(), rating, footballer.age() + 1);
    }

    // Up to 23: +1 to +4. 24-29: -1 to +2. 30-32: -3 to 0. 33 and older: -4 to -1.
    static int ratingChange(int age, Random random) {
        if (age <= 23) {
            return between(1, 4, random);
        }
        if (age <= 29) {
            return between(-1, 2, random);
        }
        if (age <= 32) {
            return between(-3, 0, random);
        }
        return between(-4, -1, random);
    }

    public static boolean retires(FootballerProfile footballer) {
        return footballer.age() >= RETIREMENT_AGE;
    }

    // The positions of the academy players a squad needs: a goalkeeper if it has none,
    // then players until it has the minimum squad size, each time in the position that is shortest
    // compared with a normal squad (2 goalkeepers, 6 defenders, 6 midfielders, 4 forwards).
    public static List<Position> youthNeeded(Collection<Position> squad) {
        Map<Position, Integer> counts = new EnumMap<>(Position.class);
        for (Position position : Position.values()) {
            counts.put(position, 0);
        }
        squad.forEach(position -> counts.merge(position, 1, Integer::sum));

        List<Position> needed = new ArrayList<>();
        if (counts.get(Position.GOALKEEPER) < SquadLimits.MIN_GOALKEEPERS) {
            needed.add(Position.GOALKEEPER);
            counts.merge(Position.GOALKEEPER, 1, Integer::sum);
        }
        while (squad.size() + needed.size() < SquadLimits.MIN_SQUAD) {
            Position shortest = Position.GOALKEEPER;
            double lowest = Double.MAX_VALUE;
            for (Position position : Position.values()) {
                double share = (double) counts.get(position) / SquadGenerator.COMPOSITION.get(position);
                if (share < lowest) {
                    lowest = share;
                    shortest = position;
                }
            }
            needed.add(shortest);
            counts.merge(shortest, 1, Integer::sum);
        }
        return needed;
    }

    public static FootballerProfile youthPlayer(String name, Position position, Random random) {
        return new FootballerProfile(name, position, between(YOUTH_LOWEST, YOUTH_HIGHEST, random), YOUTH_AGE);
    }

    private static int between(int lowest, int highest, Random random) {
        return lowest + random.nextInt(highest - lowest + 1);
    }
}
