package com.friendsleague.league;

// One game in the schedule: in which round, and who plays at home against whom.
// Teams are numbered 0 to n-1; the league decides which real team each number stands for.
public record Fixture(int round, int home, int away) {
}