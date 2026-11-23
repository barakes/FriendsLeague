package com.friendsleague.game;

import com.friendsleague.market.ComputerTransfers;
import com.friendsleague.match.LiveClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

// Every 30 seconds, and also right when the server starts:
// 1. Plays every game whose kick-off time has come.
// 2. Pays the money of every game whose live broadcast has ended.
//    Then the computer teams of that game may use the market.
// If the server was asleep, everything that was missed is done as soon as it wakes up, oldest first.
// The live view still counts from the original kick-off time.
@Component
public class RoundRunner {

    private static final Logger log = LoggerFactory.getLogger(RoundRunner.class);

    private final GameRepository games;
    private final GamePlayer player;
    private final GameSettler settler;
    private final ComputerTransfers computerTransfers;
    private final Clock clock;

    public RoundRunner(GameRepository games, GamePlayer player, GameSettler settler,
                       ComputerTransfers computerTransfers, Clock clock) {
        this.games = games;
        this.player = player;
        this.settler = settler;
        this.computerTransfers = computerTransfers;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void catchUpOnStart() {
        tick();
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public void tick() {
        Instant now = clock.instant();
        playDueGames(now);
        settleFinishedGames(now);
    }

    // Returns how many games were played. Public so tests can choose the moment.
    public int playDueGames(Instant now) {
        List<Game> due = games.findByKickoffAtLessThanEqualAndHomeGoalsIsNullOrderByKickoffAt(now);
        int played = 0;
        for (Game game : due) {
            try {
                player.play(game.getId());
                played++;
            } catch (RuntimeException e) {
                // A bug in one game must not stop the rest of the round.
                log.error("Could not play game {}", game.getId(), e);
            }
        }
        if (played > 0) {
            log.info("Played {} games", played);
        }
        return played;
    }

    // In its own transaction: a problem here never undoes the money that was already paid.
    private void useMarket(Long teamId) {
        try {
            computerTransfers.consider(teamId);
        } catch (RuntimeException e) {
            log.error("Computer team {} could not use the market", teamId, e);
        }
    }

    // Returns how many games were paid. A game is paid once its broadcast is over: kick-off + 4.5 minutes.
    public int settleFinishedGames(Instant now) {
        Instant latestKickoff = now.minus(LiveClock.GAME_LENGTH);
        List<Game> finished = games.findByKickoffAtLessThanEqualAndHomeGoalsIsNotNullAndSettledFalseOrderByKickoffAtAscIdAsc(latestKickoff);
        int settled = 0;
        for (Game game : finished) {
            try {
                settler.settle(game.getId());
                settled++;
            } catch (RuntimeException e) {
                log.error("Could not pay the money of game {}", game.getId(), e);
                continue;
            }
            useMarket(game.getHomeTeam().getId());
            useMarket(game.getAwayTeam().getId());
        }
        return settled;
    }
}
