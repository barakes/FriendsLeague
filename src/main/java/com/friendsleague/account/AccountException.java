package com.friendsleague.account;

// An expected problem with registering or logging in. The message is written for the player.
public class AccountException extends RuntimeException {

    public AccountException(String message) {
        super(message);
    }
}