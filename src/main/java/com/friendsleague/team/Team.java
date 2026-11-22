package com.friendsleague.team;

import com.friendsleague.account.Account;
import com.friendsleague.league.League;
import com.friendsleague.match.Tactic;
import com.friendsleague.squad.Formation;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

// A team in a league. A computer team has no account. One row in the "teams" table.
@Entity
@Table(name = "teams")
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "league_id", nullable = false)
    private League league;

    // Null for a computer team.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private Account account;

    @Column(nullable = false, length = 30)
    private String name;

    // Shekels, as a whole number.
    @Column(nullable = false)
    private long money;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Formation formation = Formation.F_4_4_2;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Tactic tactic = Tactic.BALANCED;

    // The saved lineup, in the "lineup_slots" table.
    @ElementCollection
    @CollectionTable(name = "lineup_slots", joinColumns = @JoinColumn(name = "team_id"))
    private Set<LineupSlot> lineup = new HashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // For JPA only.
    protected Team() {
    }

    public Team(League league, Account account, String name, long money) {
        this.league = league;
        this.account = account;
        this.name = name;
        this.money = money;
        this.createdAt = Instant.now();
    }

    public boolean isComputer() {
        return account == null;
    }

    // A friend takes over a computer team (when he joins between seasons).
    public void assignTo(Account newManager) {
        this.account = newManager;
    }

    public void changeTactics(Formation newFormation, Tactic newTactic) {
        this.formation = newFormation;
        this.tactic = newTactic;
    }

    public void saveLineup(Collection<LineupSlot> slots) {
        lineup.clear();
        lineup.addAll(slots);
    }

    public void addMoney(long amount) {
        money += amount;
    }

    public Long getId() {
        return id;
    }

    public League getLeague() {
        return league;
    }

    public Account getAccount() {
        return account;
    }

    public String getName() {
        return name;
    }

    public long getMoney() {
        return money;
    }

    public Formation getFormation() {
        return formation;
    }

    public Tactic getTactic() {
        return tactic;
    }

    public Set<LineupSlot> getLineup() {
        return Set.copyOf(lineup);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
