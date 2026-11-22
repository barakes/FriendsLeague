package com.friendsleague.money;

// One line in a team's account. Income is positive, spending is negative.
public record MoneyEntry(MoneyKind kind, long amount) {
}
