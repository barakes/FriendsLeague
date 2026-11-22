package com.friendsleague.team;

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

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class TeamControllerTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountService accountService;

    private String dana;
    private String tom;
    private Integer danaTeam;

    @BeforeEach
    void createLeague() throws Exception {
        dana = accountService.register("Dana", "secret123", false).token();
        tom = accountService.register("Tom", "secret123", false).token();
        String league = mockMvc.perform(as(dana, post("/api/leagues")).content("""
                        {"name": "ליגת החבר'ה", "size": 6, "kickoffTimes": "21:00", "teamName": "הפועל דנה"}
                        """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        danaTeam = JsonPath.read(league, "$.myTeamId");
    }

    private static MockHttpServletRequestBuilder as(String token, MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    @Test
    void aManagerSeesHisSquad() throws Exception {
        mockMvc.perform(as(dana, get("/api/teams/" + danaTeam)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("הפועל דנה"))
                .andExpect(jsonPath("$.mine").value(true))
                .andExpect(jsonPath("$.squad", hasSize(18)));
    }

    @Test
    void theBestLineupButtonChangesTheFormation() throws Exception {
        mockMvc.perform(as(dana, post("/api/teams/" + danaTeam + "/lineup/best")).content("""
                        {"formation": "F_4_3_3"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.formation").value("F_4_3_3"))
                .andExpect(jsonPath("$.squad[?(@.line == 'FORWARD')]", hasSize(3)));
    }

    @Test
    void someoneOutsideTheLeagueCannotSeeTheTeam() throws Exception {
        mockMvc.perform(as(tom, get("/api/teams/" + danaTeam)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You are not in this league"));
    }
}
