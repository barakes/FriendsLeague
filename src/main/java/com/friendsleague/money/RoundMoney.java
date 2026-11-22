package com.friendsleague.money;

import com.friendsleague.match.Side;

import java.util.ArrayList;
import java.util.List;

// The money a team gets and pays for one round.
public final class RoundMoney {

    public static final long TV_RIGHTS = 150_000;
    public static final long HOME_TICKETS = 200_000;
    public static final long WIN_BONUS = 100_000;
    public static final long DRAW_BONUS = 40_000;

    private RoundMoney() {
    }

    // side: where the team played. winner: Side.HOME or Side.AWAY, or null for a draw.
    // wages: the whole squad's wages for the round.
    public static List<MoneyEntry> settle(Side side, Side winner, Sponsor sponsor, long wages) {
        List<MoneyEntry> entries = new ArrayList<>();
        entries.add(new MoneyEntry(MoneyKind.TV_RIGHTS, TV_RIGHTS));
        if (side == Side.HOME) {
            entries.add(new MoneyEntry(MoneyKind.TICKETS, HOME_TICKETS));
        }

        boolean won = winner == side;
        if (won) {
            entries.add(new MoneyEntry(MoneyKind.WIN_BONUS, WIN_BONUS));
        } else if (winner == null) {
            entries.add(new MoneyEntry(MoneyKind.DRAW_BONUS, DRAW_BONUS));
        }

        entries.add(new MoneyEntry(MoneyKind.SPONSOR, sponsor.payment(won)));
        entries.add(new MoneyEntry(MoneyKind.WAGES, -wages));
        return entries;
    }

    public static long total(List<MoneyEntry> entries) {
        return entries.stream().mapToLong(MoneyEntry::amount).sum();
    }
}
