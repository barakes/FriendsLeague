package com.friendsleague.league;

import com.friendsleague.IntegrationTest;
import com.friendsleague.account.AccountService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class LeagueControllerTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountService accountService;

    private String dana;
    private String tom;

    @BeforeEach
    void logIn() {
        dana = accountService.register("Dana", "secret123", false).token();
        tom = accountService.register("Tom", "secret123", false).token();
    }

    private static MockHttpServletRequestBuilder as(String token, MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    // Creates a league as Dana and returns its JSON.
    private String createLeague() throws Exception {
        return mockMvc.perform(as(dana, post("/api/leagues")).content("""
                        {"name": "ליגת החבר'ה", "size": 6, "kickoffTimes": "21:00", "teamName": "הפועל דנה"}
                        """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void withoutLoggingInEverythingIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/leagues"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Please log in"));
        mockMvc.perform(as("made-up-token", get("/api/leagues")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void creatingALeagueReturnsItsCodeAndMyTeam() throws Exception {
        String league = createLeague();

        String code = JsonPath.read(league, "$.joinCode");
        assertThat(code).hasSize(6);
        mockMvc.perform(as(dana, get("/api/leagues")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("ליגת החבר'ה"))
                .andExpect(jsonPath("$[0].owner").value(true))
                .andExpect(jsonPath("$[0].myTeamName").value("הפועל דנה"))
                .andExpect(jsonPath("$[0].status").value("WAITING"))
                .andExpect(jsonPath("$[0].teams").value(1));
    }

    @Test
    void aFriendJoinsAndBothSeeTheLeague() throws Exception {
        String code = JsonPath.read(createLeague(), "$.joinCode");

        mockMvc.perform(as(tom, post("/api/leagues/join")).content("""
                        {"code": "%s", "teamName": "מכבי תום"}
                        """.formatted(code.toLowerCase())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.owner").value(false))
                .andExpect(jsonPath("$.teams").value(2))
                .andExpect(jsonPath("$.myTeamName").value("מכבי תום"));

        mockMvc.perform(as(dana, get("/api/leagues")))
                .andExpect(jsonPath("$[0].teams").value(2));
    }

    @Test
    void onlyTheCreatorStartsTheSeason() throws Exception {
        String league = createLeague();
        Integer id = JsonPath.read(league, "$.id");
        String code = JsonPath.read(league, "$.joinCode");
        mockMvc.perform(as(tom, post("/api/leagues/join")).content("""
                {"code": "%s", "teamName": "מכבי תום"}
                """.formatted(code)));

        mockMvc.perform(as(tom, post("/api/leagues/" + id + "/start")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Only the league's creator can start the season"));

        mockMvc.perform(as(dana, post("/api/leagues/" + id + "/start")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.teams").value(6));
    }

    @Test
    void mistakesGetAMessageForThePlayer() throws Exception {
        mockMvc.perform(as(dana, post("/api/leagues")).content("""
                        {"name": "Test", "size": 7, "kickoffTimes": "21:00", "teamName": "Team"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A league has 6, 8, 10 or 12 teams"));

        mockMvc.perform(as(tom, post("/api/leagues/join")).content("""
                        {"code": "NOPE99", "teamName": "Team"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("There is no league with this code"));
    }
}
