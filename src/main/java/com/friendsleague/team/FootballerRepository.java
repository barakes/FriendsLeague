package com.friendsleague.team;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FootballerRepository extends JpaRepository<Footballer, Long> {

    List<Footballer> findByTeamIdOrderById(Long teamId);

    // The transfer market of a league: footballers without a team.
    List<Footballer> findByLeagueIdAndTeamIsNullOrderByRatingDesc(Long leagueId);

    // Only the names, so a new squad doesn't reuse a name that already exists in the league.
    @Query("select f.name from Footballer f where f.league.id = :leagueId")
    List<String> findNamesByLeagueId(@Param("leagueId") Long leagueId);
}
