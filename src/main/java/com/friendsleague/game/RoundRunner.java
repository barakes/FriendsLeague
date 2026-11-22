package com.friendsleague.game;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

// Plays every game whose kick-off time has come.
// Runs every 30 seconds, and also right when the server starts: if the server was asleep at kick-off time,
// the games are played as soon as it wakes up. The live view still counts from the original kick-off time.
@Component
public class RoundRunner {

    private static final Logger log = LoggerFactory.getLogger(RoundRunner.class);

    private final GameRepository games;
    private final GamePlayer player;
    private final Clock clock;

    public RoundRunner(GameRepository games, GamePlayer player, Clock clock) {
        this.games = games;
        this.player = player;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void catchUpOnStart() {
        playDueGames();
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public void playDueGames() {
        playDueGames(clock.instant());
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
}
