package com.friendsleague.team;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FootballerRepository extends JpaRepository<Footballer, Long> {

    List<Footballer> findByTeamIdOrderById(Long teamId);

    // The transfer market of a league: footballers without a team.
    List<Footballer> findByLeagueIdAndTeamIsNullOrderByRatingDesc(Long leagueId);
}
