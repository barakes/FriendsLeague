package com.friendsleague.team;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team, Long> {

    List<Team> findByLeagueIdOrderById(Long leagueId);

    Optional<Team> findByLeagueIdAndAccountId(Long leagueId, Long accountId);

    // All the teams of one friend, one per league he plays in.
    List<Team> findByAccountIdOrderById(Long accountId);

    long countByLeagueId(Long leagueId);

    boolean existsByLeagueIdAndNameIgnoreCase(Long leagueId, String name);

    // Reads the team and locks its row until the transaction ends ("select ... for update").
    // Used when money changes, so two clicks at the same moment can't spend the same money twice.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Team t where t.id = :id")
    Optional<Team> findByIdForUpdate(@Param("id") Long id);
}
