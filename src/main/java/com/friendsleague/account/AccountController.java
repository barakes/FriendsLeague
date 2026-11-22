package com.friendsleague.account;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// Registering, logging in and out over HTTP.
// After logging in, the browser sends its token in the header "Authorization: Bearer <token>".
@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    // Boolean (not boolean), so a request without "rememberMe" is still accepted and means "no".
    public record Credentials(String username, String password, Boolean rememberMe) {
        boolean remember() {
            return Boolean.TRUE.equals(rememberMe);
        }
    }

    public record LoginResponse(String token, AccountView account) {
    }

    public record ErrorResponse(String message) {
    }

    private static final String BEARER = "Bearer ";

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public LoginResponse register(@RequestBody Credentials credentials) {
        return toResponse(accountService.register(
                credentials.username(), credentials.password(), credentials.remember()));
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody Credentials credentials) {
        return toResponse(accountService.login(
                credentials.username(), credentials.password(), credentials.remember()));
    }

    // "Who am I?" The page asks this when it opens, to know if the player is still logged in.
    @GetMapping("/me")
    public ResponseEntity<AccountView> me(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String header) {
        return accountService.findByToken(tokenFrom(header))
                .map(AccountView::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String header) {
        accountService.logout(tokenFrom(header));
    }

    // Expected problems (a taken username, a wrong password) become "400 Bad Request" with a message for the player.
    @ExceptionHandler(AccountException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handle(AccountException e) {
        return new ErrorResponse(e.getMessage());
    }

    private static LoginResponse toResponse(AccountService.LoginResult result) {
        return new LoginResponse(result.token(), AccountView.from(result.account()));
    }

    private static String tokenFrom(String header) {
        if (header == null || !header.startsWith(BEARER)) {
            return null;
        }
        return header.substring(BEARER.length()).strip();
    }
}