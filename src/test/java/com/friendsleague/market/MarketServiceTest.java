package com.friendsleague.market;

import com.friendsleague.IntegrationTest;
import com.friendsleague.account.Account;
import com.friendsleague.account.AccountRepository;
import com.friendsleague.league.League;
import com.friendsleague.league.LeagueException;
import com.friendsleague.league.LeagueService;
import com.friendsleague.money.MoneyKind;
import com.friendsleague.money.Prices;
import com.friendsleague.squad.Position;
import com.friendsleague.team.MoneyRecord;
import com.friendsleague.team.MoneyRecordRepository;
import com.friendsleague.team.TeamRepository;
import com.friendsleague.team.TeamService;
import com.friendsleague.team.TeamView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarketServiceTest extends IntegrationTest {

    @Autowired
    private MarketService market;

    @Autowired
    private LeagueService leagueService;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private TeamRepository teams;

    @Autowired
    private TeamService teamService;

    @Autowired
    private MoneyRecordRepository moneyRecords;

    @Autowired
    private TransactionTemplate transaction;

    private Account dana;
    private Account tom;
    private League league;
    private Long danaTeam;
    private Long tomTeam;

    @BeforeEach
    void startALeague() {
        dana = accounts.save(new Account("Dana", "not-a-real-hash"));
        tom = accounts.save(new Account("Tom", "not-a-real-hash"));
        league = leagueService.createLeague(dana.getId(), "Test league", 6, "21:00", "הפועל דנה");
        tomTeam = leagueService.joinLeague(tom.getId(), league.getJoinCode(), "מכבי תום").getId();
        danaTeam = teams.findByLeagueIdAndAccountId(league.getId(), dana.getId()).orElseThrow().getId();
    }

    private void start() {
        leagueService.startSeason(dana.getId(), league.getId());
    }

    private MarketFootballerView cheapest() {
        return market.market(dana.getId(), league.getId()).stream()
                .min(Comparator.comparingLong(MarketFootballerView::price)).orElseThrow();
    }

    private void setMoney(Long teamId, long money) {
        transaction.executeWithoutResult(status -> {
            var team = teams.findById(teamId).orElseThrow();
            team.addMoney(money - team.getMoney());
        });
    }

    private static TeamView.FootballerView first(TeamView view, boolean inLineup, Position position) {
        return view.squad().stream()
                .filter(f -> (f.line() != null) == inLineup && (position == null || f.position() == position))
                .findFirst().orElseThrow();
    }

    // ---------- The market ----------

    @Test
    void theMarketOpensWithTheSeason() {
        assertThat(market.market(dana.getId(), league.getId())).isEmpty();

        start();

        List<MarketFootballerView> free = market.market(dana.getId(), league.getId());
        assertThat(free).hasSize(18).isSortedAccordingTo(Comparator.comparingInt(MarketFootballerView::rating).reversed());
        assertThat(free).allSatisfy(f -> {
            assertThat(f.price()).isEqualTo(Prices.price(f.rating(), f.age()));
            assertThat(f.wage()).isEqualTo(Prices.wage(f.rating()));
        });
    }

    @Test
    void onlyMembersSeeTheMarket() {
        Account stranger = accounts.save(new Account("Stranger", "not-a-real-hash"));

        assertThatThrownBy(() -> market.market(stranger.getId(), league.getId()))
                .isInstanceOf(LeagueException.class).hasMessageContaining("not in this league");
    }

    // ---------- Buying ----------

    @Test
    void buyingTakesTheMoneyAndPutsTheFootballerOnTheBench() {
        start();
        MarketFootballerView wanted = cheapest();

        TeamView after = market.buy(dana.getId(), danaTeam, wanted.id());

        assertThat(after.money()).isEqualTo(10_000_000 - wanted.price());
        assertThat(after.squad()).hasSize(19).filteredOn(f -> f.id().equals(wanted.id())).singleElement()
                .satisfies(f -> assertThat(f.line()).isNull());
        assertThat(market.market(dana.getId(), league.getId())).extracting(MarketFootballerView::id)
                .doesNotContain(wanted.id());
        assertThat(moneyRecords.findByTeamIdOrderByIdDesc(danaTeam)).first().satisfies(record -> {
            assertThat(record.getKind()).isEqualTo(MoneyKind.PURCHASE);
            assertThat(record.getAmount()).isEqualTo(-wanted.price());
            assertThat(record.getNote()).isEqualTo(wanted.name());
            assertThat(record.getRound()).isNull();
        });
    }

    @Test
    void aFootballerCanOnlyBeBoughtOnce() {
        start();
        MarketFootballerView wanted = cheapest();
        market.buy(dana.getId(), danaTeam, wanted.id());

        assertThatThrownBy(() -> market.buy(tom.getId(), tomTeam, wanted.id()))
                .isInstanceOf(LeagueException.class).hasMessageContaining("no longer on the market");
    }

    @Test
    void aTeamCannotSpendMoneyItDoesNotHave() {
        start();
        MarketFootballerView wanted = cheapest();
        setMoney(danaTeam, wanted.price() - 10_000);

        assertThatThrownBy(() -> market.buy(dana.getId(), danaTeam, wanted.id()))
                .isInstanceOf(LeagueException.class).hasMessageContaining("enough money");
        assertThat(moneyRecords.findByTeamIdOrderByIdDesc(danaTeam)).isEmpty();
    }

    @Test
    void aSquadCannotGrowPast22() {
        start();
        setMoney(danaTeam, 1_000_000_000);
        for (int i = 0; i < 4; i++) {
            market.buy(dana.getId(), danaTeam, cheapest().id());
        }

        assertThatThrownBy(() -> market.buy(dana.getId(), danaTeam, cheapest().id()))
                .isInstanceOf(LeagueException.class).hasMessageContaining("at most 22");
    }

    @Test
    void onlyTheManagerCanBuyForHisTeam() {
        start();

        assertThatThrownBy(() -> market.buy(tom.getId(), danaTeam, cheapest().id()))
                .isInstanceOf(LeagueException.class).hasMessageContaining("not your team");
    }

    // ---------- Selling ----------

    @Test
    void sellingALineupPlayerReturns70PercentAndPicksANewLineup() {
        start();
        TeamView.FootballerView sold = first(teamService.view(dana.getId(), danaTeam), true, null);

        TeamView after = market.sell(dana.getId(), danaTeam, sold.id());

        assertThat(after.money()).isEqualTo(10_000_000 + Prices.sellPrice(sold.rating(), sold.age()));
        assertThat(after.squad()).hasSize(17).extracting(TeamView.FootballerView::id).doesNotContain(sold.id());
        assertThat(after.squad()).filteredOn(f -> f.line() != null).hasSize(11);
        assertThat(market.market(dana.getId(), league.getId())).extracting(MarketFootballerView::id).contains(sold.id());
        assertThat(moneyRecords.findByTeamIdOrderByIdDesc(danaTeam)).first()
                .extracting(MoneyRecord::getKind).isEqualTo(MoneyKind.SALE);
    }

    @Test
    void sellingABenchPlayerKeepsTheLineup() {
        start();
        TeamView before = teamService.view(dana.getId(), danaTeam);
        TeamView.FootballerView sold = first(before, false, null);

        TeamView after = market.sell(dana.getId(), danaTeam, sold.id());

        assertThat(after.squad()).filteredOn(f -> f.line() != null).extracting(TeamView.FootballerView::id)
                .containsExactlyInAnyOrderElementsOf(before.squad().stream().filter(f -> f.line() != null)
                        .map(TeamView.FootballerView::id).toList());
    }

    @Test
    void aSquadCannotGoBelow16() {
        start();
        market.sell(dana.getId(), danaTeam, first(teamService.view(dana.getId(), danaTeam), false, null).id());
        market.sell(dana.getId(), danaTeam, first(teamService.view(dana.getId(), danaTeam), false, null).id());

        Long third = first(teamService.view(dana.getId(), danaTeam), true, null).id();
        assertThatThrownBy(() -> market.sell(dana.getId(), danaTeam, third))
                .isInstanceOf(LeagueException.class).hasMessageContaining("at least 16");
    }

    @Test
    void theLastGoalkeeperCannotBeSold() {
        start();
        market.sell(dana.getId(), danaTeam, first(teamService.view(dana.getId(), danaTeam), false, Position.GOALKEEPER).id());

        Long lastGoalkeeper = first(teamService.view(dana.getId(), danaTeam), true, Position.GOALKEEPER).id();
        assertThatThrownBy(() -> market.sell(dana.getId(), danaTeam, lastGoalkeeper))
                .isInstanceOf(LeagueException.class).hasMessageContaining("last goalkeeper");
    }

    @Test
    void nobodyCanSellAnotherTeamsFootballer() {
        start();
        Long tomsPlayer = teamService.view(tom.getId(), tomTeam).squad().getFirst().id();

        assertThatThrownBy(() -> market.sell(dana.getId(), danaTeam, tomsPlayer))
                .isInstanceOf(LeagueException.class).hasMessageContaining("not in your squad");
    }
}
