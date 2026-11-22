package com.friendsleague.league;

import com.friendsleague.account.Account;
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

// A private league of friends. One row in the "leagues" table.
@Entity
@Table(name = "leagues")
public class League {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String name;

    @Column(name = "join_code", nullable = false, length = 8)
    private String joinCode;

    // "size" is also a word in the query language, so the Java field has a clearer name.
    @Column(name = "size", nullable = false)
    private int teamCount;

    // LAZY: the owner is loaded from the database only when someone asks for him.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_account_id", nullable = false)
    private Account owner;

    @Column(nullable = false)
    private int season = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LeagueStatus status = LeagueStatus.WAITING;

    // Israel time, for example "21:00" or "13:00,21:00".
    @Column(name = "kickoff_times", nullable = false, length = 60)
    private String kickoffTimes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // For JPA only.
    protected League() {
    }

    public League(String name, String joinCode, int teamCount, Account owner, String kickoffTimes) {
        this.name = name;
        this.joinCode = joinCode;
        this.teamCount = teamCount;
        this.owner = owner;
        this.kickoffTimes = kickoffTimes;
        this.createdAt = Instant.now();
    }

    public void start() {
        status = LeagueStatus.RUNNING;
    }

    public void endSeason() {
        status = LeagueStatus.BETWEEN_SEASONS;
    }

    public void startNextSeason() {
        season++;
        status = LeagueStatus.RUNNING;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getJoinCode() {
        return joinCode;
    }

    public int getTeamCount() {
        return teamCount;
    }

    public Account getOwner() {
        return owner;
    }

    public int getSeason() {
        return season;
    }

    public LeagueStatus getStatus() {
        return status;
    }

    public String getKickoffTimes() {
        return kickoffTimes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
