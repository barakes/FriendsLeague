package com.friendsleague.match;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class LiveClockTest {

    private static final Instant KICKOFF = Instant.parse("2026-11-22T19:00:00Z");

    @Test
    void beforeKickoffTheClockShowsZero() {
        assertThat(LiveClock.minuteAt(KICKOFF, KICKOFF.minusSeconds(60))).isZero();
        assertThat(LiveClock.minuteAt(KICKOFF, KICKOFF)).isZero();
    }

    @Test
    void everyGameMinuteTakesThreeSeconds() {
        assertThat(LiveClock.minuteAt(KICKOFF, KICKOFF.plusSeconds(2))).isZero();
        assertThat(LiveClock.minuteAt(KICKOFF, KICKOFF.plusSeconds(3))).isEqualTo(1);
        assertThat(LiveClock.minuteAt(KICKOFF, KICKOFF.plusSeconds(120))).isEqualTo(40);
    }

    @Test
    void theClockStopsAtNinety() {
        assertThat(LiveClock.minuteAt(KICKOFF, KICKOFF.plus(Duration.ofHours(5)))).isEqualTo(90);
    }

    @Test
    void aGameLastsFourAndAHalfMinutes() {
        assertThat(LiveClock.GAME_LENGTH).isEqualTo(Duration.ofSeconds(270));
        assertThat(LiveClock.isOver(KICKOFF, KICKOFF.plusSeconds(269))).isFalse();
        assertThat(LiveClock.isOver(KICKOFF, KICKOFF.plusSeconds(270))).isTrue();
        assertThat(LiveClock.revealAt(KICKOFF, 34)).isEqualTo(KICKOFF.plusSeconds(102));
    }
}
