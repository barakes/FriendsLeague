package com.friendsleague.season;

import com.friendsleague.squad.FootballerProfile;
import com.friendsleague.squad.Position;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class SeasonRulesTest {

    private static FootballerProfile footballer(int rating, int age) {
        return new FootballerProfile("שחקן", Position.MIDFIELDER, rating, age);
    }

    @Test
    void everyoneGetsAYearOlder() {
        Random random = new Random(1);
        assertThat(SeasonRules.olderBySeason(footballer(60, 18), random).age()).isEqualTo(19);
        assertThat(SeasonRules.olderBySeason(footballer(60, 34), random).age()).isEqualTo(35);
    }

    @Test
    void youngPlayersImproveAndOldPlayersDecline() {
        Random random = new Random(2);
        for (int i = 0; i < 500; i++) {
            assertThat(SeasonRules.olderBySeason(footballer(60, 20), random).rating()).isBetween(61, 64);
            assertThat(SeasonRules.olderBySeason(footballer(60, 26), random).rating()).isBetween(59, 62);
            assertThat(SeasonRules.olderBySeason(footballer(60, 31), random).rating()).isBetween(57, 60);
            assertThat(SeasonRules.olderBySeason(footballer(60, 34), random).rating()).isBetween(56, 59);
        }
    }

    @Test
    void theRatingStaysBetween1And99() {
        Random random = new Random(3);
        for (int i = 0; i < 200; i++) {
            assertThat(SeasonRules.olderBySeason(footballer(99, 19), random).rating()).isEqualTo(99);
            assertThat(SeasonRules.olderBySeason(footballer(1, 34), random).rating()).isEqualTo(1);
        }
    }

    @Test
    void footballersRetireAt36() {
        assertThat(SeasonRules.retires(footballer(60, 35))).isFalse();
        assertThat(SeasonRules.retires(footballer(60, 36))).isTrue();
    }

    @Test
    void aSquadWithoutAGoalkeeperGetsOne() {
        List<Position> squad = new ArrayList<>(Collections.nCopies(17, Position.DEFENDER));

        assertThat(SeasonRules.youthNeeded(squad)).containsExactly(Position.GOALKEEPER);
    }

    @Test
    void aSmallSquadIsFilledTo16InTheShortestPositions() {
        List<Position> squad = new ArrayList<>(Collections.nCopies(2, Position.GOALKEEPER));
        squad.addAll(Collections.nCopies(6, Position.DEFENDER));
        squad.addAll(Collections.nCopies(6, Position.MIDFIELDER));

        // 14 players and no forwards: the two missing players are forwards.
        assertThat(SeasonRules.youthNeeded(squad)).containsExactly(Position.FORWARD, Position.FORWARD);
    }

    @Test
    void aFullSquadNeedsNobody() {
        List<Position> squad = new ArrayList<>(Collections.nCopies(2, Position.GOALKEEPER));
        squad.addAll(Collections.nCopies(14, Position.DEFENDER));

        assertThat(SeasonRules.youthNeeded(squad)).isEmpty();
    }

    @Test
    void academyPlayersAreYoung() {
        Random random = new Random(4);
        for (int i = 0; i < 200; i++) {
            FootballerProfile youth = SeasonRules.youthPlayer("נער", Position.FORWARD, random);
            assertThat(youth.age()).isEqualTo(18);
            assertThat(youth.rating()).isBetween(45, 55);
        }
    }
}
