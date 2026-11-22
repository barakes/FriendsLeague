package com.friendsleague.game;

import com.friendsleague.IntegrationTest;
import com.friendsleague.account.Account;
import com.friendsleague.account.AccountRepository;
import com.friendsleague.league.League;
import com.friendsleague.league.LeagueQueries;
import com.friendsleague.league.LeagueService;
import com.friendsleague.league.TableEntry;
import com.friendsleague.money.MoneyKind;
import com.friendsleague.money.Prices;
import com.friendsleague.money.RoundMoney;
import com.friendsleague.team.MoneyRecord;
import com.friendsleague.team.FootballerRepository;
import com.friendsleague.team.MoneyRecordRepository;
import com.friendsleague.team.Team;
import com.friendsleague.team.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MoneySettlementTest extends IntegrationTest {

    // When a game's broadcast is over: kick-off + 4.5 minutes.
    private static final long BROADCAST_SECONDS = 270;

    @Autowired
    private RoundRunner runner;

    @Autowired
    private LeagueService leagueService;

    @Autowired
    private LeagueQueries queries;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private TeamRepository teams;

    @Autowired
    private GameRepository games;

    @Autowired
    private MoneyRecordRepository moneyRecords;

    @Autowired
    private FootballerRepository footballers;

    @Autowired
    private TransactionTemplate transaction;

    private Account dana;
    private League league;
    private List<Game> season;

    @BeforeEach
    void startALeague() {
        dana = accounts.save(new Account("Dana", "not-a-real-hash"));
        league = leagueService.createLeague(dana.getId(), "Test league", 6, "21:00", "הפועל דנה");
        leagueService.startSeason(dana.getId(), league.getId());
        season = games.findByLeagueIdAndSeasonOrderByRoundAscIdAsc(league.getId(), 1);
    }

    private Instant kickoffOfRound(int round) {
        return season.stream().filter(game -> game.getRound() == round).findFirst().orElseThrow().getKickoffAt();
    }

    private void playRound(int round) {
        runner.playDueGames(kickoffOfRound(round));
        runner.settleFinishedGames(kickoffOfRound(round).plusSeconds(BROADCAST_SECONDS));
    }

    private Team danasTeam() {
        return teams.findByLeagueIdAndAccountId(league.getId(), dana.getId()).orElseThrow();
    }

    @Test
    void moneyIsPaidOnlyAfterTheBroadcastEnds() {
        runner.playDueGames(kickoffOfRound(1));

        assertThat(runner.settleFinishedGames(kickoffOfRound(1).plusSeconds(BROADCAST_SECONDS - 1))).isZero();
        assertThat(danasTeam().getMoney()).isEqualTo(10_000_000);

        assertThat(runner.settleFinishedGames(kickoffOfRound(1).plusSeconds(BROADCAST_SECONDS))).isEqualTo(3);
        assertThat(danasTeam().getMoney()).isNotEqualTo(10_000_000);
    }

    @Test
    void everyTeamGetsItsIncomeAndPaysItsWages() {
        playRound(1);

        for (Game game : games.findByLeagueIdAndSeasonAndRoundOrderById(league.getId(), 1, 1)) {
            assertThat(game.isSettled()).isTrue();
            Team home = teams.findById(game.getHomeTeam().getId()).orElseThrow();
            Team away = teams.findById(game.getAwayTeam().getId()).orElseThrow();

            List<MoneyRecord> homeRecords = moneyRecords.findByTeamIdOrderByIdDesc(home.getId());
            List<MoneyRecord> awayRecords = moneyRecords.findByTeamIdOrderByIdDesc(away.getId());
            assertThat(homeRecords).extracting(MoneyRecord::getKind)
                    .contains(MoneyKind.TV_RIGHTS, MoneyKind.TICKETS, MoneyKind.SPONSOR, MoneyKind.WAGES);
            assertThat(awayRecords).extracting(MoneyRecord::getKind)
                    .contains(MoneyKind.TV_RIGHTS, MoneyKind.SPONSOR, MoneyKind.WAGES)
                    .doesNotContain(MoneyKind.TICKETS);

            // The money in the team equals the starting money plus every line in its history.
            assertThat(home.getMoney()).isEqualTo(10_000_000 + homeRecords.stream().mapToLong(MoneyRecord::getAmount).sum());
            assertThat(away.getMoney()).isEqualTo(10_000_000 + awayRecords.stream().mapToLong(MoneyRecord::getAmount).sum());
            assertThat(homeRecords).filteredOn(r -> r.getKind() == MoneyKind.TV_RIGHTS).singleElement()
                    .extracting(MoneyRecord::getAmount).isEqualTo(RoundMoney.TV_RIGHTS);
        }
    }

    @Test
    void theWagesAreTheWholeSquadsWages() {
        playRound(1);
        Team team = danasTeam();
        long expected = footballers.findByTeamIdOrderById(team.getId()).stream()
                .mapToLong(footballer -> Prices.wage(footballer.getRating())).sum();

        assertThat(moneyRecords.findByTeamIdOrderByIdDesc(team.getId()))
                .filteredOn(r -> r.getKind() == MoneyKind.WAGES).singleElement()
                .extracting(MoneyRecord::getAmount).isEqualTo(-expected);
    }

    @Test
    void aGameIsPaidOnlyOnce() {
        playRound(1);
        long money = danasTeam().getMoney();

        assertThat(runner.settleFinishedGames(kickoffOfRound(1).plusSeconds(3_600))).isZero();
        assertThat(danasTeam().getMoney()).isEqualTo(money);
    }

    @Test
    void aTeamThatStaysInDebtForThreeRoundsLosesThreePoints() {
        transaction.executeWithoutResult(status ->
                teams.findById(danasTeam().getId()).orElseThrow().addMoney(-20_000_000));

        playRound(1);
        playRound(2);
        assertThat(danasTeam().getRoundsInDebt()).isEqualTo(2);
        assertThat(danasTeam().getPointsDeducted()).isZero();

        playRound(3);
        assertThat(danasTeam().getRoundsInDebt()).isEqualTo(3);
        assertThat(danasTeam().getPointsDeducted()).isEqualTo(3);

        TableEntry mine = queries.details(dana.getId(), league.getId(), kickoffOfRound(3).plusSeconds(BROADCAST_SECONDS))
                .table().stream().filter(TableEntry::mine).findFirst().orElseThrow();
        assertThat(mine.pointsDeducted()).isEqualTo(3);
        assertThat(mine.points()).isEqualTo(mine.wins() * 3 + mine.draws() - 3);
    }
}
