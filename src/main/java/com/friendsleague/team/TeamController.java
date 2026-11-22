package com.friendsleague.team;

import com.friendsleague.account.Account;
import com.friendsleague.match.Tactic;
import com.friendsleague.squad.Formation;
import com.friendsleague.web.CurrentAccount;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/teams")
public class TeamController {

    public record LineupRequest(Formation formation, Tactic tactic, List<TeamService.SlotRequest> slots) {
    }

    public record BestLineupRequest(Formation formation) {
    }

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @GetMapping("/{teamId}")
    public TeamView view(@CurrentAccount Account account, @PathVariable Long teamId) {
        return teamService.view(account.getId(), teamId);
    }

    @PutMapping("/{teamId}/lineup")
    public TeamView saveLineup(@CurrentAccount Account account, @PathVariable Long teamId,
                               @RequestBody LineupRequest request) {
        return teamService.saveLineup(account.getId(), teamId, request.formation(), request.tactic(), request.slots());
    }

    @PostMapping("/{teamId}/lineup/best")
    public TeamView pickBestLineup(@CurrentAccount Account account, @PathVariable Long teamId,
                                   @RequestBody BestLineupRequest request) {
        return teamService.pickBestLineup(account.getId(), teamId, request.formation());
    }
}
