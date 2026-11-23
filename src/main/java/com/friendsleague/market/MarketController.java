package com.friendsleague.market;

import com.friendsleague.account.Account;
import com.friendsleague.team.TeamView;
import com.friendsleague.web.CurrentAccount;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class MarketController {

    public record FootballerRequest(Long footballerId) {
    }

    private final MarketService marketService;

    public MarketController(MarketService marketService) {
        this.marketService = marketService;
    }

    @GetMapping("/leagues/{leagueId}/market")
    public List<MarketFootballerView> market(@CurrentAccount Account account, @PathVariable Long leagueId) {
        return marketService.market(account.getId(), leagueId);
    }

    @PostMapping("/teams/{teamId}/buy")
    public TeamView buy(@CurrentAccount Account account, @PathVariable Long teamId,
                        @RequestBody FootballerRequest request) {
        return marketService.buy(account.getId(), teamId, request.footballerId());
    }

    @PostMapping("/teams/{teamId}/sell")
    public TeamView sell(@CurrentAccount Account account, @PathVariable Long teamId,
                         @RequestBody FootballerRequest request) {
        return marketService.sell(account.getId(), teamId, request.footballerId());
    }
}
