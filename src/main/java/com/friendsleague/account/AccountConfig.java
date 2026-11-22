package com.friendsleague.account;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;

@Configuration
public class AccountConfig {

    // BCrypt is slow on purpose and adds a random "salt" to every password,
    // so even two players with the same password get different hashes.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // The real clock. Tests can use a fixed clock instead.
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}