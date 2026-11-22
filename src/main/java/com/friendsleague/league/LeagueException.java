package com.friendsleague.league;

// An expected problem with a league (a wrong code, a full league...). The message is written for the player.
public class LeagueException extends RuntimeException {

    public LeagueException(String message) {
        super(message);
    }
}