package com.friendsleague.season;

import com.friendsleague.game.Game;
import com.friendsleague.game.GameRepository;
import com.friendsleague.league.League;
import com.friendsleague.league.LeagueRepository;
import com.friendsleague.league.LeagueStatus;
import com.friendsleague.league.LeagueTable;
import com.friendsleague.league.PlayedGame;
import com.friendsleague.league.TableRow;
import com.friendsleague.market.MarketGenerator;
import com.friendsleague.money.MoneyKind;
import com.friendsleague.money.SeasonPrizes;
import com.friendsleague.squad.FootballerProfile;
import com.friendsleague.squad.NameGenerator;
import com.friendsleague.squad.Position;
import com.friendsleague.team.Footballer;
import com.friendsleague.team.FootballerRepository;
import com.friendsleague.team.LineupPicker;
import com.friendsleague.team.LineupSlot;
import com.friendsleague.team.MoneyRecord;
import com.friendsleague.team.MoneyRecordRepository;
import com.friendsleague.team.Team;
import com.friendsleague.team.TeamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

// Ends a season once the money of its last game was paid:
// 1. Prize money by the final table, and the champion is remembered.
// 2. Every footballer gets a year older; the oldest retire; small squads get academy players.
// 3. The market is topped up, and the league waits for the next season.
// Points lost for debt stay until the next season starts, so the final table still shows them.
@Service
public class SeasonEnder {

    private final LeagueRepository leagues;
    private final GameRepository games;
    private final TeamRepository teams;
    private final FootballerRepository footballers;
    private final MoneyRecordRepository moneyRecords;
    private final SeasonChampionRepository champions;
    private final LineupPicker lineupPicker;
    private final Random random = new SecureRandom();

    public SeasonEnder(LeagueRepository leagues, GameRepository games, TeamRepository teams,
                       FootballerRepository footballers, MoneyRecordRepository moneyRecords,
                       SeasonChampionRepository champions, LineupPicker lineupPicker) {
        this.leagues = leagues;
        this.games = games;
        this.teams = teams;
        this.footballers = footballers;
        this.moneyRecords = moneyRecords;
        this.champions = champions;
        this.lineupPicker = lineupPicker;
    }

    // Returns true if the season ended now. Safe to call after every game: it checks first.
    @Transactional
    public boolean endIfFinished(Long leagueId) {
        League league = leagues.findByIdForUpdate(leagueId).orElseThrow();
        if (league.getStatus() != LeagueStatus.RUNNING) {
            return false;
        }
        List<Game> season = games.findByLeagueIdAndSeasonOrderByRoundAscIdAsc(leagueId, league.getSeason());
        if (season.isEmpty() || season.stream().anyMatch(game -> !game.isSettled())) {
            return false;
        }

        List<Team> leagueTeams = teams.findByLeagueIdOrderById(leagueId);
        payPrizes(league, leagueTeams, season);
        ageFootballers(league, leagueTeams);
        topUpMarket(league);
        league.endSeason();
        return true;
    }

    // ---------- 1. Prizes and the champion ----------

    private void payPrizes(League league, List<Team> leagueTeams, List<Game> season) {
        Map<Long, Integer> indexOf = new HashMap<>();
        int[] deducted = new int[leagueTeams.size()];
        for (int i = 0; i < leagueTeams.size(); i++) {
            indexOf.put(leagueTeams.get(i).getId(), i);
            deducted[i] = leagueTeams.get(i).getPointsDeducted();
        }

        List<PlayedGame> played = new ArrayList<>(season.size());
        for (Game game : season) {
            played.add(new PlayedGame(indexOf.get(game.getHomeTeam().getId()), indexOf.get(game.getAwayTeam().getId()),
                    game.getHomeGoals(), game.getAwayGoals()));
        }

        List<TableRow> table = LeagueTable.build(leagueTeams.size(), played, deducted);
        for (int position = 1; position <= table.size(); position++) {
            Team team = leagueTeams.get(table.get(position - 1).team());
            long prize = SeasonPrizes.forPosition(position, table.size());
            team.addMoney(prize);
            moneyRecords.save(new MoneyRecord(team, league.getSeason(), null, MoneyKind.SEASON_PRIZE, prize,
                    "מקום " + position));
        }

        TableRow first = table.getFirst();
        champions.save(new SeasonChampion(league, league.getSeason(), leagueTeams.get(first.team()).getName(),
                first.points()));
    }

    // ---------- 2. Older footballers, retirement and the academy ----------

    private void ageFootballers(League league, List<Team> leagueTeams) {
        Map<Long, Team> teamById = new HashMap<>();
        leagueTeams.forEach(team -> teamById.put(team.getId(), team));
        Set<Team> lostAStarter = new HashSet<>();

        for (Footballer footballer : footballers.findByLeagueIdOrderById(league.getId())) {
            FootballerProfile older = SeasonRules.olderBySeason(footballer.toProfile(), random);
            if (!SeasonRules.retires(older)) {
                footballer.growOlder(older.age(), older.rating());
                continue;
            }
            Team team = footballer.getTeam() == null ? null : teamById.get(footballer.getTeam().getId());
            if (team != null && removeFromLineup(team, footballer.getId())) {
                lostAStarter.add(team);
            }
            footballers.delete(footballer);
        }
        // Saved now, so the queries below see the squads without the retired footballers.
        footballers.flush();

        NameGenerator names = new NameGenerator(random, footballers.findNamesByLeagueId(league.getId()));
        for (Team team : leagueTeams) {
            List<Position> squad = footballers.findByTeamIdOrderById(team.getId()).stream()
                    .map(Footballer::getPosition).toList();
            for (Position position : SeasonRules.youthNeeded(squad)) {
                footballers.save(new Footballer(league, team, SeasonRules.youthPlayer(names.next(), position, random)));
            }
        }
        footballers.flush();

        // A retired starter leaves a hole: the best lineup in the same formation fills it.
        for (Team team : lostAStarter) {
            lineupPicker.pickBest(team, team.getFormation());
        }
    }

    // True if the footballer was in the team's lineup.
    private static boolean removeFromLineup(Team team, Long footballerId) {
        List<LineupSlot> kept = team.getLineup().stream()
                .filter(slot -> !slot.getFootballerId().equals(footballerId))
                .toList();
        if (kept.size() == team.getLineup().size()) {
            return false;
        }
        team.saveLineup(kept);
        return true;
    }

    // ---------- 3. The market ----------

    private void topUpMarket(League league) {
        int onMarket = footballers.findByLeagueIdAndTeamIsNullOrderByRatingDesc(league.getId()).size();
        int missing = league.getTeamCount() * MarketGenerator.PER_TEAM - onMarket;
        if (missing <= 0) {
            return;
        }
        NameGenerator names = new NameGenerator(random, footballers.findNamesByLeagueId(league.getId()));
        for (FootballerProfile profile : new MarketGenerator(random, names).more(missing)) {
            footballers.save(new Footballer(league, null, profile));
        }
    }
}
