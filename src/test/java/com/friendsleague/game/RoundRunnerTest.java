package com.friendsleague.game;

import com.friendsleague.IntegrationTest;
import com.friendsleague.account.Account;
import com.friendsleague.account.AccountRepository;
import com.friendsleague.league.League;
import com.friendsleague.league.LeagueService;
import com.friendsleague.match.Side;
import com.friendsleague.match.TeamSheet;
import com.friendsleague.squad.FootballerProfile;
import com.friendsleague.team.Footballer;
import com.friendsleague.team.FootballerRepository;
import com.friendsleague.team.Team;
import com.friendsleague.team.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RoundRunnerTest extends IntegrationTest {

    @Autowired
    private RoundRunner runner;

    @Autowired
    private GamePlayer player;

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
    private TransactionTemplate transaction;

    private League league;
    private List<Game> season;

    @BeforeEach
    void startALeague() {
        Account dana = accounts.save(new Account("Dana", "not-a-real-hash"));
        league = leagueService.createLeague(dana.getId(), "Test league", 8, "21:00", "הפועל דנה");
        leagueService.startSeason(dana.getId(), league.getId());
        season = games.findByLeagueIdAndSeasonOrderByRoundAscIdAsc(league.getId(), 1);
    }

    private Instant kickoffOfRound(int round) {
        return season.stream().filter(game -> game.getRound() == round).findFirst().orElseThrow().getKickoffAt();
    }

    @Test
    void nothingIsPlayedBeforeKickoff() {
        assertThat(runner.playDueGames(kickoffOfRound(1).minusSeconds(1))).isZero();
    }

    @Test
    void atKickoffTheWholeRoundIsPlayed() {
        int played = runner.playDueGames(kickoffOfRound(1));

        assertThat(played).isEqualTo(4);
        List<Game> afterRoundOne = games.findByLeagueIdAndSeasonOrderByRoundAscIdAsc(league.getId(), 1);
        assertThat(afterRoundOne).filteredOn(game -> game.getRound() == 1).allSatisfy(game -> assertThat(game.isPlayed()).isTrue());
        assertThat(afterRoundOne).filteredOn(game -> game.getRound() == 2).allSatisfy(game -> assertThat(game.isPlayed()).isFalse());
    }

    @Test
    void aServerThatSleptCatchesUpOnEveryMissedRound() {
        // The server wakes up after round 3 should already have been played.
        int played = runner.playDueGames(kickoffOfRound(3).plusSeconds(600));

        assertThat(played).isEqualTo(12);
        // Running again plays nothing twice.
        assertThat(runner.playDueGames(kickoffOfRound(3).plusSeconds(600))).isZero();
    }

    @Test
    void savedResultsMatchTheirGoals() {
        runner.playDueGames(kickoffOfRound(2));

        transaction.executeWithoutResult(status -> {
            for (Game game : games.findByLeagueIdAndSeasonAndRoundOrderById(league.getId(), 1, 1)) {
                long home = game.getGoals().stream().filter(goal -> goal.getSide() == Side.HOME).count();
                assertThat(home).isEqualTo(game.getHomeGoals().longValue());
                assertThat(game.getGoals()).hasSize(game.getHomeGoals() + game.getAwayGoals());
            }
        });
    }

    @Test
    void aBrokenSavedLineupFallsBackToTheBestLineup() {
        // Take a player out of Dana's saved lineup (as if he was sold): the game must still be played.
        transaction.executeWithoutResult(status -> {
            Team dana = teams.findByLeagueIdOrderById(league.getId()).getFirst();
            Long inLineup = dana.getLineup().iterator().next().getFootballerId();
            Footballer sold = footballers.findById(inLineup).orElseThrow();
            sold.moveTo(null);

            TeamSheet sheet = player.sheetFor(dana);
            assertThat(sheet.lineup().allPlayers()).hasSize(11);
            assertThat(sheet.lineup().allPlayers()).extracting(FootballerProfile::name).doesNotContain(sold.getName());
        });

        assertThat(runner.playDueGames(kickoffOfRound(14))).isEqualTo(56);
    }
}
