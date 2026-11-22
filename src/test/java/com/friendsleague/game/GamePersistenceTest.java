package com.friendsleague.game;

import com.friendsleague.IntegrationTest;
import com.friendsleague.account.Account;
import com.friendsleague.account.AccountRepository;
import com.friendsleague.league.League;
import com.friendsleague.league.LeagueRepository;
import com.friendsleague.match.Goal;
import com.friendsleague.match.MatchResult;
import com.friendsleague.match.Side;
import com.friendsleague.team.Team;
import com.friendsleague.team.TeamRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GamePersistenceTest extends IntegrationTest {

    private static final Instant KICKOFF = Instant.parse("2026-11-22T19:00:00Z");

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private LeagueRepository leagues;

    @Autowired
    private TeamRepository teams;

    @Autowired
    private GameRepository games;

    @Autowired
    private TransactionTemplate transaction;

    private Game newGame() {
        Account owner = accounts.save(new Account("Dana", "not-a-real-hash"));
        League league = leagues.save(new League("Test league", "GAME01", 6, owner, "21:00"));
        Team home = teams.save(new Team(league, owner, "Home", 0));
        Team away = teams.save(new Team(league, null, "Away", 0));
        return games.save(new Game(league, 1, 1, home, away, KICKOFF));
    }

    private static MatchResult twoOne() {
        return new MatchResult(2, 1, List.of(
                new Goal(10, Side.HOME, "יוסי כהן"),
                new Goal(40, Side.AWAY, "אבי לוי"),
                new Goal(80, Side.HOME, "יוסי כהן")));
    }

    @Test
    void aGameWaitsToBePlayedOnceItsKickoffHasPassed() {
        Game game = newGame();

        assertThat(games.findByKickoffAtLessThanEqualAndHomeGoalsIsNullOrderByKickoffAt(KICKOFF.minusSeconds(1))).isEmpty();
        assertThat(games.findByKickoffAtLessThanEqualAndHomeGoalsIsNullOrderByKickoffAt(KICKOFF))
                .extracting(Game::getId).containsExactly(game.getId());
    }

    @Test
    void theResultAndGoalsAreSavedInOrder() {
        Game game = newGame();
        game.recordResult(twoOne());
        games.save(game);

        transaction.executeWithoutResult(status -> {
            Game loaded = games.findById(game.getId()).orElseThrow();
            assertThat(loaded.isPlayed()).isTrue();
            assertThat(loaded.getHomeGoals()).isEqualTo(2);
            assertThat(loaded.getAwayGoals()).isEqualTo(1);
            assertThat(loaded.getGoals()).extracting(GameGoal::getMinute).containsExactly(10, 40, 80);
        });
        assertThat(games.findByKickoffAtLessThanEqualAndHomeGoalsIsNullOrderByKickoffAt(KICKOFF.plusSeconds(600))).isEmpty();
    }

    @Test
    void theLiveViewShowsOnlyGoalsThatHaveHappened() {
        Game game = newGame();
        game.recordResult(twoOne());

        // 2 minutes after kick-off = minute 40.
        Instant duringTheGame = KICKOFF.plusSeconds(120);
        assertThat(game.goalsUntil(duringTheGame)).extracting(GameGoal::getMinute).containsExactly(10, 40);
        assertThat(game.scoreAt(Side.HOME, duringTheGame)).isEqualTo(1);
        assertThat(game.scoreAt(Side.AWAY, duringTheGame)).isEqualTo(1);
        assertThat(game.isOverAt(duringTheGame)).isFalse();

        Instant afterTheGame = KICKOFF.plusSeconds(300);
        assertThat(game.scoreAt(Side.HOME, afterTheGame)).isEqualTo(2);
        assertThat(game.isOverAt(afterTheGame)).isTrue();

        assertThat(game.goalsUntil(KICKOFF.minusSeconds(10))).isEmpty();
    }

    @Test
    void aGameCannotBePlayedTwice() {
        Game game = newGame();
        game.recordResult(twoOne());

        assertThatThrownBy(() -> game.recordResult(twoOne())).isInstanceOf(IllegalStateException.class);
    }
}
