package com.friendsleague.account;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;

// Registering, logging in and out, and finding out who a login token belongs to.
@Service
public class AccountService {

    public static final Duration REMEMBER_ME_DURATION = Duration.ofDays(30);
    public static final Duration SESSION_DURATION = Duration.ofDays(1);

    // Letters in any language (including Hebrew), digits and '_'.
    private static final Pattern USERNAME = Pattern.compile("[\\p{L}\\p{N}_]{3,20}");
    private static final int MIN_PASSWORD_LENGTH = 6;
    // BCrypt only looks at the first 72 bytes of a password, so longer ones are rejected.
    private static final int MAX_PASSWORD_BYTES = 72;
    private static final int TOKEN_BYTES = 32;

    // The same message for a wrong username and a wrong password, so nobody can find out which usernames exist.
    private static final String WRONG_LOGIN = "Wrong username or password";

    // What a successful registration or login returns: the token for the browser, and the account.
    public record LoginResult(String token, Account account) {
    }

    private final AccountRepository accounts;
    private final LoginTokenRepository tokens;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    // Checked when the username doesn't exist, so a wrong username takes as long as a wrong password.
    private final String dummyHash;

    public AccountService(AccountRepository accounts, LoginTokenRepository tokens,
                          PasswordEncoder passwordEncoder, Clock clock) {
        this.accounts = accounts;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("not-a-real-password");
    }

    public LoginResult register(String username, String password, boolean rememberMe) {
        String name = username == null ? "" : username.strip();
        if (!USERNAME.matcher(name).matches()) {
            throw new AccountException("Username must be 3-20 letters, digits or _");
        }
        checkPassword(password);
        if (accounts.existsByUsernameIgnoreCase(name)) {
            throw new AccountException("This username is taken");
        }

        Account account;
        try {
            account = accounts.saveAndFlush(new Account(name, passwordEncoder.encode(password)));
        } catch (DataIntegrityViolationException e) {
            // Two people registered the same name at the same moment; the unique index stopped the second one.
            throw new AccountException("This username is taken");
        }
        return new LoginResult(issueToken(account, rememberMe), account);
    }

    public LoginResult login(String username, String password, boolean rememberMe) {
        Optional<Account> found = username == null
                ? Optional.empty()
                : accounts.findByUsernameIgnoreCase(username.strip());

        String hash = found.map(Account::getPasswordHash).orElse(dummyHash);
        boolean passwordMatches = password != null && passwordEncoder.matches(password, hash);

        if (found.isEmpty() || !passwordMatches) {
            throw new AccountException(WRONG_LOGIN);
        }
        return new LoginResult(issueToken(found.get(), rememberMe), found.get());
    }

    // Who does this token belong to? Empty if the token is unknown or expired.
    public Optional<Account> findByToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        return tokens.findById(hash(token))
                .filter(loginToken -> !loginToken.isExpired(now))
                .map(LoginToken::getAccount);
    }

    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            tokens.deleteById(hash(token));
        }
    }

    @Transactional
    public int deleteExpiredTokens() {
        return tokens.deleteExpired(clock.instant());
    }

    private void checkPassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new AccountException("Password must be at least 6 characters");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new AccountException("Password is too long");
        }
    }

    // Creates a random token, stores only its hash, and returns the token itself (for the browser).
    private String issueToken(Account account, boolean rememberMe) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        Instant now = clock.instant();
        Duration lifetime = rememberMe ? REMEMBER_ME_DURATION : SESSION_DURATION;
        tokens.save(new LoginToken(hash(token), account, now, now.plus(lifetime)));
        return token;
    }

    // SHA-256 is enough here (no need for slow BCrypt): the token is long and random, so it can't be guessed.
    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available in Java", e);
        }
    }
}