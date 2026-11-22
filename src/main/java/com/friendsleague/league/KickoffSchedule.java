package com.friendsleague.league;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

// Turns the league's kick-off times (for example "13:00,21:00", Israel time) into the exact moments of the rounds.
public final class KickoffSchedule {

    public static final ZoneId ISRAEL = ZoneId.of("Asia/Jerusalem");
    public static final int MAX_ROUNDS_PER_DAY = 4;

    // The first round is at least this far away, so managers have time to set their lineups.
    static final Duration MIN_NOTICE = Duration.ofMinutes(30);
    // Rounds on the same day must be at least this far apart.
    static final Duration MIN_GAP = Duration.ofHours(1);

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private KickoffSchedule() {
    }

    // Checks and sorts the times. Throws a LeagueException with a message for the player if they are not valid.
    public static List<LocalTime> parse(String kickoffTimes) {
        if (kickoffTimes == null || kickoffTimes.isBlank()) {
            throw new LeagueException("Please choose at least one kick-off time");
        }

        List<LocalTime> times = new ArrayList<>();
        for (String part : kickoffTimes.split(",")) {
            try {
                times.add(LocalTime.parse(part.strip(), FORMAT));
            } catch (DateTimeParseException e) {
                throw new LeagueException("Kick-off times look like 21:00");
            }
        }
        if (times.size() > MAX_ROUNDS_PER_DAY) {
            throw new LeagueException("At most " + MAX_ROUNDS_PER_DAY + " rounds a day");
        }

        times.sort(null);
        for (int i = 1; i < times.size(); i++) {
            if (Duration.between(times.get(i - 1), times.get(i)).compareTo(MIN_GAP) < 0) {
                throw new LeagueException("Rounds on the same day must be at least an hour apart");
            }
        }
        return List.copyOf(times);
    }

    // The same times written in one standard form, to save in the database ("21:00, 13:00" -> "13:00,21:00").
    public static String normalize(String kickoffTimes) {
        return String.join(",", parse(kickoffTimes).stream().map(FORMAT::format).toList());
    }

    // The moments of the next "count" rounds after "now".
    public static List<Instant> next(String kickoffTimes, Instant now, int count) {
        List<LocalTime> times = parse(kickoffTimes);
        Instant earliest = now.plus(MIN_NOTICE);

        List<Instant> kickoffs = new ArrayList<>(count);
        LocalDate day = LocalDate.ofInstant(now, ISRAEL);
        while (kickoffs.size() < count) {
            for (LocalTime time : times) {
                // ZonedDateTime takes care of summer and winter time.
                Instant kickoff = ZonedDateTime.of(day, time, ISRAEL).toInstant();
                if (!kickoff.isBefore(earliest) && kickoffs.size() < count) {
                    kickoffs.add(kickoff);
                }
            }
            day = day.plusDays(1);
        }
        return kickoffs;
    }
}