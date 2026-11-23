package com.friendsleague.market;

import com.friendsleague.squad.Position;

import java.util.Collection;

// How big a squad may get by buying and selling. A squad starts with 18.
public final class SquadLimits {

    public static final int MIN_SQUAD = 16;
    public static final int MAX_SQUAD = 22;
    // A team must always keep a real goalkeeper.
    public static final int MIN_GOALKEEPERS = 1;

    private SquadLimits() {
    }

    // Null if the purchase is allowed, otherwise the reason for the player.
    public static String whyCannotBuy(int squadSize) {
        if (squadSize >= MAX_SQUAD) {
            return "A squad can have at most " + MAX_SQUAD + " footballers";
        }
        return null;
    }

    // positions: the positions of the whole squad before the sale. sold: the position of the one being sold.
    public static String whyCannotSell(Collection<Position> positions, Position sold) {
        if (positions.size() <= MIN_SQUAD) {
            return "A squad needs at least " + MIN_SQUAD + " footballers";
        }
        long goalkeepers = positions.stream().filter(position -> position == Position.GOALKEEPER).count();
        if (sold == Position.GOALKEEPER && goalkeepers <= MIN_GOALKEEPERS) {
            return "You can't sell your last goalkeeper";
        }
        return null;
    }
}
