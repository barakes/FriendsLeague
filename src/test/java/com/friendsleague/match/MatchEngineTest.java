package com.friendsleague.match;

import com.friendsleague.squad.FootballerProfile;
import com.friendsleague.squad.Formation;
import com.friendsleague.squad.Lineup;
import com.friendsleague.squad.Position;
import com.friendsleague.squad.SquadGenerator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

// Thousands of simulated matches, to check that results look like real football.
class MatchEngineTest {

    // A squad where every player has the same rating, so only the thing being tested makes a difference.
    private static List<FootballerProfile> evenSquad(int rating) {
        List<FootballerProfile> squad = new ArrayList<>();
        int number = 0;
        for (Map.Entry<Position, Integer> entry : SquadGenerator.COMPOSITION.entrySet()) {
            for (int i = 0; i < entry.getValue(); i++) {
                squad.add(new FootballerProfile("שחקן " + number++, entry.getKey(), rating, 25));
            }
        }
        return squad;
    }

    private static TeamSheet sheet(String name, List<FootballerProfile> squad, Formation formation, Tactic tactic) {
        return new TeamSheet(name, Lineup.best(formation, squad), tactic);
    }

    @Test
    void theStrongerTeamUsuallyWinsButNotAlways() {
        TeamSheet strong = sheet("strong", evenSquad(65), Formation.F_4_4_2, Tactic.BALANCED);
        TeamSheet weak = sheet("weak", evenSquad(55), Formation.F_4_4_2, Tactic.BALANCED);
        MatchEngine engine = new MatchEngine(new Random(1));
        int strongWins = 0;
        int weakWins = 0;
        int games = 4_000;

        for (int i = 0; i < games; i++) {
            // Half the games at home, half away.
            boolean strongAtHome = i % 2 == 0;
            MatchResult result = strongAtHome ? engine.play(strong, weak) : engine.play(weak, strong);
            Side strongSide = strongAtHome ? Side.HOME : Side.AWAY;
            if (result.winner() == strongSide) {
                strongWins++;
            } else if (result.winner() != null) {
                weakWins++;
            }
        }

        // A gap of 10 points: the stronger team wins about 60% of games, and the weaker one still wins some.
        assertThat(strongWins / (double) games).isBetween(0.55, 0.67);
        assertThat(weakWins / (double) games).isGreaterThan(0.10);
    }

    @Test
    void equalTeamsGiveRealisticResultsWithAHomeAdvantage() {
        TeamSheet home = sheet("home", evenSquad(60), Formation.F_4_4_2, Tactic.BALANCED);
        TeamSheet away = sheet("away", evenSquad(60), Formation.F_4_4_2, Tactic.BALANCED);
        MatchEngine engine = new MatchEngine(new Random(2));
        int homeWins = 0;
        int draws = 0;
        int awayWins = 0;
        long goals = 0;
        int games = 4_000;

        for (int i = 0; i < games; i++) {
            MatchResult result = engine.play(home, away);
            goals += result.homeGoals() + result.awayGoals();
            if (result.winner() == Side.HOME) {
                homeWins++;
            } else if (result.winner() == null) {
                draws++;
            } else {
                awayWins++;
            }
        }

        assertThat(homeWins / (double) games).isGreaterThan(awayWins / (double) games + 0.05);
        assertThat(draws / (double) games).isBetween(0.18, 0.33);
        // Real football: about 2.5 to 3 goals a game.
        assertThat(goals / (double) games).isBetween(2.4, 3.1);
    }

    @Test
    void noFormationIsBetterByItself() {
        MatchEngine engine = new MatchEngine(new Random(3));
        int games = 10_000;

        for (Formation formation : Formation.values()) {
            TeamSheet tested = sheet("tested", evenSquad(60), formation, Tactic.BALANCED);
            TeamSheet reference = sheet("reference", evenSquad(60), Formation.F_4_4_2, Tactic.BALANCED);
            int testedWins = 0;
            int referenceWins = 0;

            for (int i = 0; i < games; i++) {
                boolean testedAtHome = i % 2 == 0;
                MatchResult result = testedAtHome ? engine.play(tested, reference) : engine.play(reference, tested);
                Side testedSide = testedAtHome ? Side.HOME : Side.AWAY;
                if (result.winner() == testedSide) {
                    testedWins++;
                } else if (result.winner() != null) {
                    referenceWins++;
                }
            }

            // With equal players, every formation wins about as often as it loses against a 4-4-2.
            assertThat(Math.abs(testedWins - referenceWins) / (double) games)
                    .as(formation.label())
                    .isLessThan(0.04);
        }
    }

    @Test
    void attackingGamesHaveMoreGoalsThanDefensiveOnes() {
        MatchEngine engine = new MatchEngine(new Random(4));
        TeamSheet attackingHome = sheet("a", evenSquad(60), Formation.F_4_4_2, Tactic.ATTACKING);
        TeamSheet attackingAway = sheet("b", evenSquad(60), Formation.F_4_4_2, Tactic.ATTACKING);
        TeamSheet defensiveHome = sheet("a", evenSquad(60), Formation.F_4_4_2, Tactic.DEFENSIVE);
        TeamSheet defensiveAway = sheet("b", evenSquad(60), Formation.F_4_4_2, Tactic.DEFENSIVE);
        long attackingGoals = 0;
        long defensiveGoals = 0;

        for (int i = 0; i < 4_000; i++) {
            MatchResult attacking = engine.play(attackingHome, attackingAway);
            attackingGoals += attacking.homeGoals() + attacking.awayGoals();
            MatchResult defensive = engine.play(defensiveHome, defensiveAway);
            defensiveGoals += defensive.homeGoals() + defensive.awayGoals();
        }

        assertThat(attackingGoals).isGreaterThan(defensiveGoals * 2);
    }

    @Test
    void goalsAreInTimeOrderAndScoredByPlayersOnThePitch() {
        TeamSheet home = sheet("home", evenSquad(70), Formation.F_4_3_3, Tactic.ATTACKING);
        TeamSheet away = sheet("away", evenSquad(50), Formation.F_5_3_2, Tactic.DEFENSIVE);
        MatchEngine engine = new MatchEngine(new Random(5));

        for (int i = 0; i < 200; i++) {
            MatchResult result = engine.play(home, away);

            assertThat(result.goals()).hasSize(result.homeGoals() + result.awayGoals());
            int previousMinute = 0;
            for (Goal goal : result.goals()) {
                assertThat(goal.minute()).isBetween(previousMinute, MatchEngine.MINUTES);
                previousMinute = goal.minute();

                Lineup scorerTeam = goal.side() == Side.HOME ? home.lineup() : away.lineup();
                assertThat(scorerTeam.allPlayers()).extracting(FootballerProfile::name).contains(goal.scorer());
            }
        }
    }

    @Test
    void theSameSeedGivesTheSameMatch() {
        TeamSheet home = sheet("home", evenSquad(60), Formation.F_4_4_2, Tactic.BALANCED);
        TeamSheet away = sheet("away", evenSquad(62), Formation.F_4_3_3, Tactic.ATTACKING);

        MatchResult first = new MatchEngine(new Random(9)).play(home, away);
        MatchResult second = new MatchEngine(new Random(9)).play(home, away);

        assertThat(first).isEqualTo(second);
    }
}