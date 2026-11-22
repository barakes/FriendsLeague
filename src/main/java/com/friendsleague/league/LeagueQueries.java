package com.friendsleague.league;

import com.friendsleague.team.Team;
import com.friendsleague.team.TeamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// Reading leagues for the browser. readOnly: no changes are saved, so the database can work a little faster.
// Everything is turned into simple records inside the transaction, so nothing "lazy" is touched after it ends.
@Service
@Transactional(readOnly = true)
public class LeagueQueries {

    private final TeamRepository teams;

    public LeagueQueries(TeamRepository teams) {
        this.teams = teams;
    }

    public List<LeagueSummary> myLeagues(Long accountId) {
        return teams.findByAccountIdOrderById(accountId).stream().map(team -> summary(team, accountId)).toList();
    }

    public LeagueSummary summary(Long accountId, Long leagueId) {
        Team myTeam = teams.findByLeagueIdAndAccountId(leagueId, accountId)
                .orElseThrow(() -> new LeagueException("You are not in this league"));
        return summary(myTeam, accountId);
    }

    private LeagueSummary summary(Team myTeam, Long accountId) {
        League league = myTeam.getLeague();
        return new LeagueSummary(league.getId(), league.getName(), league.getJoinCode(), league.getTeamCount(),
                teams.countByLeagueId(league.getId()), league.getStatus(), league.getSeason(), league.getKickoffTimes(),
                league.getOwner().getId().equals(accountId), myTeam.getId(), myTeam.getName());
    }
}
