package com.friendsleague.match;

import java.time.Duration;
import java.time.Instant;

// The "live broadcast" clock: every minute of a game takes 3 real seconds, so a whole game takes 4.5 minutes.
// The result is worked out at kick-off; this clock only says how much of it may be shown at a given moment.
public final class LiveClock {

    public static final Duration REAL_TIME_PER_MINUTE = Duration.ofSeconds(3);
    public static final Duration GAME_LENGTH = REAL_TIME_PER_MINUTE.multipliedBy(MatchEngine.MINUTES);

    private LiveClock() {
    }

    // The game minute at a moment: 0 before kick-off, then up to 90.
    public static int minuteAt(Instant kickoff, Instant now) {
        if (now.isBefore(kickoff)) {
            return 0;
        }
        long elapsed = Duration.between(kickoff, now).toMillis();
        long minute = elapsed / REAL_TIME_PER_MINUTE.toMillis();
        return (int) Math.min(MatchEngine.MINUTES, minute);
    }

    // When a goal in this minute may be shown.
    public static Instant revealAt(Instant kickoff, int minute) {
        return kickoff.plus(REAL_TIME_PER_MINUTE.multipliedBy(minute));
    }

    public static boolean isOver(Instant kickoff, Instant now) {
        return !now.isBefore(kickoff.plus(GAME_LENGTH));
    }
}
