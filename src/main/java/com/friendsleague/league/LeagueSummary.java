package com.friendsleague.league;

// What the browser sees about a league in the list of "my leagues".
public record LeagueSummary(Long id, String name, String joinCode, int size, long teams, LeagueStatus status,
                            int season, String kickoffTimes, boolean owner, Long myTeamId, String myTeamName) {
}
