package com.friendsleague.season;

import com.friendsleague.IntegrationTest;
import com.friendsleague.account.Account;
import com.friendsleague.account.AccountRepository;
import com.friendsleague.game.Game;
import com.friendsleague.game.GameRepository;
import com.friendsleague.game.RoundRunner;
import com.friendsleague.league.League;
import com.friendsleague.league.LeagueDetails;
import com.friendsleague.league.LeagueException;
import com.friendsleague.league.LeagueQueries;
import com.friendsleague.league.LeagueRepository;
import com.friendsleague.league.LeagueService;
import com.friendsleague.league.LeagueStatus;
import com.friendsleague.money.MoneyKind;
import com.friendsleague.money.SeasonPrizes;
import com.friendsleague.squad.Position;
import com.friendsleague.team.Footballer;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// A whole season of a 6-team league (10 rounds), then the move to the next season.
class SeasonTest extends IntegrationTest {

    @Autowired
    private RoundRunner runner;

    @Autowired
    private LeagueService leagueService;

    @Autowired
    private LeagueQueries queries;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private LeagueRepository leagues;

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

    private Account dana;
    private League league;
    private Long danaTeam;

    @BeforeEach
    void startALeague() {
        dana = accounts.save(new Account("Dana", "not-a-real-hash"));
        league = leagueService.createLeague(dana.getId(), "Test league", 6, "21:00", "הפועל דנה");
        leagueService.startSeason(dana.getId(), league.getId());
        danaTeam = teams.findByLeagueIdAndAccountId(league.getId(), dana.getId()).orElseThrow().getId();
    }

    // Plays and pays rounds "from" to "to" of season 1, each after its broadcast ended.
    private void playRounds(int from, int to) {
        List<Game> season = games.findByLeagueIdAndSeasonOrderByRoundAscIdAsc(league.getId(), 1);
        for (int round = from; round <= to; round++) {
            int r = round;
            Instant kickoff = season.stream().filter(game -> game.getRound() == r).findFirst().orElseThrow().getKickoffAt();
            runner.playDueGames(kickoff);
            runner.settleFinishedGames(kickoff.plusSeconds(270));
        }
    }

    private LeagueStatus status() {
        return leagues.findById(league.getId()).orElseThrow().getStatus();
    }

    @Test
    void theSeasonEndsOnlyAfterTheLastGameIsPaid() {
        playRounds(1, 9);
        assertThat(status()).isEqualTo(LeagueStatus.RUNNING);

        playRounds(10, 10);
        assertThat(status()).isEqualTo(LeagueStatus.BETWEEN_SEASONS);
    }

    @Test
    void prizesArePaidByTheFinalTableAndTheChampionIsRemembered() {
        playRounds(1, 10);

        // The moment after the last broadcast, so the table counts every game.
        Instant end = games.findByLeagueIdAndSeasonOrderByRoundAscIdAsc(league.getId(), 1).getLast().getKickoffAt()
                .plusSeconds(300);
        LeagueDetails details = queries.details(dana.getId(), league.getId(), end);
        assertThat(details.table()).extracting(row -> row.played()).containsOnly(10);
        for (var row : details.table()) {
            List<MoneyRecord> prizes = moneyRecords.findByTeamIdOrderByIdDesc(row.teamId()).stream()
                    .filter(record -> record.getKind() == MoneyKind.SEASON_PRIZE).toList();
            assertThat(prizes).singleElement().extracting(MoneyRecord::getAmount)
                    .isEqualTo(SeasonPrizes.forPosition(row.position(), 6));
        }
        assertThat(details.champions()).singleElement().satisfies(champion -> {
            assertThat(champion.season()).isEqualTo(1);
            assertThat(champion.teamName()).isEqualTo(details.table().getFirst().teamName());
            assertThat(champion.points()).isEqualTo(details.table().getFirst().points());
        });
    }

    @Test
    void footballersGetOlderAndTheOldestRetire() {
        // Dana's two goalkeepers are 35: after the season they turn 36 and retire.
        transaction.executeWithoutResult(status -> {
            for (Footballer footballer : footballers.findByTeamIdOrderById(danaTeam)) {
                if (footballer.getPosition() == Position.GOALKEEPER) {
                    footballer.growOlder(35, footballer.getRating());
                }
            }
        });
        Map<Long, Integer> agesBefore = new HashMap<>();
        footballers.findByLeagueIdOrderById(league.getId()).forEach(f -> agesBefore.put(f.getId(), f.getAge()));

        playRounds(1, 10);

        List<Footballer> after = footballers.findByLeagueIdOrderById(league.getId());
        assertThat(after).allSatisfy(footballer -> {
            assertThat(footballer.getAge()).isLessThan(SeasonRules.RETIREMENT_AGE);
            Integer before = agesBefore.get(footballer.getId());
            if (before != null) {
                assertThat(footballer.getAge()).isEqualTo(before + 1);
            }
        });

        // The academy sent Dana a young goalkeeper, and he is in her lineup.
        List<Footballer> danasSquad = footballers.findByTeamIdOrderById(danaTeam);
        assertThat(danasSquad).filteredOn(f -> f.getPosition() == Position.GOALKEEPER).singleElement().satisfies(keeper -> {
            assertThat(keeper.getAge()).isEqualTo(SeasonRules.YOUTH_AGE);
            Team team = transaction.execute(status -> {
                Team loaded = teams.findById(danaTeam).orElseThrow();
                loaded.getLineup().size();
                return loaded;
            });
            assertThat(team.getLineup()).hasSize(11)
                    .anySatisfy(slot -> assertThat(slot.getFootballerId()).isEqualTo(keeper.getId()));
        });
    }

    @Test
    void theMarketIsToppedUpForTheNextSeason() {
        playRounds(1, 10);

        assertThat(footballers.findByLeagueIdAndTeamIsNullOrderByRatingDesc(league.getId())).hasSizeGreaterThanOrEqualTo(18);
    }

    @Test
    void pointsLostForDebtAreForgottenOnlyWhenTheNextSeasonStarts() {
        transaction.executeWithoutResult(status -> teams.findById(danaTeam).orElseThrow().addMoney(-50_000_000));

        playRounds(1, 10);
        // The final table of the season that ended still shows them.
        assertThat(teams.findById(danaTeam).orElseThrow().getPointsDeducted()).isEqualTo(9);

        leagueService.startSeason(dana.getId(), league.getId());

        assertThat(teams.findById(danaTeam).orElseThrow().getPointsDeducted()).isZero();
        assertThat(teams.findById(danaTeam).orElseThrow().getRoundsInDebt()).isPositive();
    }

    @Test
    void theOwnerStartsTheNextSeasonWithANewSchedule() {
        playRounds(1, 10);

        leagueService.startSeason(dana.getId(), league.getId());

        League next = leagues.findById(league.getId()).orElseThrow();
        assertThat(next.getStatus()).isEqualTo(LeagueStatus.RUNNING);
        assertThat(next.getSeason()).isEqualTo(2);
        List<Game> season2 = games.findByLeagueIdAndSeasonOrderByRoundAscIdAsc(league.getId(), 2);
        assertThat(season2).hasSize(30).allSatisfy(game -> {
            assertThat(game.isPlayed()).isFalse();
            assertThat(game.getKickoffAt()).isAfter(Instant.now());
        });
        assertThat(teams.findByLeagueIdOrderById(league.getId())).hasSize(6);
    }

    @Test
    void aFriendCanJoinOnlyBetweenSeasonsAndTakesOverAComputerTeam() {
        Account tom = accounts.save(new Account("Tom", "not-a-real-hash"));
        assertThatThrownBy(() -> leagueService.joinLeague(tom.getId(), league.getJoinCode(), "מכבי תום"))
                .isInstanceOf(LeagueException.class).hasMessageContaining("join next season");

        playRounds(1, 10);
        Team computer = teams.findByLeagueIdOrderById(league.getId()).stream().filter(Team::isComputer).findFirst().orElseThrow();
        int squadSize = footballers.findByTeamIdOrderById(computer.getId()).size();

        Team toms = leagueService.joinLeague(tom.getId(), league.getJoinCode(), "מכבי תום");

        Team saved = teams.findById(toms.getId()).orElseThrow();
        assertThat(saved.getId()).isEqualTo(computer.getId());
        assertThat(saved.isComputer()).isFalse();
        assertThat(saved.getName()).isEqualTo("מכבי תום");
        assertThat(saved.getMoney()).isEqualTo(computer.getMoney());
        assertThat(footballers.findByTeamIdOrderById(saved.getId())).hasSize(squadSize);
        assertThat(teams.findByLeagueIdOrderById(league.getId())).hasSize(6);
    }
}
