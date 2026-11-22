package com.friendsleague.league;

// One line of the league table.
public record TableRow(int team, int played, int wins, int draws, int losses, int goalsFor, int goalsAgainst,
                       int pointsDeducted) {

    public static final int POINTS_FOR_WIN = 3;
    public static final int POINTS_FOR_DRAW = 1;

    public int points() {
        return wins * POINTS_FOR_WIN + draws * POINTS_FOR_DRAW - pointsDeducted;
    }

    public int goalDifference() {
        return goalsFor - goalsAgainst;
    }
}