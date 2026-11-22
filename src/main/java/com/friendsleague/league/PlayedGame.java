package com.friendsleague.league;

// A finished game, as the table needs it: who played and the score.
public record PlayedGame(int home, int away, int homeGoals, int awayGoals) {
}