package com.friendsleague.league;

import com.friendsleague.account.Account;
import com.friendsleague.web.CurrentAccount;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Creating, joining and starting leagues. Every address here needs a logged-in player.
@RestController
@RequestMapping("/api/leagues")
public class LeagueController {

    public record CreateLeagueRequest(String name, Integer size, String kickoffTimes, String teamName) {
    }

    public record JoinLeagueRequest(String code, String teamName) {
    }

    private final LeagueService leagueService;
    private final LeagueQueries queries;

    public LeagueController(LeagueService leagueService, LeagueQueries queries) {
        this.leagueService = leagueService;
        this.queries = queries;
    }

    @GetMapping
    public List<LeagueSummary> myLeagues(@CurrentAccount Account account) {
        return queries.myLeagues(account.getId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LeagueSummary create(@CurrentAccount Account account, @RequestBody CreateLeagueRequest request) {
        int size = request.size() == null ? 0 : request.size();
        League league = leagueService.createLeague(account.getId(), request.name(), size,
                request.kickoffTimes(), request.teamName());
        return queries.summary(account.getId(), league.getId());
    }

    @PostMapping("/join")
    public LeagueSummary join(@CurrentAccount Account account, @RequestBody JoinLeagueRequest request) {
        Long leagueId = leagueService.joinLeague(account.getId(), request.code(), request.teamName()).getLeague().getId();
        return queries.summary(account.getId(), leagueId);
    }

    @PostMapping("/{leagueId}/start")
    public LeagueSummary start(@CurrentAccount Account account, @PathVariable Long leagueId) {
        leagueService.startSeason(account.getId(), leagueId);
        return queries.summary(account.getId(), leagueId);
    }
}
