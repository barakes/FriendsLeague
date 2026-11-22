package com.friendsleague.team;

import com.friendsleague.IntegrationTest;
import com.friendsleague.account.Account;
import com.friendsleague.account.AccountRepository;
import com.friendsleague.league.League;
import com.friendsleague.league.LeagueRepository;
import com.friendsleague.league.LeagueStatus;
import com.friendsleague.match.Tactic;
import com.friendsleague.squad.FootballerProfile;
import com.friendsleague.squad.Formation;
import com.friendsleague.squad.Position;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Saves leagues, teams and footballers to a real database and reads them back.
class PersistenceTest extends IntegrationTest {

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private LeagueRepository leagues;

    @Autowired
    private TeamRepository teams;

    @Autowired
    private FootballerRepository footballers;

    @Autowired
    private TransactionTemplate transaction;

    private League newLeague() {
        Account owner = accounts.save(new Account("Dana", "not-a-real-hash"));
        return leagues.save(new League("ליגת החבר'ה", "ABC123", 8, owner, "21:00"));
    }

    @Test
    void aLeagueIsSavedAndFoundByItsCode() {
        League league = newLeague();

        League found = leagues.findByJoinCode("ABC123").orElseThrow();

        assertThat(found.getId()).isEqualTo(league.getId());
        assertThat(found.getTeamCount()).isEqualTo(8);
        assertThat(found.getSeason()).isEqualTo(1);
        assertThat(found.getStatus()).isEqualTo(LeagueStatus.WAITING);
    }

    @Test
    void aTeamWithFootballersAndALineupIsSavedAndReadBack() {
        League league = newLeague();
        Team team = teams.save(new Team(league, league.getOwner(), "הפועל דנה", 10_000_000));
        Footballer striker = footballers.save(new Footballer(league, team,
                new FootballerProfile("יוסי כהן", Position.FORWARD, 75, 24)));
        footballers.save(new Footballer(league, null, new FootballerProfile("אבי לוי", Position.DEFENDER, 60, 30)));

        team.changeTactics(Formation.F_4_3_3, Tactic.ATTACKING);
        team.saveLineup(List.of(new LineupSlot(striker.getId(), Position.FORWARD)));
        teams.save(team);

        // Read everything again, as a new request would.
        transaction.executeWithoutResult(status -> {
            Team loaded = teams.findById(team.getId()).orElseThrow();
            assertThat(loaded.getFormation()).isEqualTo(Formation.F_4_3_3);
            assertThat(loaded.getTactic()).isEqualTo(Tactic.ATTACKING);
            assertThat(loaded.getMoney()).isEqualTo(10_000_000);
            assertThat(loaded.isComputer()).isFalse();
            assertThat(loaded.getLineup()).containsExactly(new LineupSlot(striker.getId(), Position.FORWARD));
        });

        assertThat(footballers.findByTeamIdOrderById(team.getId()))
                .singleElement().extracting(Footballer::getName).isEqualTo("יוסי כהן");
        assertThat(footballers.findByLeagueIdAndTeamIsNullOrderByRatingDesc(league.getId()))
                .singleElement().satisfies(free -> assertThat(free.isOnMarket()).isTrue());
    }

    @Test
    void aComputerTeamHasNoAccount() {
        League league = newLeague();

        Team computer = teams.save(new Team(league, null, "מכבי מחשב", 10_000_000));

        assertThat(computer.isComputer()).isTrue();
        assertThat(teams.countByLeagueId(league.getId())).isEqualTo(1);
    }

    @Test
    void twoTeamsInALeagueCannotHaveTheSameName() {
        League league = newLeague();
        teams.save(new Team(league, null, "Maccabi", 10_000_000));

        // The unique index ignores letter case.
        assertThatThrownBy(() -> teams.saveAndFlush(new Team(league, null, "MACCABI", 10_000_000)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aFriendHasOnlyOneTeamInALeague() {
        League league = newLeague();
        teams.save(new Team(league, league.getOwner(), "First", 10_000_000));

        assertThatThrownBy(() -> teams.saveAndFlush(new Team(league, league.getOwner(), "Second", 10_000_000)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
