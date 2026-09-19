package com.example.mednlp.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** EC-32: with no seeding and empty rule tables the service still starts and answers cleanly. */
@SpringBootTest(properties = "mednlp.seed-enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class EmptyRulesTest {

    @Autowired
    MockMvc mvc;

    @Test
    void startsAndAnswersWithNoRulesAtAll() throws Exception {
        mvc.perform(get("/api/v1/rules/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.concepts").value(0))
                .andExpect(jsonPath("$.sectionRules").value(0))
                .andExpect(jsonPath("$.negationTriggers").value(0));

        mvc.perform(post("/api/v1/charts/analyze").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Patient denies fever. BP 120/80. MRN: 12345678\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.concepts").isEmpty())
                .andExpect(jsonPath("$.phi").isEmpty())
                .andExpect(jsonPath("$.measurements").isEmpty())
                .andExpect(jsonPath("$.sentenceCount").value(3));
    }
}
