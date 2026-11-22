package com.friendsleague.league;

import com.friendsleague.IntegrationTest;
import com.friendsleague.account.Account;
import com.friendsleague.account.AccountRepository;
import com.friendsleague.game.Game;
import com.friendsleague.game.GameRepository;
import com.friendsleague.game.RoundRunner;
import com.friendsleague.match.Side;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// The league page as it looks at different moments of a round.
class LeagueViewTest extends IntegrationTest {

    @Autowired
    private LeagueService leagueService;

    @Autowired
    private LeagueQueries queries;

    @Autowired
    private RoundRunner runner;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private GameRepository games;

    private Account dana;
    private League league;
    private Instant kickoff;

    @BeforeEach
    void playTheFirstRound() {
        dana = accounts.save(new Account("Dana", "not-a-real-hash"));
        league = leagueService.createLeague(dana.getId(), "Test league", 6, "21:00", "הפועל דנה");
        leagueService.startSeason(dana.getId(), league.getId());
        kickoff = games.findByLeagueIdAndSeasonAndRoundOrderById(league.getId(), 1, 1).getFirst().getKickoffAt();
        runner.playDueGames(kickoff);
    }

    @Test
    void theTableCountsAGameOnlyAfterItsBroadcastEnds() {
        LeagueDetails during = queries.details(dana.getId(), league.getId(), kickoff.plusSeconds(60));
        assertThat(during.currentRound()).isEqualTo(1);
        assertThat(during.totalRounds()).isEqualTo(10);
        assertThat(during.table()).extracting(TableEntry::played).containsOnly(0);

        LeagueDetails after = queries.details(dana.getId(), league.getId(), kickoff.plusSeconds(300));
        assertThat(after.currentRound()).isEqualTo(2);
        assertThat(after.table()).hasSize(6).extracting(TableEntry::played).containsOnly(1);
        assertThat(after.table()).extracting(TableEntry::position).containsExactly(1, 2, 3, 4, 5, 6);
        assertThat(after.table()).filteredOn(TableEntry::mine).singleElement()
                .extracting(TableEntry::teamName).isEqualTo("הפועל דנה");
    }

    @Test
    void duringTheBroadcastOnlyGoalsThatHappenedAreShown() {
        // 2 minutes after kick-off = minute 40.
        List<GameView> live = queries.round(dana.getId(), league.getId(), 1, kickoff.plusSeconds(120));

        assertThat(live).hasSize(3).allSatisfy(game -> {
            assertThat(game.status()).isEqualTo(GameView.Status.LIVE);
            assertThat(game.minute()).isEqualTo(40);
            assertThat(game.goals()).allSatisfy(goal -> assertThat(goal.minute()).isLessThanOrEqualTo(40));
            assertThat(game.homeScore()).isEqualTo((int) game.goals().stream().filter(g -> g.side() == Side.HOME).count());
        });
    }

    @Test
    void afterTheBroadcastTheFinalScoreIsShown() {
        List<GameView> finished = queries.round(dana.getId(), league.getId(), 1, kickoff.plusSeconds(300));

        for (GameView view : finished) {
            Game game = games.findById(view.id()).orElseThrow();
            assertThat(view.status()).isEqualTo(GameView.Status.FINISHED);
            assertThat(view.homeScore()).isEqualTo(game.getHomeGoals());
            assertThat(view.awayScore()).isEqualTo(game.getAwayGoals());
        }
    }

    @Test
    void aRoundThatHasNotStartedHasNoScores() {
        List<GameView> nextRound = queries.round(dana.getId(), league.getId(), 2, kickoff.plusSeconds(300));

        assertThat(nextRound).hasSize(3).allSatisfy(game -> {
            assertThat(game.status()).isEqualTo(GameView.Status.SCHEDULED);
            assertThat(game.homeScore()).isNull();
            assertThat(game.goals()).isEmpty();
        });
    }

    @Test
    void onlyMembersCanSeeALeague() {
        Account stranger = accounts.save(new Account("Stranger", "not-a-real-hash"));

        assertThatThrownBy(() -> queries.details(stranger.getId(), league.getId(), Instant.now()))
                .isInstanceOf(LeagueException.class).hasMessageContaining("not in this league");
    }
}
