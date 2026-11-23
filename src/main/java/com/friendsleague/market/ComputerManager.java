package com.friendsleague.market;

import com.friendsleague.money.Prices;
import com.friendsleague.squad.FootballerProfile;
import com.friendsleague.squad.Lineup;
import com.friendsleague.squad.Position;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

// How a computer team uses the market after each of its games. At most one purchase and one sale each time.
// - In debt: it sells its most valuable bench player, to get back above zero.
// - Otherwise: it buys the free agent who makes its best lineup strongest, if the gain is worth it
//   and it keeps a reserve for wages. With a full squad, it first sells its weakest bench player.
public final class ComputerManager {

    // Money it always keeps after buying, so the wages of the next rounds can be paid.
    public static final long RESERVE = 1_500_000;
    // A computer squad stays at 20 or fewer, so it never blocks itself at the limit of 22.
    public static final int MAX_SQUAD = 20;
    // The lineup's total strength (the sum of 11 effective ratings) must grow at least this much.
    public static final double MIN_GAIN = 3.0;

    // What to do this time. Either part can be missing; both missing means "do nothing".
    public record Decision(FootballerProfile sell, FootballerProfile buy) {

        public static final Decision NOTHING = new Decision(null, null);
    }

    private ComputerManager() {
    }

    public static Decision decide(List<FootballerProfile> squad, long money, List<FootballerProfile> market) {
        if (money < 0) {
            return new Decision(mostValuableSellable(squad).orElse(null), null);
        }

        List<FootballerProfile> kept = new ArrayList<>(squad);
        long budget = money - RESERVE;
        FootballerProfile sell = null;
        if (squad.size() >= MAX_SQUAD) {
            sell = weakestSellable(squad).orElse(null);
            if (sell == null) {
                return Decision.NOTHING;
            }
            kept.remove(sell);
            budget += Prices.sellPrice(sell.rating(), sell.age());
        }

        Lineup now = Lineup.bestOfAll(kept);
        double strengthNow = now.totalStrength();
        FootballerProfile bestBuy = null;
        double bestGain = MIN_GAIN;
        for (FootballerProfile candidate : market) {
            if (Prices.price(candidate.rating(), candidate.age()) > budget) {
                continue;
            }
            // Quick check first: someone who isn't better than the weakest player in his own line can't help much,
            // so the slower full search is skipped for him.
            if (!betterThanWeakestIn(now, candidate)) {
                continue;
            }
            kept.add(candidate);
            double gain = Lineup.bestOfAll(kept).totalStrength() - strengthNow;
            kept.removeLast();
            if (gain >= bestGain) {
                bestGain = gain;
                bestBuy = candidate;
            }
        }

        // Selling only makes sense together with a purchase.
        return bestBuy == null ? Decision.NOTHING : new Decision(sell, bestBuy);
    }

    private static boolean betterThanWeakestIn(Lineup lineup, FootballerProfile candidate) {
        Position line = candidate.position();
        return lineup.playersIn(line).stream()
                .mapToDouble(player -> Lineup.effectiveRating(player, line))
                .anyMatch(rating -> candidate.rating() >= rating + MIN_GAIN);
    }

    // Bench players: not in the best lineup. Only they are ever sold, so the team keeps its strength.
    static List<FootballerProfile> sellableBench(List<FootballerProfile> squad) {
        List<FootballerProfile> lineup = Lineup.bestOfAll(squad).allPlayers();
        List<Position> positions = squad.stream().map(FootballerProfile::position).toList();
        return squad.stream()
                .filter(player -> !lineup.contains(player))
                .filter(player -> SquadLimits.whyCannotSell(positions, player.position()) == null)
                .toList();
    }

    private static Optional<FootballerProfile> weakestSellable(List<FootballerProfile> squad) {
        return sellableBench(squad).stream().min(Comparator.comparingInt(FootballerProfile::rating));
    }

    private static Optional<FootballerProfile> mostValuableSellable(List<FootballerProfile> squad) {
        return sellableBench(squad).stream()
                .max(Comparator.comparingLong(player -> Prices.sellPrice(player.rating(), player.age())));
    }
}
