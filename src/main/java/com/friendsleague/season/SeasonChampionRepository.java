package com.friendsleague.season;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeasonChampionRepository extends JpaRepository<SeasonChampion, Long> {

    // The latest season first.
    List<SeasonChampion> findByLeagueIdOrderBySeasonDesc(Long leagueId);
}
