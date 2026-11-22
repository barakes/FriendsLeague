package com.friendsleague.squad;

// A footballer as the generator creates him: name, position, strength (1-99) and age.
// Plain data, no database: the database entity is built from it later.
public record FootballerProfile(String name, Position position, int rating, int age) {

    public static final int MIN_RATING = 1;
    public static final int MAX_RATING = 99;

    public FootballerProfile {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A footballer needs a name");
        }
        if (position == null) {
            throw new IllegalArgumentException("A footballer needs a position");
        }
        if (rating < MIN_RATING || rating > MAX_RATING) {
            throw new IllegalArgumentException("Rating must be between 1 and 99, was " + rating);
        }
        if (age < 15 || age > 45) {
            throw new IllegalArgumentException("Unrealistic age: " + age);
        }
    }
}