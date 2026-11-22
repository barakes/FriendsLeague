package com.friendsleague.account;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

// The id of a login token is its hash (a String).
public interface LoginTokenRepository extends JpaRepository<LoginToken, String> {

    // Deletes all expired tokens in one SQL statement, instead of loading them one by one.
    @Modifying
    @Query("delete from LoginToken t where t.expiresAt <= :now")
    int deleteExpired(@Param("now") Instant now);
}