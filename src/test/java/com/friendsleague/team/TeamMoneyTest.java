package com.friendsleague.team;

import com.friendsleague.IntegrationTest;
import com.friendsleague.account.Account;
import com.friendsleague.account.AccountRepository;
import com.friendsleague.game.GameRepository;
import com.friendsleague.game.RoundRunner;
import com.friendsleague.league.League;
import com.friendsleague.league.LeagueException;
import com.friendsleague.league.LeagueService;
import com.friendsleague.money.MoneyKind;
import com.friendsleague.money.Prices;
import com.friendsleague.money.Sponsor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TeamMoneyTest extends IntegrationTest {

    @Autowired
    private TeamService teamService;

    @Autowired
    private LeagueService leagueService;

    @Autowired
    private RoundRunner runner;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private TeamRepository teams;

    @Autowired
    private GameRepository games;

    private Account dana;
    private Account tom;
    private League league;
    private Long danaTeam;

    @BeforeEach
    void createLeague() {
        dana = accounts.save(new Account("Dana", "not-a-real-hash"));
        tom = accounts.save(new Account("Tom", "not-a-real-hash"));
        league = leagueService.createLeague(dana.getId(), "Test league", 6, "21:00", "הפועל דנה");
        leagueService.joinLeague(tom.getId(), league.getJoinCode(), "מכבי תום");
        danaTeam = teams.findByLeagueIdAndAccountId(league.getId(), dana.getId()).orElseThrow().getId();
    }

    @Test
    void aNewTeamHasTheSafeSponsorAndShowsItsWages() {
        TeamView view = teamService.view(dana.getId(), danaTeam);

        assertThat(view.sponsor()).isEqualTo(Sponsor.SAFE);
        assertThat(view.roundsInDebt()).isZero();
        assertThat(view.wagesPerRound()).isEqualTo(view.squad().stream().mapToLong(TeamView.FootballerView::wage).sum());
        assertThat(view.squad()).allSatisfy(footballer -> {
            assertThat(footballer.wage()).isEqualTo(Prices.wage(footballer.rating()));
            assertThat(footballer.sellPrice()).isEqualTo(Prices.sellPrice(footballer.rating(), footballer.age()));
        });
    }

    @Test
    void theSponsorCanBeChangedBeforeTheSeasonButNotDuringIt() {
        assertThat(teamService.chooseSponsor(dana.getId(), danaTeam, Sponsor.RISKY).sponsor()).isEqualTo(Sponsor.RISKY);

        leagueService.startSeason(dana.getId(), league.getId());

        assertThatThrownBy(() -> teamService.chooseSponsor(dana.getId(), danaTeam, Sponsor.SAFE))
                .isInstanceOf(LeagueException.class).hasMessageContaining("between seasons");
        assertThat(teamService.view(dana.getId(), danaTeam).sponsor()).isEqualTo(Sponsor.RISKY);
    }

    @Test
    void onlyTheManagerCanChooseTheSponsor() {
        assertThatThrownBy(() -> teamService.chooseSponsor(tom.getId(), danaTeam, Sponsor.RISKY))
                .isInstanceOf(LeagueException.class).hasMessageContaining("not your team");
    }

    @Test
    void theAccountHistoryShowsTheRoundsPaymentsToTheManagerOnly() {
        leagueService.startSeason(dana.getId(), league.getId());
        Instant kickoff = games.findByLeagueIdAndSeasonAndRoundOrderById(league.getId(), 1, 1).getFirst().getKickoffAt();
        runner.playDueGames(kickoff);
        runner.settleFinishedGames(kickoff.plusSeconds(270));

        assertThat(teamService.moneyHistory(dana.getId(), danaTeam))
                .isNotEmpty()
                .allSatisfy(record -> {
                    assertThat(record.season()).isEqualTo(1);
                    assertThat(record.round()).isEqualTo(1);
                })
                .extracting(MoneyRecordView::kind).contains(MoneyKind.TV_RIGHTS, MoneyKind.WAGES);

        assertThatThrownBy(() -> teamService.moneyHistory(tom.getId(), danaTeam))
                .isInstanceOf(LeagueException.class).hasMessageContaining("not your team");
    }
}
