package com.friendsleague.money;

// What happens to a team whose money is below zero after a round.
// - It cannot buy footballers.
// - It has 3 rounds to get back above zero.
// - If it is still in debt after 3 rounds in a row, it loses 3 points, and again every 3 rounds after that.
// Footballers are never sold automatically: the manager decides what to do.
public final class DebtRules {

    public static final int ROUNDS_TO_RECOVER = 3;
    public static final int POINTS_DEDUCTED = 3;

    private DebtRules() {
    }

    public static boolean canBuy(long money, long price) {
        return money >= price;
    }

    // roundsInDebt: how many rounds in a row (including this one) the team ended in debt.
    public static int pointsToDeduct(int roundsInDebt) {
        if (roundsInDebt > 0 && roundsInDebt % ROUNDS_TO_RECOVER == 0) {
            return POINTS_DEDUCTED;
        }
        return 0;
    }
}
