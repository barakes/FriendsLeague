package com.friendsleague.money;

import com.friendsleague.match.Side;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    // ---------- Prices and wages ----------

    @Test
    void betterFootballersCostMuchMore() {
        assertThat(Prices.price(60, 25)).isEqualTo(1_000_000);
        assertThat(Prices.price(70, 25)).isEqualTo(1_970_000);
        assertThat(Prices.price(80, 25)).isEqualTo(3_870_000);
        assertThat(Prices.price(90, 25)).isEqualTo(7_610_000);
    }

    @Test
    void olderFootballersAreCheaper() {
        assertThat(Prices.price(80, 29)).isEqualTo(Prices.price(80, 20));
        assertThat(Prices.price(80, 30)).isLessThan(Prices.price(80, 29));
        assertThat(Prices.price(80, 32)).isLessThan(Prices.price(80, 30));
        // Never below 30% of the full price.
        assertThat(Prices.price(80, 40)).isEqualTo(Math.round(3_870_000 * 0.30 / 10_000) * 10_000);
    }

    @Test
    void sellingReturnsLessThanBuying() {
        for (int rating = 1; rating <= 99; rating++) {
            assertThat(Prices.sellPrice(rating, 25)).isPositive().isLessThan(Prices.price(rating, 25) + 1);
        }
        assertThat(Prices.sellPrice(60, 25)).isEqualTo(700_000);
    }

    @Test
    void wagesGrowWithTheRating() {
        assertThat(Prices.wage(60)).isEqualTo(18_000);
        assertThat(Prices.wage(80)).isEqualTo(32_000);
    }

    // ---------- One round ----------

    @Test
    void aHomeWinWithTheSafeSponsor() {
        List<MoneyEntry> entries = RoundMoney.settle(Side.HOME, Side.HOME, Sponsor.SAFE, 330_000);

        assertThat(entries).extracting(MoneyEntry::kind).containsExactly(
                MoneyKind.TV_RIGHTS, MoneyKind.TICKETS, MoneyKind.WIN_BONUS, MoneyKind.SPONSOR, MoneyKind.WAGES);
        assertThat(RoundMoney.total(entries)).isEqualTo(150_000 + 200_000 + 100_000 + 80_000 - 330_000);
    }

    @Test
    void anAwayDrawHasNoTicketsAndADrawBonus() {
        List<MoneyEntry> entries = RoundMoney.settle(Side.AWAY, null, Sponsor.SAFE, 330_000);

        assertThat(entries).extracting(MoneyEntry::kind).doesNotContain(MoneyKind.TICKETS, MoneyKind.WIN_BONUS)
                .contains(MoneyKind.DRAW_BONUS);
        assertThat(RoundMoney.total(entries)).isEqualTo(150_000 + 40_000 + 80_000 - 330_000);
    }

    @Test
    void theRiskySponsorPaysMoreOnlyForAWin() {
        assertThat(Sponsor.RISKY.payment(true)).isGreaterThan(Sponsor.SAFE.payment(true));
        assertThat(Sponsor.RISKY.payment(false)).isLessThan(Sponsor.SAFE.payment(false));
        assertThat(RoundMoney.settle(Side.AWAY, Side.HOME, Sponsor.RISKY, 0))
                .contains(new MoneyEntry(MoneyKind.SPONSOR, 30_000));
    }

    // ---------- End of season ----------

    @Test
    void prizesGoDownFromTheChampionToTheLastTeam() {
        assertThat(SeasonPrizes.forPosition(1, 6)).isEqualTo(3_000_000);
        assertThat(SeasonPrizes.forPosition(6, 6)).isEqualTo(300_000);
        assertThat(SeasonPrizes.forPosition(2, 6)).isEqualTo(2_460_000);
        for (int position = 2; position <= 12; position++) {
            assertThat(SeasonPrizes.forPosition(position, 12)).isLessThan(SeasonPrizes.forPosition(position - 1, 12));
        }
        assertThatThrownBy(() -> SeasonPrizes.forPosition(7, 6)).isInstanceOf(IllegalArgumentException.class);
    }

    // ---------- Debt ----------

    @Test
    void aTeamCannotBuyWithMoneyItDoesNotHave() {
        assertThat(DebtRules.canBuy(5_000_000, 5_000_000)).isTrue();
        assertThat(DebtRules.canBuy(4_990_000, 5_000_000)).isFalse();
        assertThat(DebtRules.canBuy(-100_000, 10_000)).isFalse();
    }

    @Test
    void pointsAreDeductedEveryThirdRoundInDebt() {
        assertThat(DebtRules.pointsToDeduct(0)).isZero();
        assertThat(DebtRules.pointsToDeduct(1)).isZero();
        assertThat(DebtRules.pointsToDeduct(2)).isZero();
        assertThat(DebtRules.pointsToDeduct(3)).isEqualTo(3);
        assertThat(DebtRules.pointsToDeduct(4)).isZero();
        assertThat(DebtRules.pointsToDeduct(6)).isEqualTo(3);
    }
}
