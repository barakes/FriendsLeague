package com.friendsleague.squad;

// A lineup that breaks the rules. The message is written for the manager.
public class LineupException extends RuntimeException {

    public LineupException(String message) {
        super(message);
    }
}