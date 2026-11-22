package com.friendsleague.league;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// Builds the league table from the games played so far.
// Order: points, then goal difference, then goals scored. If everything is equal, the lower team number comes first,
// so the order never changes randomly between two pages loads.
public final class LeagueTable {

    private static final Comparator<TableRow> ORDER = Comparator.comparingInt(TableRow::points).reversed()
            .thenComparing(Comparator.comparingInt(TableRow::goalDifference).reversed())
            .thenComparing(Comparator.comparingInt(TableRow::goalsFor).reversed())
            .thenComparingInt(TableRow::team);

    private LeagueTable() {
    }

    // "pointsDeducted" is indexed by team number (a team in debt for too long loses points).
    public static List<TableRow> build(int teams, List<PlayedGame> games, int[] pointsDeducted) {
        if (pointsDeducted.length != teams) {
            throw new IllegalArgumentException("Need a points deduction entry for every team");
        }

        int[] played = new int[teams];
        int[] wins = new int[teams];
        int[] draws = new int[teams];
        int[] losses = new int[teams];
        int[] goalsFor = new int[teams];
        int[] goalsAgainst = new int[teams];

        for (PlayedGame game : games) {
            played[game.home()]++;
            played[game.away()]++;
            goalsFor[game.home()] += game.homeGoals();
            goalsAgainst[game.home()] += game.awayGoals();
            goalsFor[game.away()] += game.awayGoals();
            goalsAgainst[game.away()] += game.homeGoals();

            if (game.homeGoals() > game.awayGoals()) {
                wins[game.home()]++;
                losses[game.away()]++;
            } else if (game.homeGoals() < game.awayGoals()) {
                wins[game.away()]++;
                losses[game.home()]++;
            } else {
                draws[game.home()]++;
                draws[game.away()]++;
            }
        }

        List<TableRow> table = new ArrayList<>(teams);
        for (int team = 0; team < teams; team++) {
            table.add(new TableRow(team, played[team], wins[team], draws[team], losses[team],
                    goalsFor[team], goalsAgainst[team], pointsDeducted[team]));
        }
        table.sort(ORDER);
        return table;
    }

    public static List<TableRow> build(int teams, List<PlayedGame> games) {
        return build(teams, games, new int[teams]);
    }
}