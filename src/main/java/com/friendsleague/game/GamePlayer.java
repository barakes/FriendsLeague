package com.friendsleague.game;

import com.friendsleague.match.MatchEngine;
import com.friendsleague.match.MatchResult;
import com.friendsleague.match.TeamSheet;
import com.friendsleague.squad.FootballerProfile;
import com.friendsleague.squad.Lineup;
import com.friendsleague.squad.LineupException;
import com.friendsleague.squad.Position;
import com.friendsleague.team.Footballer;
import com.friendsleague.team.FootballerRepository;
import com.friendsleague.team.LineupSlot;
import com.friendsleague.team.Team;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Plays one game: builds both team sheets from the database, runs the match engine and saves the result.
@Service
public class GamePlayer {

    private final GameRepository games;
    private final FootballerRepository footballers;
    private final MatchEngine engine = new MatchEngine(new SecureRandom());

    public GamePlayer(GameRepository games, FootballerRepository footballers) {
        this.games = games;
        this.footballers = footballers;
    }

    // Its own transaction for every game, so one broken game can't stop the others from being played.
    @Transactional
    public void play(Long gameId) {
        Game game = games.findById(gameId).orElseThrow();
        if (game.isPlayed()) {
            return;
        }
        MatchResult result = engine.play(sheetFor(game.getHomeTeam()), sheetFor(game.getAwayTeam()));
        game.recordResult(result);
    }

    // The lineup that takes the field. Computer teams always pick their best lineup.
    // A manager's saved lineup is used if it is still valid; if not (for example, a player was sold),
    // the best lineup in the manager's formation is used instead, so the game can always be played.
    TeamSheet sheetFor(Team team) {
        List<Footballer> squad = footballers.findByTeamIdOrderById(team.getId());
        Map<Long, FootballerProfile> profiles = new HashMap<>();
        List<FootballerProfile> squadProfiles = new ArrayList<>();
        for (Footballer footballer : squad) {
            FootballerProfile profile = footballer.toProfile();
            profiles.put(footballer.getId(), profile);
            squadProfiles.add(profile);
        }

        Lineup lineup;
        if (team.isComputer()) {
            lineup = Lineup.bestOfAll(squadProfiles);
        } else {
            lineup = savedLineup(team, profiles);
            if (lineup == null) {
                lineup = Lineup.best(team.getFormation(), squadProfiles);
            }
        }
        return new TeamSheet(team.getName(), lineup, team.getTactic());
    }

    // Null if the saved lineup no longer fits the squad or the formation.
    private static Lineup savedLineup(Team team, Map<Long, FootballerProfile> profiles) {
        Map<Position, List<FootballerProfile>> lines = new EnumMap<>(Position.class);
        for (Position line : Position.values()) {
            lines.put(line, new ArrayList<>());
        }
        for (LineupSlot slot : team.getLineup()) {
            FootballerProfile profile = profiles.get(slot.getFootballerId());
            if (profile == null) {
                return null;
            }
            lines.get(slot.getLine()).add(profile);
        }
        try {
            return Lineup.of(team.getFormation(), lines);
        } catch (LineupException e) {
            return null;
        }
    }
}
