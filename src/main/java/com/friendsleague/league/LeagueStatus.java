package com.friendsleague.league;

public enum LeagueStatus {
    // Friends are joining; the season hasn't started.
    WAITING,
    // Rounds are being played.
    RUNNING,
    // The season is over; the transfer window is open until the next one starts.
    BETWEEN_SEASONS
}
