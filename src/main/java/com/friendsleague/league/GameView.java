package com.friendsleague.league;

import com.friendsleague.match.Side;

import java.time.Instant;
import java.util.List;

// A game as the browser sees it at this moment. During the live broadcast only goals that have "happened" are here.
public record GameView(Long id, int round, Instant kickoffAt, Status status, int minute,
                       TeamRef home, TeamRef away, Integer homeScore, Integer awayScore, List<GoalView> goals) {

    public enum Status {
        SCHEDULED,
        LIVE,
        FINISHED
    }

    public record TeamRef(Long id, String name) {
    }

    public record GoalView(int minute, Side side, String scorer) {
    }
}
