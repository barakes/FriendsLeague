package com.friendsleague.squad;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// The 11 players who start a match, placed in the lines of a formation.
// A player placed in a line that isn't his position counts as weaker there.
public final class Lineup {

    public static final int PLAYERS = 11;

    private final Formation formation;
    private final Map<Position, List<FootballerProfile>> lines;

    private Lineup(Formation formation, Map<Position, List<FootballerProfile>> lines) {
        this.formation = formation;
        this.lines = lines;
    }

    // A lineup the manager chose. Checks the counts per line and that nobody appears twice.
    public static Lineup of(Formation formation, Map<Position, List<FootballerProfile>> chosen) {
        if (formation == null) {
            throw new LineupException("Please choose a formation");
        }

        Map<Position, List<FootballerProfile>> lines = new EnumMap<>(Position.class);
        Set<FootballerProfile> seen = new HashSet<>();
        for (Position line : Position.values()) {
            List<FootballerProfile> players = chosen.getOrDefault(line, List.of());
            if (players.size() != formation.countFor(line)) {
                throw new LineupException("The formation " + formation.label() + " needs "
                        + formation.countFor(line) + " players in " + line);
            }
            for (FootballerProfile player : players) {
                if (!seen.add(player)) {
                    throw new LineupException(player.name() + " appears twice in the lineup");
                }
            }
            lines.put(line, List.copyOf(players));
        }
        return new Lineup(formation, Collections.unmodifiableMap(lines));
    }

    // The strongest possible lineup for a formation. Used by computer teams, and by the "pick my lineup" button.
    // It tries every way of filling the places, but remembers results it already worked out (dynamic programming),
    // so even with 18 players it takes only a few thousand steps.
    public static Lineup best(Formation formation, List<FootballerProfile> squad) {
        if (squad.size() < PLAYERS) {
            throw new LineupException("A squad needs at least " + PLAYERS + " players to play");
        }

        BestLineupSearch search = new BestLineupSearch(squad);
        int[] places = new int[Position.values().length];
        for (Position line : Position.values()) {
            places[line.ordinal()] = formation.countFor(line);
        }
        return of(formation, search.choose(places));
    }

    // The best lineup in the formation that suits this squad best.
    public static Lineup bestOfAll(List<FootballerProfile> squad) {
        Lineup best = null;
        for (Formation formation : Formation.values()) {
            Lineup candidate = best(formation, squad);
            if (best == null || candidate.totalStrength() > best.totalStrength()) {
                best = candidate;
            }
        }
        return best;
    }

    // How strong a player is when he plays in a given line.
    // His own position: full strength. A neighbouring line: 85%. Two lines away: 70%.
    // An outfield player in goal, or a goalkeeper outside it: 30%.
    public static double effectiveRating(FootballerProfile player, Position playsAs) {
        Position own = player.position();
        if (own == playsAs) {
            return player.rating();
        }
        if (own == Position.GOALKEEPER || playsAs == Position.GOALKEEPER) {
            return player.rating() * 0.3;
        }
        int distance = Math.abs(own.ordinal() - playsAs.ordinal());
        return player.rating() * (distance == 1 ? 0.85 : 0.7);
    }

    // Every player in the lineup must belong to the team's squad.
    public void checkBelongsTo(List<FootballerProfile> squad) {
        for (FootballerProfile player : allPlayers()) {
            if (!squad.contains(player)) {
                throw new LineupException(player.name() + " is not in your squad");
            }
        }
    }

    public Formation formation() {
        return formation;
    }

    public List<FootballerProfile> playersIn(Position line) {
        return lines.get(line);
    }

    public List<FootballerProfile> allPlayers() {
        List<FootballerProfile> all = new ArrayList<>(PLAYERS);
        lines.values().forEach(all::addAll);
        return all;
    }

    // The sum of the effective ratings of the players in a line. The match simulation is built on these numbers.
    public double lineStrength(Position line) {
        return lines.get(line).stream().mapToDouble(player -> effectiveRating(player, line)).sum();
    }

    public double totalStrength() {
        double total = 0;
        for (Position line : Position.values()) {
            total += lineStrength(line);
        }
        return total;
    }
}