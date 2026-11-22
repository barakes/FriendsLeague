package com.friendsleague.account;

// What the browser may see about an account. Never the password hash.
public record AccountView(String username) {

    public static AccountView from(Account account) {
        return new AccountView(account.getUsername());
    }
}
