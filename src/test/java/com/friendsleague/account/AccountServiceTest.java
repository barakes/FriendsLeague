package com.friendsleague.account;

import com.friendsleague.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountServiceTest extends IntegrationTest {

    // A clock that only moves when the test tells it to.
    static class TestClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T12:00:00Z");

        void set(Instant instant) {
            now = instant;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    // Replaces the real clock in this test's application.
    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        TestClock testClock() {
            return new TestClock();
        }
    }

    @Autowired
    private AccountService service;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private LoginTokenRepository tokens;

    @Autowired
    private TestClock clock;

    @BeforeEach
    void cleanDatabase() {
        // All tests share one database, so each test starts from empty tables.
        tokens.deleteAll();
        accounts.deleteAll();
        clock.set(Instant.parse("2026-01-01T12:00:00Z"));
    }

    @Test
    void aNewPlayerCanRegisterAndThenLogIn() {
        AccountService.LoginResult registered = service.register("Dana", "secret123", false);

        assertThat(registered.token()).isNotBlank();

        // The username can be typed in any letter case.
        AccountService.LoginResult loggedIn = service.login("dana", "secret123", false);
        assertThat(loggedIn.account().getId()).isEqualTo(registered.account().getId());
        assertThat(loggedIn.token()).isNotEqualTo(registered.token());
    }

    @Test
    void theTokenTellsWhoThePlayerIs() {
        String token = service.register("Dana", "secret123", false).token();

        assertThat(service.findByToken(token)).get()
                .extracting(Account::getUsername)
                .isEqualTo("Dana");
        assertThat(service.findByToken("not-a-real-token")).isEmpty();
        assertThat(service.findByToken(null)).isEmpty();
    }

    @Test
    void onlyAHashOfThePasswordAndOfTheTokenIsStored() {
        AccountService.LoginResult result = service.register("Dana", "secret123", false);

        Account saved = accounts.findById(result.account().getId()).orElseThrow();
        assertThat(saved.getPasswordHash()).isNotEqualTo("secret123").startsWith("$2");

        assertThat(tokens.findAll()).hasSize(1);
        assertThat(tokens.findAll().getFirst().getTokenHash()).isNotEqualTo(result.token()).hasSize(64);
    }

    @Test
    void aUsernameCanBeTakenOnlyOnceInAnyLetterCase() {
        service.register("Dana", "secret123", false);

        assertThatThrownBy(() -> service.register("DANA", "other-password", false))
                .isInstanceOf(AccountException.class)
                .hasMessageContaining("taken");
    }

    @Test
    void hebrewUsernamesAreAllowed() {
        AccountService.LoginResult result = service.register("ברק_7", "secret123", false);

        assertThat(result.account().getUsername()).isEqualTo("ברק_7");
    }

    @Test
    void badUsernamesAndPasswordsAreRejected() {
        assertThatThrownBy(() -> service.register("ab", "secret123", false))
                .isInstanceOf(AccountException.class).hasMessageContaining("Username");
        assertThatThrownBy(() -> service.register("has space", "secret123", false))
                .isInstanceOf(AccountException.class).hasMessageContaining("Username");
        assertThatThrownBy(() -> service.register("a".repeat(21), "secret123", false))
                .isInstanceOf(AccountException.class).hasMessageContaining("Username");
        assertThatThrownBy(() -> service.register("Dana", "12345", false))
                .isInstanceOf(AccountException.class).hasMessageContaining("at least 6");
        assertThatThrownBy(() -> service.register("Dana", "a".repeat(73), false))
                .isInstanceOf(AccountException.class).hasMessageContaining("too long");

        assertThat(accounts.count()).isZero();
    }

    @Test
    void aWrongPasswordAndAnUnknownUsernameGiveTheSameMessage() {
        service.register("Dana", "secret123", false);

        assertThatThrownBy(() -> service.login("Dana", "wrong-password", false))
                .isInstanceOf(AccountException.class)
                .hasMessage("Wrong username or password");
        assertThatThrownBy(() -> service.login("Nobody", "secret123", false))
                .isInstanceOf(AccountException.class)
                .hasMessage("Wrong username or password");
    }

    @Test
    void afterLoggingOutTheTokenNoLongerWorks() {
        String token = service.register("Dana", "secret123", false).token();

        service.logout(token);

        assertThat(service.findByToken(token)).isEmpty();
    }

    @Test
    void rememberMeKeepsThePlayerLoggedInFor30Days() {
        service.register("Dana", "secret123", false);
        String normal = service.login("Dana", "secret123", false).token();
        String remembered = service.login("Dana", "secret123", true).token();

        // Two days later: only the "remember me" token still works.
        clock.advance(Duration.ofDays(2));
        assertThat(service.findByToken(normal)).isEmpty();
        assertThat(service.findByToken(remembered)).isPresent();

        // After 30 days it expires too.
        clock.advance(Duration.ofDays(28));
        assertThat(service.findByToken(remembered)).isEmpty();
    }

    @Test
    void expiredTokensAreDeletedFromTheDatabase() {
        service.register("Dana", "secret123", false);
        service.login("Dana", "secret123", true);
        assertThat(tokens.count()).isEqualTo(2);

        clock.advance(Duration.ofDays(2));
        int deleted = service.deleteExpiredTokens();

        assertThat(deleted).isEqualTo(1);
        assertThat(tokens.count()).isEqualTo(1);
    }
}