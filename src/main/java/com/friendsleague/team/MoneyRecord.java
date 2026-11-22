package com.friendsleague.team;

import com.friendsleague.money.MoneyKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

// One line in a team's account history: a payment in (positive) or out (negative).
// One row in the "money_entries" table.
@Entity
@Table(name = "money_entries")
public class MoneyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Column(nullable = false)
    private int season;

    // Null for payments that don't belong to a round, such as buying a footballer.
    private Integer round;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MoneyKind kind;

    @Column(nullable = false)
    private long amount;

    // Extra detail for the history page, such as the name of a footballer who was bought.
    @Column(length = 60)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // For JPA only.
    protected MoneyRecord() {
    }

    public MoneyRecord(Team team, int season, Integer round, MoneyKind kind, long amount, String note) {
        this.team = team;
        this.season = season;
        this.round = round;
        this.kind = kind;
        this.amount = amount;
        this.note = note;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Team getTeam() {
        return team;
    }

    public int getSeason() {
        return season;
    }

    public Integer getRound() {
        return round;
    }

    public MoneyKind getKind() {
        return kind;
    }

    public long getAmount() {
        return amount;
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
