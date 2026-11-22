package com.friendsleague.game;

import com.friendsleague.league.League;
import com.friendsleague.match.Goal;
import com.friendsleague.match.LiveClock;
import com.friendsleague.match.MatchResult;
import com.friendsleague.match.Side;
import com.friendsleague.team.Team;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

// One game of the season. One row in the "games" table; its goals are in "game_goals".
@Entity
@Table(name = "games")
public class Game {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "league_id", nullable = false)
    private League league;

    @Column(nullable = false)
    private int season;

    @Column(nullable = false)
    private int round;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "home_team_id", nullable = false)
    private Team homeTeam;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "away_team_id", nullable = false)
    private Team awayTeam;

    @Column(name = "kickoff_at", nullable = false)
    private Instant kickoffAt;

    // Null until the game is played.
    @Column(name = "home_goals")
    private Integer homeGoals;

    @Column(name = "away_goals")
    private Integer awayGoals;

    // True after the money of this game was paid (when its live broadcast ended).
    @Column(nullable = false)
    private boolean settled;

    // @OrderColumn keeps the goals in the order they were saved, in the "seq" column.
    @ElementCollection
    @CollectionTable(name = "game_goals", joinColumns = @JoinColumn(name = "game_id"))
    @OrderColumn(name = "seq")
    private List<GameGoal> goals = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // For JPA only.
    protected Game() {
    }

    public Game(League league, int season, int round, Team homeTeam, Team awayTeam, Instant kickoffAt) {
        this.league = league;
        this.season = season;
        this.round = round;
        this.homeTeam = homeTeam;
        this.awayTeam = awayTeam;
        this.kickoffAt = kickoffAt;
        this.createdAt = Instant.now();
    }

    public boolean isPlayed() {
        return homeGoals != null;
    }

    // Saves the whole result, worked out by the match engine at kick-off.
    public void recordResult(MatchResult result) {
        if (isPlayed()) {
            throw new IllegalStateException("Game " + id + " was already played");
        }
        homeGoals = result.homeGoals();
        awayGoals = result.awayGoals();
        goals.clear();
        for (Goal goal : result.goals()) {
            goals.add(new GameGoal(goal.minute(), goal.side(), goal.scorer()));
        }
    }

    // Side.HOME or Side.AWAY, or null for a draw. Only for a game that was played.
    public Side winner() {
        if (!isPlayed()) {
            throw new IllegalStateException("Game " + id + " was not played yet");
        }
        if (homeGoals > awayGoals) {
            return Side.HOME;
        }
        return awayGoals > homeGoals ? Side.AWAY : null;
    }

    public boolean isSettled() {
        return settled;
    }

    public void markSettled() {
        settled = true;
    }

    // ---------- The live view: only what has "happened" by now ----------

    public int minuteAt(Instant now) {
        return LiveClock.minuteAt(kickoffAt, now);
    }

    // Goals that may be shown at this moment. Later goals are never sent to the browser before their time.
    public List<GameGoal> goalsUntil(Instant now) {
        if (!isPlayed()) {
            return List.of();
        }
        int minute = minuteAt(now);
        return goals.stream().filter(goal -> goal.getMinute() <= minute).toList();
    }

    public int scoreAt(Side side, Instant now) {
        return (int) goalsUntil(now).stream().filter(goal -> goal.getSide() == side).count();
    }

    public boolean isOverAt(Instant now) {
        return isPlayed() && LiveClock.isOver(kickoffAt, now);
    }

    public Long getId() {
        return id;
    }

    public League getLeague() {
        return league;
    }

    public int getSeason() {
        return season;
    }

    public int getRound() {
        return round;
    }

    public Team getHomeTeam() {
        return homeTeam;
    }

    public Team getAwayTeam() {
        return awayTeam;
    }

    public Instant getKickoffAt() {
        return kickoffAt;
    }

    // The final score: only for games that are over. The live view uses scoreAt instead.
    public Integer getHomeGoals() {
        return homeGoals;
    }

    public Integer getAwayGoals() {
        return awayGoals;
    }

    public List<GameGoal> getGoals() {
        return List.copyOf(goals);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
