package com.friendsleague.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

// Once a day, deletes login tokens that have expired. They no longer work anyway; this only keeps the table small.
@Configuration
@EnableScheduling
public class TokenCleanup {

    private static final Logger log = LoggerFactory.getLogger(TokenCleanup.class);

    private final AccountService accountService;

    public TokenCleanup(AccountService accountService) {
        this.accountService = accountService;
    }

    // Every day at 04:00 (server time), when almost nobody is playing.
    @Scheduled(cron = "0 0 4 * * *")
    public void deleteExpiredTokens() {
        int deleted = accountService.deleteExpiredTokens();
        log.info("Deleted {} expired login tokens", deleted);
    }
}