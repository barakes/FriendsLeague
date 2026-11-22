package com.friendsleague.web;

// The request needs a logged-in player, and there is none (no token, or an expired one).
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException() {
        super("Please log in");
    }
}
