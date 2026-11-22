package com.friendsleague.game;

import com.friendsleague.match.Side;
import com.friendsleague.money.MoneyEntry;
import com.friendsleague.money.Prices;
import com.friendsleague.money.RoundMoney;
import com.friendsleague.team.Footballer;
import com.friendsleague.team.FootballerRepository;
import com.friendsleague.team.MoneyRecord;
import com.friendsleague.team.MoneyRecordRepository;
import com.friendsleague.team.Team;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// Pays the money of one game to both teams: TV rights, tickets, bonuses, sponsor and wages.
// It runs after the live broadcast ends, so the money (and any points lost for debt) never gives away the result.
@Service
public class GameSettler {

    private final GameRepository games;
    private final FootballerRepository footballers;
    private final MoneyRecordRepository moneyRecords;

    public GameSettler(GameRepository games, FootballerRepository footballers, MoneyRecordRepository moneyRecords) {
        this.games = games;
        this.footballers = footballers;
        this.moneyRecords = moneyRecords;
    }

    // Its own transaction for every game: both teams are paid and the game is marked, or nothing changes.
    @Transactional
    public void settle(Long gameId) {
        Game game = games.findById(gameId).orElseThrow();
        if (!game.isPlayed() || game.isSettled()) {
            return;
        }

        Side winner = game.winner();
        pay(game, game.getHomeTeam(), Side.HOME, winner);
        pay(game, game.getAwayTeam(), Side.AWAY, winner);
        game.markSettled();
    }

    private void pay(Game game, Team team, Side side, Side winner) {
        List<MoneyEntry> entries = RoundMoney.settle(side, winner, team.getSponsor(), wagesOf(team));
        for (MoneyEntry entry : entries) {
            moneyRecords.save(new MoneyRecord(team, game.getSeason(), game.getRound(), entry.kind(), entry.amount(), null));
        }
        team.settleRound(RoundMoney.total(entries));
    }

    private long wagesOf(Team team) {
        return footballers.findByTeamIdOrderById(team.getId()).stream()
                .mapToLong(footballer -> Prices.wage(footballer.getRating()))
                .sum();
    }
}
