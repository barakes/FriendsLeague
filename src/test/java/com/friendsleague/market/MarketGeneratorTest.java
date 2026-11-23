package com.friendsleague.market;

import com.friendsleague.squad.FootballerProfile;
import com.friendsleague.squad.NameGenerator;
import com.friendsleague.squad.Position;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class MarketGeneratorTest {

    private static List<FootballerProfile> market(long seed, int teams) {
        Random random = new Random(seed);
        return new MarketGenerator(random, new NameGenerator(random)).generate(teams);
    }

    @Test
    void thereAreThreeFreeAgentsPerTeam() {
        assertThat(market(1, 6)).hasSize(18);
        assertThat(market(1, 12)).hasSize(36);
    }

    @Test
    void everyPositionIsOnTheMarket() {
        for (long seed = 0; seed < 200; seed++) {
            assertThat(market(seed, 6)).extracting(FootballerProfile::position)
                    .contains(Position.GOALKEEPER, Position.DEFENDER, Position.MIDFIELDER, Position.FORWARD);
        }
    }

    @Test
    void ratingsAndAgesStayInRangeAndNamesDoNotRepeat() {
        for (long seed = 0; seed < 200; seed++) {
            List<FootballerProfile> market = market(seed, 12);
            assertThat(market).allSatisfy(footballer -> {
                assertThat(footballer.rating()).isBetween(45, 90);
                assertThat(footballer.age()).isBetween(18, 33);
            });
            assertThat(market).extracting(FootballerProfile::name).doesNotHaveDuplicates();
        }
    }

    @Test
    void mostAreOrdinaryAndAFewAreStars() {
        int stars = 0;
        int total = 0;
        for (long seed = 0; seed < 300; seed++) {
            for (FootballerProfile footballer : market(seed, 12)) {
                total++;
                if (footballer.rating() >= 83) {
                    stars++;
                }
            }
        }
        // About 5% stars.
        assertThat((double) stars / total).isBetween(0.03, 0.07);
    }
}
