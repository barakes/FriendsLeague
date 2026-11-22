package com.friendsleague.game;

import com.friendsleague.match.Side;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

// A saved goal. It has no id of its own: it lives inside a Game (the "game_goals" table).
@Embeddable
public class GameGoal {

    @Column(nullable = false)
    private int minute;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 4)
    private Side side;

    // The scorer's name as text, so old games still show it after the footballer retires.
    @Column(nullable = false, length = 40)
    private String scorer;

    // For JPA only.
    protected GameGoal() {
    }

    public GameGoal(int minute, Side side, String scorer) {
        this.minute = minute;
        this.side = side;
        this.scorer = scorer;
    }

    public int getMinute() {
        return minute;
    }

    public Side getSide() {
        return side;
    }

    public String getScorer() {
        return scorer;
    }
}
