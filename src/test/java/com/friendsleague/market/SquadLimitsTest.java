package com.friendsleague.market;

import com.friendsleague.squad.Position;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SquadLimitsTest {

    private static List<Position> squad(int size, int goalkeepers) {
        List<Position> positions = new ArrayList<>(Collections.nCopies(goalkeepers, Position.GOALKEEPER));
        positions.addAll(Collections.nCopies(size - goalkeepers, Position.DEFENDER));
        return positions;
    }

    @Test
    void aSquadCanGrowUpTo22() {
        assertThat(SquadLimits.whyCannotBuy(18)).isNull();
        assertThat(SquadLimits.whyCannotBuy(21)).isNull();
        assertThat(SquadLimits.whyCannotBuy(22)).contains("at most 22");
    }

    @Test
    void aSquadCannotGoBelow16() {
        assertThat(SquadLimits.whyCannotSell(squad(17, 2), Position.DEFENDER)).isNull();
        assertThat(SquadLimits.whyCannotSell(squad(16, 2), Position.DEFENDER)).contains("at least 16");
    }

    @Test
    void theLastGoalkeeperCannotBeSold() {
        assertThat(SquadLimits.whyCannotSell(squad(18, 2), Position.GOALKEEPER)).isNull();
        assertThat(SquadLimits.whyCannotSell(squad(18, 1), Position.GOALKEEPER)).contains("last goalkeeper");
        assertThat(SquadLimits.whyCannotSell(squad(18, 1), Position.DEFENDER)).isNull();
    }
}
