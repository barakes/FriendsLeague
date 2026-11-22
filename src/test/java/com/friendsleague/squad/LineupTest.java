package com.friendsleague.squad;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class LineupTest {

    private static List<FootballerProfile> squad(long seed) {
        return new SquadGenerator(new Random(seed), new NameGenerator(new Random(seed))).generate(60);
    }

    private static FootballerProfile player(Position position, int rating) {
        return new FootballerProfile("שחקן " + position + " " + rating, position, rating, 25);
    }

    // A copy of a lineup's lines that a test can change.
    private static Map<Position, List<FootballerProfile>> linesOf(Lineup lineup) {
        Map<Position, List<FootballerProfile>> lines = new EnumMap<>(Position.class);
        for (Position line : Position.values()) {
            lines.put(line, new ArrayList<>(lineup.playersIn(line)));
        }
        return lines;
    }

    // ---------- Playing out of position ----------

    @Test
    void aPlayerIsWeakerOutsideHisPosition() {
        FootballerProfile defender = player(Position.DEFENDER, 80);

        assertThat(Lineup.effectiveRating(defender, Position.DEFENDER)).isEqualTo(80);
        assertThat(Lineup.effectiveRating(defender, Position.MIDFIELDER)).isCloseTo(68, within(0.001));
        assertThat(Lineup.effectiveRating(defender, Position.FORWARD)).isCloseTo(56, within(0.001));
        assertThat(Lineup.effectiveRating(defender, Position.GOALKEEPER)).isCloseTo(24, within(0.001));
        assertThat(Lineup.effectiveRating(player(Position.GOALKEEPER, 80), Position.FORWARD)).isCloseTo(24, within(0.001));
    }

    // ---------- The best lineup ----------

    @Test
    void theBestLineupPutsEveryoneInHisOwnPositionWhenPossible() {
        Lineup lineup = Lineup.best(Formation.F_4_4_2, squad(1));

        assertThat(lineup.allPlayers()).hasSize(Lineup.PLAYERS);
        for (Position line : Position.values()) {
            assertThat(lineup.playersIn(line)).allSatisfy(footballer -> assertThat(footballer.position()).isEqualTo(line));
        }
    }

    @Test
    void theBestLineupIsReallyTheBest() {
        // Compared with trying every possible lineup, on small squads of 12 so that it stays fast.
        Random random = new Random(11);
        for (int team = 0; team < 10; team++) {
            List<FootballerProfile> full = new ArrayList<>(new SquadGenerator(random, new NameGenerator(random)).generate(60));
            Collections.shuffle(full, random);
            List<FootballerProfile> small = full.subList(0, 12);

            for (Formation formation : Formation.values()) {
                int[] free = new int[Position.values().length];
                for (Position line : Position.values()) {
                    free[line.ordinal()] = formation.countFor(line);
                }
                assertThat(Lineup.best(formation, small).totalStrength())
                        .isCloseTo(tryEverything(small, 0, free), within(1e-6));
            }
        }
    }

    @Test
    void withoutAGoalkeeperSomeoneElseGoesInGoal() {
        List<FootballerProfile> squad = new ArrayList<>(squad(2));
        squad.removeIf(footballer -> footballer.position() == Position.GOALKEEPER);

        Lineup lineup = Lineup.best(Formation.F_4_4_2, squad);

        assertThat(lineup.playersIn(Position.GOALKEEPER)).hasSize(1);
        assertThat(lineup.playersIn(Position.GOALKEEPER).getFirst().position()).isNotEqualTo(Position.GOALKEEPER);
    }

    @Test
    void aSquadOfFewerThanElevenCannotPlay() {
        assertThatThrownBy(() -> Lineup.best(Formation.F_4_4_2, squad(3).subList(0, 10)))
                .isInstanceOf(LineupException.class)
                .hasMessageContaining("at least 11");
    }

    @Test
    void theBestFormationIsTheStrongestOne() {
        List<FootballerProfile> squad = squad(4);

        Lineup best = Lineup.bestOfAll(squad);

        for (Formation formation : Formation.values()) {
            assertThat(best.totalStrength()).isGreaterThanOrEqualTo(Lineup.best(formation, squad).totalStrength());
        }
    }

    // ---------- A lineup the manager chose ----------

    @Test
    void aLineupNeedsTheRightNumberOfPlayersInEachLine() {
        Map<Position, List<FootballerProfile>> lines = linesOf(Lineup.best(Formation.F_4_4_2, squad(5)));
        lines.get(Position.FORWARD).removeFirst();

        assertThatThrownBy(() -> Lineup.of(Formation.F_4_4_2, lines))
                .isInstanceOf(LineupException.class)
                .hasMessageContaining("needs 2 players");
    }

    @Test
    void aPlayerCannotAppearTwice() {
        Map<Position, List<FootballerProfile>> lines = linesOf(Lineup.best(Formation.F_4_4_2, squad(6)));
        List<FootballerProfile> forwards = lines.get(Position.FORWARD);
        forwards.set(1, forwards.getFirst());

        assertThatThrownBy(() -> Lineup.of(Formation.F_4_4_2, lines))
                .isInstanceOf(LineupException.class)
                .hasMessageContaining("twice");
    }

    @Test
    void onlyPlayersFromTheSquadCanPlay() {
        List<FootballerProfile> squad = squad(7);
        Map<Position, List<FootballerProfile>> lines = linesOf(Lineup.best(Formation.F_4_4_2, squad));
        lines.get(Position.FORWARD).set(0, player(Position.FORWARD, 99));
        Lineup lineup = Lineup.of(Formation.F_4_4_2, lines);

        assertThatThrownBy(() -> lineup.checkBelongsTo(squad))
                .isInstanceOf(LineupException.class)
                .hasMessageContaining("not in your squad");
    }

    @Test
    void lineStrengthIsTheSumOfTheEffectiveRatings() {
        Map<Position, List<FootballerProfile>> lines = new EnumMap<>(Position.class);
        lines.put(Position.GOALKEEPER, List.of(player(Position.GOALKEEPER, 70)));
        lines.put(Position.DEFENDER, List.of(player(Position.DEFENDER, 60), player(Position.DEFENDER, 61),
                player(Position.DEFENDER, 62), player(Position.MIDFIELDER, 80)));
        lines.put(Position.MIDFIELDER, List.of(player(Position.MIDFIELDER, 70), player(Position.MIDFIELDER, 71),
                player(Position.MIDFIELDER, 72), player(Position.MIDFIELDER, 73)));
        lines.put(Position.FORWARD, List.of(player(Position.FORWARD, 75), player(Position.FORWARD, 76)));

        Lineup lineup = Lineup.of(Formation.F_4_4_2, lines);

        // The midfielder playing in defence counts as 80 x 0.85 = 68.
        assertThat(lineup.lineStrength(Position.DEFENDER)).isCloseTo(60 + 61 + 62 + 68, within(0.001));
        assertThat(lineup.lineStrength(Position.FORWARD)).isCloseTo(151, within(0.001));
    }

    // Tries every way to fill the free places, without any shortcut. Slow, but certainly right.
    private static double tryEverything(List<FootballerProfile> squad, int index, int[] free) {
        int remaining = 0;
        for (int count : free) {
            remaining += count;
        }
        if (remaining == 0) {
            return 0;
        }
        if (squad.size() - index < remaining) {
            return Double.NEGATIVE_INFINITY;
        }

        double best = tryEverything(squad, index + 1, free);
        for (Position line : Position.values()) {
            if (free[line.ordinal()] > 0) {
                free[line.ordinal()]--;
                best = Math.max(best, Lineup.effectiveRating(squad.get(index), line) + tryEverything(squad, index + 1, free));
                free[line.ordinal()]++;
            }
        }
        return best;
    }
}