package com.friendsleague.money;

// The prize money at the end of a season, by the final position in the table.
// The champion gets FIRST_PRIZE, the last team LAST_PRIZE, and the places between go down in equal steps.
public final class SeasonPrizes {

    public static final long FIRST_PRIZE = 3_000_000;
    public static final long LAST_PRIZE = 300_000;

    private SeasonPrizes() {
    }

    // position: 1 for the champion, teams for the last place.
    public static long forPosition(int position, int teams) {
        if (teams < 2 || position < 1 || position > teams) {
            throw new IllegalArgumentException("No position " + position + " in a league of " + teams);
        }
        double step = (double) (FIRST_PRIZE - LAST_PRIZE) / (teams - 1);
        long prize = Math.round(FIRST_PRIZE - step * (position - 1));
        return Math.round(prize / 10_000.0) * 10_000;
    }
}
