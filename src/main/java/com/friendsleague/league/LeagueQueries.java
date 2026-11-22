package com.friendsleague.league;

import com.friendsleague.game.Game;
import com.friendsleague.game.GameGoal;
import com.friendsleague.game.GameRepository;
import com.friendsleague.match.Side;
import com.friendsleague.team.Team;
import com.friendsleague.team.TeamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Reading leagues for the browser. readOnly: no changes are saved, so the database can work a little faster.
// Everything is turned into simple records inside the transaction, so nothing "lazy" is touched after it ends.
@Service
@Transactional(readOnly = true)
public class LeagueQueries {

    private final TeamRepository teams;
    private final GameRepository games;

    public LeagueQueries(TeamRepository teams, GameRepository games) {
        this.teams = teams;
        this.games = games;
    }

    public List<LeagueSummary> myLeagues(Long accountId) {
        return teams.findByAccountIdOrderById(accountId).stream().map(team -> summary(team, accountId)).toList();
    }

    public LeagueSummary summary(Long accountId, Long leagueId) {
        return summary(myTeam(accountId, leagueId), accountId);
    }

    public LeagueDetails details(Long accountId, Long leagueId, Instant now) {
        Team myTeam = myTeam(accountId, leagueId);
        League league = myTeam.getLeague();
        List<Game> season = games.findByLeagueIdAndSeasonOrderByRoundAscIdAsc(leagueId, league.getSeason());

        int totalRounds = season.isEmpty() ? 0 : season.getLast().getRound();
        // The current round: the first one that still has a game that isn't over. After the last game, the last round.
        int currentRound = season.stream().filter(game -> !game.isOverAt(now)).mapToInt(Game::getRound)
                .min().orElse(totalRounds);

        return new LeagueDetails(summary(myTeam, accountId), currentRound, totalRounds, table(league, myTeam, season, now));
    }

    public List<GameView> round(Long accountId, Long leagueId, int round, Instant now) {
        League league = myTeam(accountId, leagueId).getLeague();
        return games.findByLeagueIdAndSeasonAndRoundOrderById(leagueId, league.getSeason(), round).stream()
                .map(game -> view(game, now))
                .toList();
    }

    // Only games that are over count, so the table never gives away a result before the live broadcast ends.
    private List<TableEntry> table(League league, Team myTeam, List<Game> season, Instant now) {
        List<Team> leagueTeams = teams.findByLeagueIdOrderById(league.getId());
        Map<Long, Integer> number = new HashMap<>();
        for (int i = 0; i < leagueTeams.size(); i++) {
            number.put(leagueTeams.get(i).getId(), i);
        }

        List<PlayedGame> finished = new ArrayList<>();
        for (Game game : season) {
            if (game.isOverAt(now)) {
                finished.add(new PlayedGame(number.get(game.getHomeTeam().getId()), number.get(game.getAwayTeam().getId()),
                        game.getHomeGoals(), game.getAwayGoals()));
            }
        }

        List<TableRow> rows = LeagueTable.build(leagueTeams.size(), finished);
        List<TableEntry> table = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            TableRow row = rows.get(i);
            Team team = leagueTeams.get(row.team());
            table.add(new TableEntry(i + 1, team.getId(), team.getName(), team.isComputer(),
                    team.getId().equals(myTeam.getId()), row.played(), row.wins(), row.draws(), row.losses(),
                    row.goalsFor(), row.goalsAgainst(), row.goalDifference(), row.points()));
        }
        return table;
    }

    private static GameView view(Game game, Instant now) {
        GameView.TeamRef home = new GameView.TeamRef(game.getHomeTeam().getId(), game.getHomeTeam().getName());
        GameView.TeamRef away = new GameView.TeamRef(game.getAwayTeam().getId(), game.getAwayTeam().getName());

        // Not played yet (before kick-off, or the server is about to play it): no score at all.
        if (!game.isPlayed() || now.isBefore(game.getKickoffAt())) {
            return new GameView(game.getId(), game.getRound(), game.getKickoffAt(), GameView.Status.SCHEDULED, 0,
                    home, away, null, null, List.of());
        }

        List<GameView.GoalView> goals = new ArrayList<>();
        for (GameGoal goal : game.goalsUntil(now)) {
            goals.add(new GameView.GoalView(goal.getMinute(), goal.getSide(), goal.getScorer()));
        }
        GameView.Status status = game.isOverAt(now) ? GameView.Status.FINISHED : GameView.Status.LIVE;
        return new GameView(game.getId(), game.getRound(), game.getKickoffAt(), status, game.minuteAt(now),
                home, away, game.scoreAt(Side.HOME, now), game.scoreAt(Side.AWAY, now), goals);
    }

    private Team myTeam(Long accountId, Long leagueId) {
        return teams.findByLeagueIdAndAccountId(leagueId, accountId)
                .orElseThrow(() -> new LeagueException("You are not in this league"));
    }

    private LeagueSummary summary(Team myTeam, Long accountId) {
        League league = myTeam.getLeague();
        return new LeagueSummary(league.getId(), league.getName(), league.getJoinCode(), league.getTeamCount(),
                teams.countByLeagueId(league.getId()), league.getStatus(), league.getSeason(), league.getKickoffTimes(),
                league.getOwner().getId().equals(accountId), myTeam.getId(), myTeam.getName());
    }
}
