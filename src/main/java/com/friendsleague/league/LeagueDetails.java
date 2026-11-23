package com.friendsleague.league;

import java.util.List;

// Everything the league page shows at once: the league, which round is current, the table,
// and the champions of the seasons that ended (the latest first).
public record LeagueDetails(LeagueSummary league, int currentRound, int totalRounds, List<TableEntry> table,
                            List<ChampionView> champions) {
}
