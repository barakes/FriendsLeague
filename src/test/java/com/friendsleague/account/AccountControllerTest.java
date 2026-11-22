package com.friendsleague.account;

import com.friendsleague.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Calls the HTTP addresses the way a browser would.
@AutoConfigureMockMvc
class AccountControllerTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private LoginTokenRepository tokens;

    @BeforeEach
    void cleanDatabase() {
        tokens.deleteAll();
        accounts.deleteAll();
    }

    @Test
    void registeringReturnsATokenAndTheAccountButNeverThePassword() throws Exception {
        mockMvc.perform(post("/api/accounts/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "Dana", "password": "secret123", "rememberMe": true}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.account.username").value("Dana"))
                .andExpect(jsonPath("$.account.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.account.password").doesNotExist());
    }

    @Test
    void aRequestWithoutRememberMeIsAccepted() throws Exception {
        mockMvc.perform(post("/api/accounts/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "Dana", "password": "secret123"}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void theTokenAnswersWhoAmI() throws Exception {
        String token = register("Dana", "secret123");

        mockMvc.perform(get("/api/accounts/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("Dana"));
    }

    @Test
    void withoutAValidTokenWhoAmIIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/accounts/me"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/accounts/me").header(HttpHeaders.AUTHORIZATION, "Bearer made-up-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aWrongPasswordGivesAMessageForThePlayer() throws Exception {
        register("Dana", "secret123");

        mockMvc.perform(post("/api/accounts/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "Dana", "password": "wrong-password", "rememberMe": false}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Wrong username or password"));
    }

    @Test
    void afterLoggingOutTheTokenStopsWorking() throws Exception {
        String token = register("Dana", "secret123");

        mockMvc.perform(post("/api/accounts/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/accounts/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    // Registers a player and returns the token from the response.
    private String register(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/api/accounts/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "%s", "password": "%s", "rememberMe": false}
                                """.formatted(username, password)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.token");
    }
}