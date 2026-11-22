package com.friendsleague.match;

import java.util.List;

// The whole match, worked out at kick-off. The goals are in time order, so the "live" view can reveal them one by one.
public record MatchResult(int homeGoals, int awayGoals, List<Goal> goals) {

    public MatchResult {
        goals = List.copyOf(goals);
    }

    public Side winner() {
        if (homeGoals == awayGoals) {
            return null;
        }
        return homeGoals > awayGoals ? Side.HOME : Side.AWAY;
    }
}