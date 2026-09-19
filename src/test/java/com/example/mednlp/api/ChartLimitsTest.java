package com.example.mednlp.api;

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

/** EC-04 / EC-05: the configured size limit is enforced exactly. */
@SpringBootTest(properties = "mednlp.max-chars=1000")
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class ChartLimitsTest {

    @Autowired
    MockMvc mvc;

    @Test
    void aChartExactlyAtTheLimitIsAccepted() throws Exception {
        mvc.perform(post("/api/v1/charts/analyze/text").contentType(MediaType.TEXT_PLAIN).content("x".repeat(1000)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.textLength").value(1000));
    }

    @Test
    void aChartOverTheLimitIsRejectedWith413() throws Exception {
        mvc.perform(post("/api/v1/charts/analyze/text").contentType(MediaType.TEXT_PLAIN).content("x".repeat(1001)))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.status").value(413))
                .andExpect(jsonPath("$.detail").value("The chart has 1001 characters; the limit is 1000"));
    }
}
