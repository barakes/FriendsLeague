package com.friendsleague.team;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FootballerRepository extends JpaRepository<Footballer, Long> {

    List<Footballer> findByTeamIdOrderById(Long teamId);

    // The transfer market of a league: footballers without a team.
    List<Footballer> findByLeagueIdAndTeamIsNullOrderByRatingDesc(Long leagueId);

    // Only the names, so a new squad doesn't reuse a name that already exists in the league.
    @Query("select f.name from Footballer f where f.league.id = :leagueId")
    List<String> findNamesByLeagueId(@Param("leagueId") Long leagueId);

    // Locks the footballer's row: if two friends buy him at the same moment, the second one waits,
    // then sees that he already has a team.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from Footballer f where f.id = :id")
    Optional<Footballer> findByIdForUpdate(@Param("id") Long id);
}
