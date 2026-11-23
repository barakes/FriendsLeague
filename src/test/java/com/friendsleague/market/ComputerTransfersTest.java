package com.friendsleague.market;

import com.friendsleague.IntegrationTest;
import com.friendsleague.account.Account;
import com.friendsleague.account.AccountRepository;
import com.friendsleague.game.GameRepository;
import com.friendsleague.game.RoundRunner;
import com.friendsleague.league.League;
import com.friendsleague.league.LeagueService;
import com.friendsleague.money.MoneyKind;
import com.friendsleague.team.FootballerRepository;
import com.friendsleague.team.MoneyRecord;
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

class ComputerTransfersTest extends IntegrationTest {

    @Autowired
    private RoundRunner runner;

    @Autowired
    private LeagueService leagueService;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private TeamRepository teams;

    @Autowired
    private FootballerRepository footballers;

    @Autowired
    private GameRepository games;

    @Autowired
    private MoneyRecordRepository moneyRecords;

    @Autowired
    private TransactionTemplate transaction;

    private League league;
    private Instant kickoff;

    @BeforeEach
    void startALeague() {
        Account dana = accounts.save(new Account("Dana", "not-a-real-hash"));
        league = leagueService.createLeague(dana.getId(), "Test league", 6, "21:00", "הפועל דנה");
        leagueService.startSeason(dana.getId(), league.getId());
        kickoff = games.findByLeagueIdAndSeasonAndRoundOrderById(league.getId(), 1, 1).getFirst().getKickoffAt();
    }

    private void giveEveryTeam(long money) {
        transaction.executeWithoutResult(status -> {
            for (Team team : teams.findByLeagueIdOrderById(league.getId())) {
                team.addMoney(money - team.getMoney());
            }
        });
    }

    private void playRoundOne() {
        runner.playDueGames(kickoff);
        runner.settleFinishedGames(kickoff.plusSeconds(270));
    }

    private List<Team> computerTeams() {
        return teams.findByLeagueIdOrderById(league.getId()).stream().filter(Team::isComputer).toList();
    }

    private Team friendsTeam() {
        return teams.findByLeagueIdOrderById(league.getId()).stream().filter(team -> !team.isComputer())
                .findFirst().orElseThrow();
    }

    private List<MoneyKind> kindsOf(Team team) {
        return moneyRecords.findByTeamIdOrderByIdDesc(team.getId()).stream().map(MoneyRecord::getKind).toList();
    }

    @Test
    void richComputerTeamsBuyAfterTheirGame() {
        giveEveryTeam(100_000_000);
        int marketBefore = footballers.findByLeagueIdAndTeamIsNullOrderByRatingDesc(league.getId()).size();

        playRoundOne();

        long buyers = computerTeams().stream().filter(team -> kindsOf(team).contains(MoneyKind.PURCHASE)).count();
        assertThat(buyers).isPositive();
        assertThat(footballers.findByLeagueIdAndTeamIsNullOrderByRatingDesc(league.getId()))
                .hasSize(marketBefore - (int) buyers);
        for (Team team : computerTeams()) {
            assertThat(footballers.findByTeamIdOrderById(team.getId())).hasSizeBetween(18, 19);
        }
    }

    @Test
    void aFriendsTeamIsNeverChangedByTheComputer() {
        giveEveryTeam(100_000_000);

        playRoundOne();

        Team friend = friendsTeam();
        assertThat(footballers.findByTeamIdOrderById(friend.getId())).hasSize(18);
        assertThat(kindsOf(friend)).doesNotContain(MoneyKind.PURCHASE, MoneyKind.SALE);
    }

    @Test
    void aComputerTeamInDebtSellsABenchPlayer() {
        Long poorTeam = computerTeams().getFirst().getId();
        transaction.executeWithoutResult(status ->
                teams.findById(poorTeam).orElseThrow().addMoney(-15_000_000));

        playRoundOne();

        Team team = teams.findById(poorTeam).orElseThrow();
        assertThat(kindsOf(team)).contains(MoneyKind.SALE).doesNotContain(MoneyKind.PURCHASE);
        assertThat(footballers.findByTeamIdOrderById(poorTeam)).hasSize(17);
    }
}
