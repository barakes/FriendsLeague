package com.friendsleague.match;

// How a team plays. Attacking: more goals scored, but more conceded. Defensive: the other way around.
public enum Tactic {
    ATTACKING(1.12, 0.90),
    BALANCED(1.0, 1.0),
    DEFENSIVE(0.90, 1.12);

    private final double attack;
    private final double defense;

    Tactic(double attack, double defense) {
        this.attack = attack;
        this.defense = defense;
    }

    public double attack() {
        return attack;
    }

    public double defense() {
        return defense;
    }
}