package com.friendsleague.league;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KickoffScheduleTest {

    @Test
    void oneRoundADayAtNineInTheEvening() {
        // 20:00 in Israel (winter, UTC+2) = 18:00 UTC. 21:00 Israel = 19:00 UTC.
        assertThat(KickoffSchedule.next("21:00", Instant.parse("2026-11-22T18:00:00Z"), 3)).containsExactly(
                Instant.parse("2026-11-22T19:00:00Z"),
                Instant.parse("2026-11-23T19:00:00Z"),
                Instant.parse("2026-11-24T19:00:00Z"));
    }

    @Test
    void aRoundLessThanHalfAnHourAwayIsSkipped() {
        // 20:45 in Israel: 21:00 is too soon, so the first round is tomorrow at 13:00.
        assertThat(KickoffSchedule.next("21:00, 13:00", Instant.parse("2026-11-22T18:45:00Z"), 3)).containsExactly(
                Instant.parse("2026-11-23T11:00:00Z"),
                Instant.parse("2026-11-23T19:00:00Z"),
                Instant.parse("2026-11-24T11:00:00Z"));
    }

    @Test
    void roundsStayAtNineIsraelTimeWhenSummerTimeStarts() {
        // Summer time starts on 26 March 2027: 21:00 Israel moves from 19:00 UTC to 18:00 UTC.
        assertThat(KickoffSchedule.next("21:00", Instant.parse("2027-03-24T12:00:00Z"), 4)).containsExactly(
                Instant.parse("2027-03-24T19:00:00Z"),
                Instant.parse("2027-03-25T19:00:00Z"),
                Instant.parse("2027-03-26T18:00:00Z"),
                Instant.parse("2027-03-27T18:00:00Z"));
    }

    @Test
    void timesAreSavedInOneStandardForm() {
        assertThat(KickoffSchedule.normalize(" 21:00,13:00")).isEqualTo("13:00,21:00");
    }

    @Test
    void invalidTimesAreRejected() {
        assertThatThrownBy(() -> KickoffSchedule.parse("")).isInstanceOf(LeagueException.class);
        assertThatThrownBy(() -> KickoffSchedule.parse("25:00")).hasMessageContaining("look like");
        assertThatThrownBy(() -> KickoffSchedule.parse("21:00,21:30")).hasMessageContaining("an hour apart");
        assertThatThrownBy(() -> KickoffSchedule.parse("08:00,10:00,12:00,14:00,16:00")).hasMessageContaining("At most 4");
    }
}