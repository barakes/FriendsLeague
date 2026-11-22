package com.friendsleague.squad;

// The formations a manager can choose from. Every formation has one goalkeeper and 10 outfield players.
public enum Formation {
    F_4_4_2("4-4-2", 4, 4, 2),
    F_4_3_3("4-3-3", 4, 3, 3),
    F_3_5_2("3-5-2", 3, 5, 2),
    F_5_3_2("5-3-2", 5, 3, 2),
    F_4_5_1("4-5-1", 4, 5, 1);

    private final String label;
    private final int defenders;
    private final int midfielders;
    private final int forwards;

    Formation(String label, int defenders, int midfielders, int forwards) {
        this.label = label;
        this.defenders = defenders;
        this.midfielders = midfielders;
        this.forwards = forwards;
    }

    public String label() {
        return label;
    }

    // How many players of this position the formation puts on the pitch.
    public int countFor(Position position) {
        return switch (position) {
            case GOALKEEPER -> 1;
            case DEFENDER -> defenders;
            case MIDFIELDER -> midfielders;
            case FORWARD -> forwards;
        };
    }
}