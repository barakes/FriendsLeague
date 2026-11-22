package com.friendsleague.team;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team, Long> {

    List<Team> findByLeagueIdOrderById(Long leagueId);

    Optional<Team> findByLeagueIdAndAccountId(Long leagueId, Long accountId);

    // All the teams of one friend, one per league he plays in.
    List<Team> findByAccountIdOrderById(Long accountId);

    long countByLeagueId(Long leagueId);

    boolean existsByLeagueIdAndNameIgnoreCase(Long leagueId, String name);
}
