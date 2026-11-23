package com.friendsleague.team;

import com.friendsleague.league.LeagueException;
import com.friendsleague.league.LeagueStatus;
import com.friendsleague.match.Tactic;
import com.friendsleague.money.Prices;
import com.friendsleague.money.Sponsor;
import com.friendsleague.squad.FootballerProfile;
import com.friendsleague.squad.Formation;
import com.friendsleague.squad.Lineup;
import com.friendsleague.squad.LineupException;
import com.friendsleague.squad.Position;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Viewing a team, changing its lineup, choosing a sponsor and reading the account history.
@Service
public class TeamService {

    public record SlotRequest(Long footballerId, Position line) {
    }

    private final TeamRepository teams;
    private final FootballerRepository footballers;
    private final MoneyRecordRepository moneyRecords;
    private final LineupPicker lineupPicker;

    public TeamService(TeamRepository teams, FootballerRepository footballers, MoneyRecordRepository moneyRecords,
                       LineupPicker lineupPicker) {
        this.teams = teams;
        this.footballers = footballers;
        this.moneyRecords = moneyRecords;
        this.lineupPicker = lineupPicker;
    }

    // Anyone in the league can look at a team (to scout the next opponent).
    @Transactional(readOnly = true)
    public TeamView view(Long accountId, Long teamId) {
        Team team = findTeam(teamId);
        if (teams.findByLeagueIdAndAccountId(team.getLeague().getId(), accountId).isEmpty()) {
            throw new LeagueException("You are not in this league");
        }
        return toView(team, accountId);
    }

    // The manager saves his own lineup: every footballer must be his, and the lines must fit the formation.
    @Transactional
    public TeamView saveLineup(Long accountId, Long teamId, Formation formation, Tactic tactic, List<SlotRequest> slots) {
        Team team = myTeam(accountId, teamId);
        if (tactic == null) {
            throw new LeagueException("Please choose a tactic");
        }

        Map<Long, Footballer> squad = new HashMap<>();
        footballers.findByTeamIdOrderById(teamId).forEach(footballer -> squad.put(footballer.getId(), footballer));

        Map<Position, List<FootballerProfile>> lines = new EnumMap<>(Position.class);
        for (Position line : Position.values()) {
            lines.put(line, new ArrayList<>());
        }
        List<LineupSlot> saved = new ArrayList<>();
        Set<Long> used = new HashSet<>();
        for (SlotRequest slot : slots == null ? List.<SlotRequest>of() : slots) {
            Footballer footballer = squad.get(slot.footballerId());
            if (footballer == null) {
                throw new LineupException("That footballer is not in your squad");
            }
            if (!used.add(footballer.getId())) {
                throw new LineupException(footballer.getName() + " appears twice in the lineup");
            }
            if (slot.line() == null) {
                throw new LineupException("Every player in the lineup needs a line");
            }
            lines.get(slot.line()).add(footballer.toProfile());
            saved.add(new LineupSlot(footballer.getId(), slot.line()));
        }

        // Checks the counts per line and duplicates; throws a LineupException with a message for the player.
        Lineup.of(formation, lines);

        team.changeTactics(formation, tactic);
        team.saveLineup(saved);
        return toView(team, accountId);
    }

    // The "pick my lineup" button: the strongest lineup in the chosen formation.
    @Transactional
    public TeamView pickBestLineup(Long accountId, Long teamId, Formation formation) {
        Team team = myTeam(accountId, teamId);
        if (formation == null) {
            throw new LineupException("Please choose a formation");
        }
        lineupPicker.pickBest(team, formation);
        return toView(team, accountId);
    }

    // The sponsor deal is for a whole season, so it can only be changed before the season starts.
    @Transactional
    public TeamView chooseSponsor(Long accountId, Long teamId, Sponsor sponsor) {
        Team team = myTeam(accountId, teamId);
        if (sponsor == null) {
            throw new LeagueException("Please choose a sponsor");
        }
        if (team.getLeague().getStatus() == LeagueStatus.RUNNING) {
            throw new LeagueException("The sponsor can only be changed between seasons");
        }
        team.chooseSponsor(sponsor);
        return toView(team, accountId);
    }

    // Only the manager sees his own account history.
    @Transactional(readOnly = true)
    public List<MoneyRecordView> moneyHistory(Long accountId, Long teamId) {
        myTeam(accountId, teamId);
        return moneyRecords.findByTeamIdOrderByIdDesc(teamId).stream()
                .map(r -> new MoneyRecordView(r.getSeason(), r.getRound(), r.getKind(), r.getAmount(), r.getNote(),
                        r.getCreatedAt()))
                .toList();
    }

    private Team myTeam(Long accountId, Long teamId) {
        Team team = findTeam(teamId);
        if (team.isComputer() || !team.getAccount().getId().equals(accountId)) {
            throw new LeagueException("This is not your team");
        }
        return team;
    }

    private Team findTeam(Long teamId) {
        return teams.findById(teamId).orElseThrow(() -> new LeagueException("There is no such team"));
    }

    // Builds the view of a team that is already loaded. No transaction of its own: the caller's transaction is used.
    public TeamView toView(Team team, Long accountId) {
        Map<Long, Position> lineFor = new HashMap<>();
        team.getLineup().forEach(slot -> lineFor.put(slot.getFootballerId(), slot.getLine()));

        List<TeamView.FootballerView> squad = footballers.findByTeamIdOrderById(team.getId()).stream()
                .map(f -> new TeamView.FootballerView(f.getId(), f.getName(), f.getPosition(), f.getRating(),
                        f.getAge(), lineFor.get(f.getId()), Prices.wage(f.getRating()),
                        Prices.sellPrice(f.getRating(), f.getAge())))
                .toList();
        long wages = squad.stream().mapToLong(TeamView.FootballerView::wage).sum();

        boolean mine = !team.isComputer() && team.getAccount().getId().equals(accountId);
        return new TeamView(team.getId(), team.getLeague().getId(), team.getName(), team.isComputer(), mine,
                team.getMoney(), team.getSponsor(), team.getRoundsInDebt(), wages,
                team.getFormation(), team.getTactic(), squad);
    }
}
