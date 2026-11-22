package com.friendsleague.match;

// One goal: in which minute, for which side, and who scored.
public record Goal(int minute, Side side, String scorer) {
}