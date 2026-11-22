package com.friendsleague.team;

import com.friendsleague.IntegrationTest;
import com.friendsleague.account.Account;
import com.friendsleague.account.AccountRepository;
import com.friendsleague.league.League;
import com.friendsleague.league.LeagueException;
import com.friendsleague.league.LeagueService;
import com.friendsleague.match.Tactic;
import com.friendsleague.squad.Formation;
import com.friendsleague.squad.LineupException;
import com.friendsleague.squad.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TeamServiceTest extends IntegrationTest {

    @Autowired
    private TeamService teamService;

    @Autowired
    private LeagueService leagueService;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private TeamRepository teams;

    private Account dana;
    private Account tom;
    private Long danaTeam;
    private Long tomTeam;

    @BeforeEach
    void createTeams() {
        dana = accounts.save(new Account("Dana", "not-a-real-hash"));
        tom = accounts.save(new Account("Tom", "not-a-real-hash"));
        League league = leagueService.createLeague(dana.getId(), "Test league", 6, "21:00", "הפועל דנה");
        danaTeam = teams.findByLeagueIdAndAccountId(league.getId(), dana.getId()).orElseThrow().getId();
        tomTeam = leagueService.joinLeague(tom.getId(), league.getJoinCode(), "מכבי תום").getId();
    }

    private static List<TeamService.SlotRequest> slotsOf(TeamView view) {
        List<TeamService.SlotRequest> slots = new ArrayList<>();
        for (TeamView.FootballerView footballer : view.squad()) {
            if (footballer.line() != null) {
                slots.add(new TeamService.SlotRequest(footballer.id(), footballer.line()));
            }
        }
        return slots;
    }

    @Test
    void aNewTeamHas18PlayersAnd11InTheLineup() {
        TeamView view = teamService.view(dana.getId(), danaTeam);

        assertThat(view.mine()).isTrue();
        assertThat(view.squad()).hasSize(18);
        assertThat(view.squad()).filteredOn(footballer -> footballer.line() != null).hasSize(11);
        assertThat(view.formation()).isEqualTo(Formation.F_4_4_2);
    }

    @Test
    void theBestLineupButtonFillsTheChosenFormation() {
        TeamView view = teamService.pickBestLineup(dana.getId(), danaTeam, Formation.F_4_3_3);

        Map<Position, Long> perLine = view.squad().stream().filter(footballer -> footballer.line() != null)
                .collect(Collectors.groupingBy(TeamView.FootballerView::line, Collectors.counting()));
        assertThat(view.formation()).isEqualTo(Formation.F_4_3_3);
        assertThat(perLine).containsEntry(Position.GOALKEEPER, 1L).containsEntry(Position.DEFENDER, 4L)
                .containsEntry(Position.MIDFIELDER, 3L).containsEntry(Position.FORWARD, 3L);
    }

    @Test
    void aManagerSavesHisLineupAndTactic() {
        List<TeamService.SlotRequest> slots = slotsOf(teamService.view(dana.getId(), danaTeam));

        TeamView saved = teamService.saveLineup(dana.getId(), danaTeam, Formation.F_4_4_2, Tactic.ATTACKING, slots);

        assertThat(saved.tactic()).isEqualTo(Tactic.ATTACKING);
        assertThat(teamService.view(dana.getId(), danaTeam).tactic()).isEqualTo(Tactic.ATTACKING);
    }

    @Test
    void aLineupMustFitTheFormation() {
        List<TeamService.SlotRequest> slots = slotsOf(teamService.view(dana.getId(), danaTeam));
        slots.removeLast();

        assertThatThrownBy(() -> teamService.saveLineup(dana.getId(), danaTeam, Formation.F_4_4_2, Tactic.BALANCED, slots))
                .isInstanceOf(LineupException.class).hasMessageContaining("needs");
    }

    @Test
    void aLineupCannotUseSomeoneElsesPlayer() {
        List<TeamService.SlotRequest> slots = slotsOf(teamService.view(dana.getId(), danaTeam));
        TeamView.FootballerView tomsPlayer = teamService.view(dana.getId(), tomTeam).squad().getFirst();
        slots.set(0, new TeamService.SlotRequest(tomsPlayer.id(), slots.getFirst().line()));

        assertThatThrownBy(() -> teamService.saveLineup(dana.getId(), danaTeam, Formation.F_4_4_2, Tactic.BALANCED, slots))
                .isInstanceOf(LineupException.class).hasMessageContaining("not in your squad");
    }

    @Test
    void theSameFootballerCannotPlayTwice() {
        List<TeamService.SlotRequest> slots = slotsOf(teamService.view(dana.getId(), danaTeam));
        slots.set(1, new TeamService.SlotRequest(slots.getFirst().footballerId(), slots.get(1).line()));

        assertThatThrownBy(() -> teamService.saveLineup(dana.getId(), danaTeam, Formation.F_4_4_2, Tactic.BALANCED, slots))
                .isInstanceOf(LineupException.class).hasMessageContaining("twice");
    }

    @Test
    void friendsCanLookAtATeamButOnlyItsManagerCanChangeIt() {
        TeamView asSeenByTom = teamService.view(tom.getId(), danaTeam);
        assertThat(asSeenByTom.mine()).isFalse();
        assertThat(asSeenByTom.squad()).hasSize(18);

        assertThatThrownBy(() -> teamService.pickBestLineup(tom.getId(), danaTeam, Formation.F_4_4_2))
                .isInstanceOf(LeagueException.class).hasMessageContaining("not your team");
    }
}
