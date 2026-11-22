package com.friendsleague;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

// Base class for tests that need a real database.
// One PostgreSQL container is started once and shared by all integration tests.
@SpringBootTest
public abstract class IntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    static {
        POSTGRES.start();
    }

    @Autowired
    private JdbcTemplate jdbc;

    // Every test starts from empty tables. "cascade" also empties every table that points to these
    // (login tokens, teams, footballers, lineups, and tables we add later), so new tables need no change here.
    @BeforeEach
    protected void resetDatabase() {
        jdbc.execute("truncate table accounts, leagues restart identity cascade");
    }
}
