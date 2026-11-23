package com.friendsleague.market;

import com.friendsleague.league.LeagueException;
import com.friendsleague.money.DebtRules;
import com.friendsleague.money.MoneyKind;
import com.friendsleague.money.Prices;
import com.friendsleague.team.Footballer;
import com.friendsleague.team.FootballerRepository;
import com.friendsleague.team.LineupPicker;
import com.friendsleague.team.LineupSlot;
import com.friendsleague.team.MoneyRecord;
import com.friendsleague.team.MoneyRecordRepository;
import com.friendsleague.team.Team;
import com.friendsleague.team.TeamRepository;
import com.friendsleague.team.TeamService;
import com.friendsleague.team.TeamView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// The transfer market: free agents that any team in the league can buy, and selling footballers back to it.
// A sold footballer becomes a free agent again, at the full market price.
@Service
public class MarketService {

    private final TeamRepository teams;
    private final FootballerRepository footballers;
    private final MoneyRecordRepository moneyRecords;
    private final LineupPicker lineupPicker;
    private final TeamService teamService;

    public MarketService(TeamRepository teams, FootballerRepository footballers, MoneyRecordRepository moneyRecords,
                         LineupPicker lineupPicker, TeamService teamService) {
        this.teams = teams;
        this.footballers = footballers;
        this.moneyRecords = moneyRecords;
        this.lineupPicker = lineupPicker;
        this.teamService = teamService;
    }

    // The best footballers first.
    @Transactional(readOnly = true)
    public List<MarketFootballerView> market(Long accountId, Long leagueId) {
        if (teams.findByLeagueIdAndAccountId(leagueId, accountId).isEmpty()) {
            throw new LeagueException("You are not in this league");
        }
        return footballers.findByLeagueIdAndTeamIsNullOrderByRatingDesc(leagueId).stream()
                .map(f -> new MarketFootballerView(f.getId(), f.getName(), f.getPosition(), f.getRating(), f.getAge(),
                        Prices.price(f.getRating(), f.getAge()), Prices.wage(f.getRating())))
                .toList();
    }

    // The new footballer starts on the bench; the manager decides whether he plays.
    @Transactional
    public TeamView buy(Long accountId, Long teamId, Long footballerId) {
        // Always lock the team first, then the footballer, so two purchases can never wait for each other forever.
        Team team = myTeamLocked(accountId, teamId);
        Footballer footballer = footballerLocked(footballerId);

        if (!footballer.getLeague().getId().equals(team.getLeague().getId())) {
            throw new LeagueException("This footballer is not in your league");
        }
        if (!footballer.isOnMarket()) {
            throw new LeagueException("This footballer is no longer on the market");
        }
        String limit = SquadLimits.whyCannotBuy(footballers.findByTeamIdOrderById(teamId).size());
        if (limit != null) {
            throw new LeagueException(limit);
        }
        long price = Prices.price(footballer.getRating(), footballer.getAge());
        if (!DebtRules.canBuy(team.getMoney(), price)) {
            throw new LeagueException("You don't have enough money for this footballer");
        }

        footballer.moveTo(team);
        team.addMoney(-price);
        moneyRecords.save(new MoneyRecord(team, team.getLeague().getSeason(), null, MoneyKind.PURCHASE, -price,
                footballer.getName()));
        return teamService.toView(team, accountId);
    }

    // If the sold footballer was in the lineup, the best lineup in the same formation is saved instead.
    @Transactional
    public TeamView sell(Long accountId, Long teamId, Long footballerId) {
        Team team = myTeamLocked(accountId, teamId);
        Footballer footballer = footballerLocked(footballerId);

        if (footballer.getTeam() == null || !footballer.getTeam().getId().equals(teamId)) {
            throw new LeagueException("This footballer is not in your squad");
        }
        List<Footballer> squad = footballers.findByTeamIdOrderById(teamId);
        String limit = SquadLimits.whyCannotSell(squad.stream().map(Footballer::getPosition).toList(),
                footballer.getPosition());
        if (limit != null) {
            throw new LeagueException(limit);
        }

        long price = Prices.sellPrice(footballer.getRating(), footballer.getAge());
        footballer.moveTo(null);
        team.addMoney(price);
        moneyRecords.save(new MoneyRecord(team, team.getLeague().getSeason(), null, MoneyKind.SALE, price,
                footballer.getName()));

        boolean wasInLineup = team.getLineup().stream().map(LineupSlot::getFootballerId).anyMatch(footballerId::equals);
        if (wasInLineup) {
            // Saved now, so the next query reads the squad without him.
            footballers.flush();
            lineupPicker.pickBest(team, team.getFormation());
        }
        return teamService.toView(team, accountId);
    }

    private Team myTeamLocked(Long accountId, Long teamId) {
        Team team = teams.findByIdForUpdate(teamId).orElseThrow(() -> new LeagueException("There is no such team"));
        if (team.isComputer() || !team.getAccount().getId().equals(accountId)) {
            throw new LeagueException("This is not your team");
        }
        return team;
    }

    private Footballer footballerLocked(Long footballerId) {
        if (footballerId == null) {
            throw new LeagueException("Please choose a footballer");
        }
        return footballers.findByIdForUpdate(footballerId)
                .orElseThrow(() -> new LeagueException("There is no such footballer"));
    }
}
