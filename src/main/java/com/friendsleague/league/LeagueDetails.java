package com.friendsleague.league;

import java.util.List;

// Everything the league page shows at once: the league, which round is current, and the table.
public record LeagueDetails(LeagueSummary league, int currentRound, int totalRounds, List<TableEntry> table) {
}
