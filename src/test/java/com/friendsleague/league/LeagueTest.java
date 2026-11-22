package com.friendsleague.league;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LeagueTest {

    private static final int[] LEAGUE_SIZES = {6, 8, 10, 12};

    // ---------- The schedule ----------

    @Test
    void everyPairMeetsTwiceOnceAtEachHome() {
        for (int teams : LEAGUE_SIZES) {
            List<Fixture> fixtures = ScheduleGenerator.doubleRoundRobin(teams);
            Map<String, Integer> meetings = new HashMap<>();
            fixtures.forEach(fixture -> meetings.merge(fixture.home() + ">" + fixture.away(), 1, Integer::sum));

            assertThat(fixtures).hasSize(teams * (teams - 1));
            for (int home = 0; home < teams; home++) {
                for (int away = 0; away < teams; away++) {
                    if (home != away) {
                        assertThat(meetings.get(home + ">" + away)).as(teams + " teams: " + home + " vs " + away).isEqualTo(1);
                    }
                }
            }
        }
    }

    @Test
    void nobodyPlaysTwiceInTheSameRound() {
        for (int teams : LEAGUE_SIZES) {
            int rounds = ScheduleGenerator.rounds(teams);
            assertThat(rounds).isEqualTo(2 * (teams - 1));

            for (int round = 1; round <= rounds; round++) {
                Set<Integer> playing = new HashSet<>();
                int games = 0;
                for (Fixture fixture : ScheduleGenerator.doubleRoundRobin(teams)) {
                    if (fixture.round() == round) {
                        assertThat(playing.add(fixture.home())).isTrue();
                        assertThat(playing.add(fixture.away())).isTrue();
                        games++;
                    }
                }
                // With an even number of teams, everyone plays every round.
                assertThat(games).isEqualTo(teams / 2);
            }
        }
    }

    @Test
    void noTeamPlaysMoreThanTwoRoundsInARowAtTheSameVenue() {
        for (int teams : LEAGUE_SIZES) {
            List<Fixture> fixtures = ScheduleGenerator.doubleRoundRobin(teams);
            int rounds = ScheduleGenerator.rounds(teams);

            for (int team = 0; team < teams; team++) {
                int homeGames = 0;
                int streak = 0;
                Boolean lastAtHome = null;
                for (int round = 1; round <= rounds; round++) {
                    for (Fixture fixture : fixtures) {
                        if (fixture.round() == round && (fixture.home() == team || fixture.away() == team)) {
                            boolean atHome = fixture.home() == team;
                            homeGames += atHome ? 1 : 0;
                            streak = Boolean.valueOf(atHome).equals(lastAtHome) ? streak + 1 : 1;
                            lastAtHome = atHome;
                            assertThat(streak).as(teams + " teams, team " + team + ", round " + round).isLessThanOrEqualTo(2);
                        }
                    }
                }
                assertThat(homeGames).isEqualTo(teams - 1);
            }
        }
    }

    @Test
    void withAnOddNumberOfTeamsEveryoneRestsOnceInEachHalf() {
        List<Fixture> fixtures = ScheduleGenerator.doubleRoundRobin(7);

        assertThat(ScheduleGenerator.rounds(7)).isEqualTo(14);
        for (int team = 0; team < 7; team++) {
            final int current = team;
            long games = fixtures.stream().filter(f -> f.home() == current || f.away() == current).count();
            assertThat(games).isEqualTo(12);
        }
    }

    @Test
    void aLeagueNeedsAtLeastTwoTeams() {
        assertThatThrownBy(() -> ScheduleGenerator.doubleRoundRobin(1)).isInstanceOf(IllegalArgumentException.class);
    }

    // ---------- The table ----------

    @Test
    void aWinIsWorthThreePointsAndADrawOne() {
        List<TableRow> table = LeagueTable.build(2, List.of(
                new PlayedGame(0, 1, 2, 0),
                new PlayedGame(1, 0, 1, 1)));

        TableRow first = table.getFirst();
        assertThat(first.team()).isZero();
        assertThat(first.played()).isEqualTo(2);
        assertThat(first.points()).isEqualTo(4);
        assertThat(first.goalDifference()).isEqualTo(2);
        assertThat(table.getLast().points()).isEqualTo(1);
    }

    @Test
    void equalPointsAreDecidedByGoalDifferenceThenByTeamNumber() {
        List<PlayedGame> games = List.of(
                new PlayedGame(0, 1, 2, 0),
                new PlayedGame(2, 0, 1, 1),
                new PlayedGame(1, 2, 3, 0),
                new PlayedGame(3, 2, 2, 0),
                new PlayedGame(3, 1, 1, 1));

        List<TableRow> table = LeagueTable.build(4, games);

        // Teams 0 and 3: 4 points, +2 each, 3 goals each: the lower number first. Team 1: 4 points but only +1.
        assertThat(table).extracting(TableRow::team).containsExactly(0, 3, 1, 2);
    }

    @Test
    void equalPointsAndGoalDifferenceAreDecidedByGoalsScored() {
        List<TableRow> table = LeagueTable.build(4, List.of(
                new PlayedGame(2, 3, 0, 0),
                new PlayedGame(1, 0, 3, 3)));

        assertThat(table).extracting(TableRow::team).containsExactly(0, 1, 2, 3);
        assertThat(table.getFirst().goalsFor()).isEqualTo(3);
    }

    @Test
    void deductedPointsMoveATeamDown() {
        List<PlayedGame> games = List.of(
                new PlayedGame(0, 1, 2, 0),
                new PlayedGame(2, 0, 1, 1),
                new PlayedGame(1, 2, 3, 0),
                new PlayedGame(3, 2, 2, 0),
                new PlayedGame(3, 1, 1, 1));

        List<TableRow> table = LeagueTable.build(4, games, new int[]{0, 0, 0, 3});

        assertThat(table).extracting(TableRow::team).containsExactly(0, 1, 3, 2);
        assertThat(table.get(2).points()).isEqualTo(1);
    }

    @Test
    void beforeAnyGameEveryoneHasZero() {
        List<TableRow> table = LeagueTable.build(3, List.of());

        assertThat(table).extracting(TableRow::points).containsOnly(0);
        assertThat(table).extracting(TableRow::team).containsExactly(0, 1, 2);
    }
}