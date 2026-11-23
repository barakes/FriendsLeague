package com.friendsleague.market;

import com.friendsleague.money.Prices;
import com.friendsleague.squad.FootballerProfile;
import com.friendsleague.squad.Lineup;
import com.friendsleague.squad.Position;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ComputerManagerTest {

    private static final FootballerProfile STAR = new FootballerProfile("כוכב", Position.FORWARD, 80, 25);
    private static final FootballerProfile WEAK = new FootballerProfile("חלש", Position.FORWARD, 40, 25);
    private static final long STAR_PRICE = Prices.price(80, 25);

    // A squad around the given rating. Extra players above 18 are weak defenders.
    private static List<FootballerProfile> squad(int size, int rating) {
        List<FootballerProfile> squad = new ArrayList<>();
        squad.add(new FootballerProfile("שוער 1", Position.GOALKEEPER, rating, 25));
        squad.add(new FootballerProfile("שוער 2", Position.GOALKEEPER, rating - 5, 25));
        for (int i = 0; i < 6; i++) {
            squad.add(new FootballerProfile("מגן " + i, Position.DEFENDER, rating - i, 25));
            squad.add(new FootballerProfile("קשר " + i, Position.MIDFIELDER, rating - i, 25));
        }
        for (int i = 0; i < 4; i++) {
            squad.add(new FootballerProfile("חלוץ " + i, Position.FORWARD, rating - i, 25));
        }
        for (int i = 18; i < size; i++) {
            squad.add(new FootballerProfile("מחליף " + i, Position.DEFENDER, rating - 20 - i, 25));
        }
        return squad;
    }

    @Test
    void itBuysAClearUpgrade() {
        ComputerManager.Decision decision = ComputerManager.decide(squad(18, 55), 20_000_000, List.of(WEAK, STAR));

        assertThat(decision.buy()).isEqualTo(STAR);
        assertThat(decision.sell()).isNull();
    }

    @Test
    void itDoesNotBuySomeoneWhoDoesNotHelp() {
        assertThat(ComputerManager.decide(squad(18, 55), 20_000_000, List.of(WEAK)))
                .isEqualTo(ComputerManager.Decision.NOTHING);
    }

    @Test
    void itAlwaysKeepsAReserveForWages() {
        long justTooLittle = STAR_PRICE + ComputerManager.RESERVE - 1;

        assertThat(ComputerManager.decide(squad(18, 55), justTooLittle, List.of(STAR)).buy()).isNull();
        assertThat(ComputerManager.decide(squad(18, 55), justTooLittle + 1, List.of(STAR)).buy()).isEqualTo(STAR);
    }

    @Test
    void withAFullSquadItSellsItsWeakestBenchPlayerFirst() {
        List<FootballerProfile> squad = squad(ComputerManager.MAX_SQUAD, 55);

        ComputerManager.Decision decision = ComputerManager.decide(squad, 20_000_000, List.of(STAR));

        assertThat(decision.buy()).isEqualTo(STAR);
        assertThat(decision.sell().name()).isEqualTo("מחליף 19");
    }

    @Test
    void inDebtItSellsItsMostValuableBenchPlayer() {
        List<FootballerProfile> squad = squad(18, 55);
        List<FootballerProfile> lineup = Lineup.bestOfAll(squad).allPlayers();

        ComputerManager.Decision decision = ComputerManager.decide(squad, -1, List.of(STAR));

        assertThat(decision.buy()).isNull();
        assertThat(decision.sell()).isNotNull();
        assertThat(lineup).doesNotContain(decision.sell());
        for (FootballerProfile benchPlayer : squad) {
            if (!lineup.contains(benchPlayer)) {
                assertThat(Prices.sellPrice(decision.sell().rating(), decision.sell().age()))
                        .isGreaterThanOrEqualTo(Prices.sellPrice(benchPlayer.rating(), benchPlayer.age()));
            }
        }
    }
}
