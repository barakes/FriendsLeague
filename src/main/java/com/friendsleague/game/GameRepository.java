package com.friendsleague.game;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface GameRepository extends JpaRepository<Game, Long> {

    List<Game> findByLeagueIdAndSeasonOrderByRoundAscIdAsc(Long leagueId, int season);

    List<Game> findByLeagueIdAndSeasonAndRoundOrderById(Long leagueId, int season, int round);

    // Games whose kick-off time has come but that were not played yet (for example, the server was asleep).
    List<Game> findByKickoffAtLessThanEqualAndHomeGoalsIsNullOrderByKickoffAt(Instant now);

    // Games that were played, kicked off at "kickoff" or earlier, and whose money was not paid yet.
    List<Game> findByKickoffAtLessThanEqualAndHomeGoalsIsNotNullAndSettledFalseOrderByKickoffAtAscIdAsc(Instant kickoff);
}
