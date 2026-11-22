package com.friendsleague.money;

// What a footballer costs on the market, and his wage.
// Every rating point makes a footballer about 7% more expensive, so stars are much more expensive than average players.
public final class Prices {

    // A footballer with this rating, aged 29 or younger, costs BASE_PRICE.
    static final int BASE_RATING = 60;
    static final long BASE_PRICE = 1_000_000;
    static final double GROWTH_PER_POINT = 1.07;

    // From this age the price goes down by 10% a year, but never below 30% of the full price.
    static final int PRICE_DROP_AGE = 30;
    static final double DROP_PER_YEAR = 0.10;
    static final double LOWEST_AGE_FACTOR = 0.30;

    // Selling to the market returns only part of the price, so buying and selling again never pays.
    static final double SELL_SHARE = 0.70;

    // Wage per round = rating² × WAGE_FACTOR: rating 60 earns 18,000, rating 80 earns 32,000.
    static final int WAGE_FACTOR = 5;

    private Prices() {
    }

    public static long price(int rating, int age) {
        double price = BASE_PRICE * Math.pow(GROWTH_PER_POINT, rating - BASE_RATING) * ageFactor(age);
        return roundTo(price, 10_000);
    }

    public static long sellPrice(int rating, int age) {
        return roundTo(price(rating, age) * SELL_SHARE, 10_000);
    }

    public static long wage(int rating) {
        return (long) rating * rating * WAGE_FACTOR;
    }

    static double ageFactor(int age) {
        if (age < PRICE_DROP_AGE) {
            return 1.0;
        }
        return Math.max(LOWEST_AGE_FACTOR, 1.0 - DROP_PER_YEAR * (age - PRICE_DROP_AGE + 1));
    }

    private static long roundTo(double amount, long step) {
        return Math.max(step, Math.round(amount / step) * step);
    }
}
