package com.friendsleague.team;

import com.friendsleague.squad.Position;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.util.Objects;

// One row of a saved lineup: which footballer plays in which line.
// @Embeddable: it has no id of its own; it lives inside a Team (the "lineup_slots" table).
@Embeddable
public class LineupSlot {

    @Column(name = "footballer_id", nullable = false)
    private Long footballerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Position line;

    // For JPA only.
    protected LineupSlot() {
    }

    public LineupSlot(Long footballerId, Position line) {
        this.footballerId = footballerId;
        this.line = line;
    }

    public Long getFootballerId() {
        return footballerId;
    }

    public Position getLine() {
        return line;
    }

    // Needed because the slots are kept in a Set: two slots are the same if they have the same footballer and line.
    @Override
    public boolean equals(Object other) {
        return other instanceof LineupSlot slot
                && Objects.equals(footballerId, slot.footballerId) && line == slot.line;
    }

    @Override
    public int hashCode() {
        return Objects.hash(footballerId, line);
    }
}
