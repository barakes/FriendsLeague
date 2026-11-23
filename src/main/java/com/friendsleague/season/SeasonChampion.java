package com.friendsleague.season;

import com.friendsleague.league.League;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

// The champion of one finished season. One row in the "season_champions" table.
@Entity
@Table(name = "season_champions")
public class SeasonChampion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "league_id", nullable = false)
    private League league;

    @Column(nullable = false)
    private int season;

    @Column(name = "team_name", nullable = false, length = 30)
    private String teamName;

    @Column(nullable = false)
    private int points;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // For JPA only.
    protected SeasonChampion() {
    }

    public SeasonChampion(League league, int season, String teamName, int points) {
        this.league = league;
        this.season = season;
        this.teamName = teamName;
        this.points = points;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public int getSeason() {
        return season;
    }

    public String getTeamName() {
        return teamName;
    }

    public int getPoints() {
        return points;
    }
}
