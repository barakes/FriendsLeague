package com.friendsleague.squad;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Plain Java, no database: these tests are instant.
class SquadGeneratorTest {

    private static SquadGenerator generator(long seed) {
        Random random = new Random(seed);
        return new SquadGenerator(random, new NameGenerator(random));
    }

    @Test
    void aSquadHas18PlayersInTheRightPositions() {
        List<FootballerProfile> squad = generator(1).generate(60);

        assertThat(squad).hasSize(SquadGenerator.SQUAD_SIZE);
        Map<Position, Long> byPosition = squad.stream()
                .collect(Collectors.groupingBy(FootballerProfile::position, Collectors.counting()));
        assertThat(byPosition).containsEntry(Position.GOALKEEPER, 2L)
                .containsEntry(Position.DEFENDER, 6L)
                .containsEntry(Position.MIDFIELDER, 6L)
                .containsEntry(Position.FORWARD, 4L);
    }

    @Test
    void everySquadHasExactlyTheSameTotalStrength() {
        SquadGenerator generator = generator(2);

        for (int team = 0; team < 100; team++) {
            int total = generator.generate(60).stream().mapToInt(FootballerProfile::rating).sum();
            assertThat(total).isEqualTo(60 * SquadGenerator.SQUAD_SIZE);
        }
    }

    @Test
    void someSquadsHaveStarsAndOthersAreEven() {
        SquadGenerator generator = generator(42);
        int smallestGap = Integer.MAX_VALUE;
        int largestGap = 0;

        for (int team = 0; team < 100; team++) {
            List<Integer> ratings = generator.generate(60).stream().map(FootballerProfile::rating).toList();
            int gap = ratings.stream().max(Integer::compare).orElseThrow()
                    - ratings.stream().min(Integer::compare).orElseThrow();
            smallestGap = Math.min(smallestGap, gap);
            largestGap = Math.max(largestGap, gap);
        }

        // The gap between the best and the worst player differs a lot from team to team.
        assertThat(smallestGap).isLessThan(25);
        assertThat(largestGap).isGreaterThan(40);
    }

    @Test
    void ratingsAndAgesStayInRealisticRanges() {
        SquadGenerator generator = generator(3);

        for (int team = 0; team < 50; team++) {
            for (FootballerProfile footballer : generator.generate(60)) {
                assertThat(footballer.rating()).isBetween(SquadGenerator.LOWEST_RATING, SquadGenerator.HIGHEST_RATING);
                assertThat(footballer.age()).isBetween(SquadGenerator.YOUNGEST, SquadGenerator.OLDEST);
            }
        }
    }

    @Test
    void namesDoNotRepeatInsideALeague() {
        SquadGenerator generator = generator(42);
        Set<String> names = new HashSet<>();

        // The largest league: 12 teams.
        for (int team = 0; team < 12; team++) {
            generator.generate(60).forEach(footballer -> names.add(footballer.name()));
        }

        assertThat(names).hasSize(12 * SquadGenerator.SQUAD_SIZE);
    }

    @Test
    void theNameGeneratorNeverFailsEvenWhenNamesRunOut() {
        NameGenerator names = new NameGenerator(new Random(5));

        // More names than the 1,600 combinations: some repeat, but nothing breaks.
        for (int i = 0; i < 3_000; i++) {
            assertThat(names.next()).isNotBlank();
        }
    }

    @Test
    void theSameSeedGivesTheSameSquad() {
        assertThat(generator(7).generate(60)).isEqualTo(generator(7).generate(60));
    }

    @Test
    void anImpossibleAverageIsRejected() {
        assertThatThrownBy(() -> generator(1).generate(95)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> generator(1).generate(10)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anInvalidFootballerCannotBeCreated() {
        assertThatThrownBy(() -> new FootballerProfile("יוסי כהן", Position.FORWARD, 150, 25))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FootballerProfile(" ", Position.FORWARD, 70, 25))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FootballerProfile("יוסי כהן", null, 70, 25))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void everyFormationPutsElevenPlayersOnThePitch() {
        for (Formation formation : Formation.values()) {
            int players = 0;
            for (Position position : Position.values()) {
                players += formation.countFor(position);
            }
            assertThat(players).as(formation.label()).isEqualTo(11);
        }
    }
}