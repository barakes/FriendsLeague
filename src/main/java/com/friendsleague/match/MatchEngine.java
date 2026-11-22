package com.friendsleague.match;

import com.friendsleague.squad.FootballerProfile;
import com.friendsleague.squad.Lineup;
import com.friendsleague.squad.Position;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// Simulates a whole match at once.
// 1. Each side gets an attack and a defense number from its lines and its tactic.
// 2. The expected number of goals for a side grows with its attack against the other side's defense.
// 3. Every one of the 90 minutes, each side may score, with a small chance based on that expectation.
//    So the stronger side usually wins, but not always.
public class MatchEngine {

    public static final int MINUTES = 90;

    // An average side scores about this many goals against an equal side.
    static final double AVERAGE_GOALS = 1.35;
    // How much the strength gap matters. Higher = fewer surprises.
    static final double STRENGTH_EFFECT = 2.2;
    static final double HOME_ADVANTAGE = 1.10;

    private static final double ATTACK_WEIGHTS_442 = 2 * 1.0 + 4 * 0.55 + 4 * 0.3;
    private static final double DEFENSE_WEIGHTS_442 = 1 * 1.0 + 4 * 0.7 + 4 * 0.45;

    // Attack and defense add up their lines with different weights, so they are on slightly different scales.
    // For two equal sides in a 4-4-2, attack / defense = 5.4 / 5.6; this factor brings that ratio to exactly 1.
    static final double SCALE = DEFENSE_WEIGHTS_442 / ATTACK_WEIGHTS_442;

    private final Random random;

    public MatchEngine(Random random) {
        this.random = random;
    }

    public MatchResult play(TeamSheet home, TeamSheet away) {
        double homeExpected = expectedGoals(home, away) * HOME_ADVANTAGE;
        double awayExpected = expectedGoals(away, home) / HOME_ADVANTAGE;

        List<Goal> goals = new ArrayList<>();
        int homeGoals = 0;
        int awayGoals = 0;
        for (int minute = 1; minute <= MINUTES; minute++) {
            if (random.nextDouble() < homeExpected / MINUTES) {
                goals.add(new Goal(minute, Side.HOME, scorer(home.lineup())));
                homeGoals++;
            }
            if (random.nextDouble() < awayExpected / MINUTES) {
                goals.add(new Goal(minute, Side.AWAY, scorer(away.lineup())));
                awayGoals++;
            }
        }
        return new MatchResult(homeGoals, awayGoals, goals);
    }

    static double expectedGoals(TeamSheet attacking, TeamSheet defending) {
        double ratio = attack(attacking) / defense(defending) * SCALE;
        return AVERAGE_GOALS * Math.pow(ratio, STRENGTH_EFFECT);
    }

    // How much each line adds to attack and to defense. A forward adds mostly to attack, a defender mostly to defense,
    // a midfielder to both. Attack and defense of a 4-4-2 add up to almost the same total (5.4 and 5.6), and moving
    // a player to another line takes from one exactly what it gives to the other. So no formation is better
    // by itself: it only moves strength between attack and defense.
    static double attack(TeamSheet team) {
        Lineup lineup = team.lineup();
        double strength = lineup.lineStrength(Position.FORWARD) * 1.0
                + lineup.lineStrength(Position.MIDFIELDER) * 0.55
                + lineup.lineStrength(Position.DEFENDER) * 0.3;
        return strength * team.tactic().attack();
    }

    static double defense(TeamSheet team) {
        Lineup lineup = team.lineup();
        double strength = lineup.lineStrength(Position.GOALKEEPER) * 1.0
                + lineup.lineStrength(Position.DEFENDER) * 0.7
                + lineup.lineStrength(Position.MIDFIELDER) * 0.45;
        return strength * team.tactic().defense();
    }

    // Forwards score most often, defenders sometimes, goalkeepers almost never. Better players score more.
    private String scorer(Lineup lineup) {
        List<FootballerProfile> players = lineup.allPlayers();
        double[] weights = new double[players.size()];
        double total = 0;
        for (int i = 0; i < players.size(); i++) {
            FootballerProfile player = players.get(i);
            double positionWeight = switch (positionOn(lineup, player)) {
                case FORWARD -> 6;
                case MIDFIELDER -> 2.5;
                case DEFENDER -> 0.8;
                case GOALKEEPER -> 0.02;
            };
            weights[i] = positionWeight * player.rating();
            total += weights[i];
        }

        double pick = random.nextDouble() * total;
        for (int i = 0; i < players.size(); i++) {
            pick -= weights[i];
            if (pick <= 0) {
                return players.get(i).name();
            }
        }
        return players.getLast().name();
    }

    private static Position positionOn(Lineup lineup, FootballerProfile player) {
        for (Position line : Position.values()) {
            if (lineup.playersIn(line).contains(player)) {
                return line;
            }
        }
        throw new IllegalStateException(player.name() + " is not in the lineup");
    }
}