package com.friendsleague.team;

import com.friendsleague.league.League;
import com.friendsleague.squad.FootballerProfile;
import com.friendsleague.squad.Position;
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

// A made-up footballer, saved in the database. A footballer with no team is on the transfer market.
@Entity
@Table(name = "footballers")
public class Footballer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "league_id", nullable = false)
    private League league;

    // Null when the footballer is on the market.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private Team team;

    @Column(nullable = false, length = 40)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Position position;

    @Column(nullable = false)
    private int rating;

    @Column(nullable = false)
    private int age;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // For JPA only.
    protected Footballer() {
    }

    // Saves a footballer that the generator made up.
    public Footballer(League league, Team team, FootballerProfile profile) {
        this.league = league;
        this.team = team;
        this.name = profile.name();
        this.position = profile.position();
        this.rating = profile.rating();
        this.age = profile.age();
        this.createdAt = Instant.now();
    }

    // The plain-data view that the lineup and match code work with.
    public FootballerProfile toProfile() {
        return new FootballerProfile(name, position, rating, age);
    }

    public boolean isOnMarket() {
        return team == null;
    }

    public void moveTo(Team newTeam) {
        this.team = newTeam;
    }

    // Between seasons: a year older, with the new rating.
    public void growOlder(int newAge, int newRating) {
        this.age = newAge;
        this.rating = newRating;
    }

    public Long getId() {
        return id;
    }

    public League getLeague() {
        return league;
    }

    public Team getTeam() {
        return team;
    }

    public String getName() {
        return name;
    }

    public Position getPosition() {
        return position;
    }

    public int getRating() {
        return rating;
    }

    public int getAge() {
        return age;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
