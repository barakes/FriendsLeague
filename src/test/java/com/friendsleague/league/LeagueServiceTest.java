package com.friendsleague.league;

import com.friendsleague.IntegrationTest;
import com.friendsleague.account.Account;
import com.friendsleague.account.AccountRepository;
import com.friendsleague.game.Game;
import com.friendsleague.game.GameRepository;
import com.friendsleague.team.Footballer;
import com.friendsleague.team.FootballerRepository;
import com.friendsleague.team.Team;
import com.friendsleague.team.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LeagueServiceTest extends IntegrationTest {

    @Autowired
    private LeagueService service;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private LeagueRepository leagues;

    @Autowired
    private TeamRepository teams;

    @Autowired
    private FootballerRepository footballers;

    @Autowired
    private GameRepository games;

    @Autowired
    private TransactionTemplate transaction;

    private Account dana;
    private Account tom;

    @BeforeEach
    void createFriends() {
        dana = accounts.save(new Account("Dana", "not-a-real-hash"));
        tom = accounts.save(new Account("Tom", "not-a-real-hash"));
    }

    private League newLeague(int size) {
        return service.createLeague(dana.getId(), "ליגת החבר'ה", size, "21:00", "הפועל דנה");
    }

    // ---------- Creating a league ----------

    @Test
    void theCreatorGetsALeagueCodeAndAFullTeam() {
        League league = newLeague(8);

        assertThat(league.getJoinCode()).hasSize(6);
        assertThat(league.getStatus()).isEqualTo(LeagueStatus.WAITING);

        List<Team> leagueTeams = teams.findByLeagueIdOrderById(league.getId());
        assertThat(leagueTeams).singleElement().satisfies(team -> {
            assertThat(team.getName()).isEqualTo("הפועל דנה");
            assertThat(team.getMoney()).isEqualTo(LeagueService.STARTING_MONEY);
            assertThat(team.isComputer()).isFalse();
        });
        assertThat(footballers.findByTeamIdOrderById(leagueTeams.getFirst().getId())).hasSize(18);
    }

    @Test
    void theNewTeamAlreadyHasALineupOfItsOwnPlayers() {
        League league = newLeague(8);
        Long teamId = teams.findByLeagueIdOrderById(league.getId()).getFirst().getId();
        Set<Long> squadIds = new HashSet<>(footballers.findByTeamIdOrderById(teamId).stream().map(Footballer::getId).toList());

        transaction.executeWithoutResult(status -> {
            Team team = teams.findById(teamId).orElseThrow();
            assertThat(team.getLineup()).hasSize(11);
            assertThat(team.getLineup()).allSatisfy(slot -> assertThat(squadIds).contains(slot.getFootballerId()));
        });
    }

    @Test
    void invalidLeaguesAreRejected() {
        assertThatThrownBy(() -> service.createLeague(dana.getId(), "Test", 7, "21:00", "Team"))
                .isInstanceOf(LeagueException.class).hasMessageContaining("6, 8, 10 or 12");
        assertThatThrownBy(() -> service.createLeague(dana.getId(), "Test", 8, "25:00", "Team"))
                .isInstanceOf(LeagueException.class);
        assertThatThrownBy(() -> service.createLeague(dana.getId(), "<script>", 8, "21:00", "Team"))
                .isInstanceOf(LeagueException.class).hasMessageContaining("League name");
    }

    // ---------- Joining ----------

    @Test
    void aFriendJoinsWithTheCodeInAnyLetterCase() {
        League league = newLeague(8);

        Team team = service.joinLeague(tom.getId(), league.getJoinCode().toLowerCase(), "מכבי תום");

        assertThat(team.getName()).isEqualTo("מכבי תום");
        assertThat(teams.countByLeagueId(league.getId())).isEqualTo(2);
    }

    @Test
    void footballerNamesDoNotRepeatInsideALeague() {
        League league = newLeague(8);
        service.joinLeague(tom.getId(), league.getJoinCode(), "מכבי תום");

        List<String> names = footballers.findNamesByLeagueId(league.getId());

        assertThat(names).hasSize(36).doesNotHaveDuplicates();
    }

    @Test
    void joiningProblemsGiveClearMessages() {
        League league = newLeague(6);

        assertThatThrownBy(() -> service.joinLeague(tom.getId(), "WRONG1", "Team"))
                .isInstanceOf(LeagueException.class).hasMessageContaining("no league");
        assertThatThrownBy(() -> service.joinLeague(dana.getId(), league.getJoinCode(), "Another"))
                .isInstanceOf(LeagueException.class).hasMessageContaining("already in this league");
        assertThatThrownBy(() -> service.joinLeague(tom.getId(), league.getJoinCode(), "הפועל דנה"))
                .isInstanceOf(LeagueException.class).hasMessageContaining("name is taken");
    }

    @Test
    void aFullLeagueCannotBeJoined() {
        League league = newLeague(6);
        for (int friend = 1; friend <= 5; friend++) {
            Account account = accounts.save(new Account("Friend" + friend, "not-a-real-hash"));
            service.joinLeague(account.getId(), league.getJoinCode(), "Team " + friend);
        }

        assertThatThrownBy(() -> service.joinLeague(tom.getId(), league.getJoinCode(), "One too many"))
                .isInstanceOf(LeagueException.class).hasMessageContaining("full");
    }

    // ---------- Starting the season ----------

    @Test
    void startingTheSeasonFillsTheLeagueAndCreatesTheWholeSchedule() {
        League league = newLeague(8);
        service.joinLeague(tom.getId(), league.getJoinCode(), "מכבי תום");
        Instant before = Instant.now();

        service.startSeason(dana.getId(), league.getId());

        List<Team> leagueTeams = teams.findByLeagueIdOrderById(league.getId());
        assertThat(leagueTeams).hasSize(8);
        assertThat(leagueTeams.stream().filter(Team::isComputer)).hasSize(6);
        assertThat(leagues.findById(league.getId()).orElseThrow().getStatus()).isEqualTo(LeagueStatus.RUNNING);

        List<Game> season = games.findByLeagueIdAndSeasonOrderByRoundAscIdAsc(league.getId(), 1);
        // 8 teams: 56 games in 14 rounds, 4 games a round.
        assertThat(season).hasSize(56);
        assertThat(season.getLast().getRound()).isEqualTo(14);
        assertThat(season).allSatisfy(game -> {
            assertThat(game.isPlayed()).isFalse();
            assertThat(game.getKickoffAt()).isAfter(before.plus(KickoffSchedule.MIN_NOTICE).minusSeconds(1));
        });
    }

    @Test
    void onlyTheCreatorCanStartTheSeasonAndOnlyOnce() {
        League league = newLeague(6);
        service.joinLeague(tom.getId(), league.getJoinCode(), "מכבי תום");

        assertThatThrownBy(() -> service.startSeason(tom.getId(), league.getId()))
                .isInstanceOf(LeagueException.class).hasMessageContaining("creator");

        service.startSeason(dana.getId(), league.getId());

        assertThatThrownBy(() -> service.startSeason(dana.getId(), league.getId()))
                .isInstanceOf(LeagueException.class).hasMessageContaining("already started");
        assertThatThrownBy(() -> service.joinLeague(accounts.save(new Account("Late", "x")).getId(),
                league.getJoinCode(), "Late team"))
                .isInstanceOf(LeagueException.class).hasMessageContaining("next season");
    }
}
