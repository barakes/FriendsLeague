package com.friendsleague.team;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MoneyRecordRepository extends JpaRepository<MoneyRecord, Long> {

    // Newest first.
    List<MoneyRecord> findByTeamIdOrderByIdDesc(Long teamId);
}
