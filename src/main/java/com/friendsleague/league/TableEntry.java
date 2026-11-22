package com.friendsleague.league;

// One line of the league table as the browser sees it. "points" is after any points lost for debt.
public record TableEntry(int position, Long teamId, String teamName, boolean computer, boolean mine,
                         int played, int wins, int draws, int losses, int goalsFor, int goalsAgainst,
                         int goalDifference, int points, int pointsDeducted) {
}
