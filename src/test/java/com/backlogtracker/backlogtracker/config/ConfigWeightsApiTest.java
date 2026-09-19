package com.backlogtracker.backlogtracker.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class ConfigWeightsApiTest extends ConfigApiTestSupport {

    @Test
    void rejectsWeightsThatDoNotSumToOne() throws Exception {
        mvc.perform(auth(put("/api/config")).contentType(MediaType.APPLICATION_JSON)
                        .content(GOOD_WEIGHTS.replace("\"effortWeight\":0.2", "\"effortWeight\":0.9")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void savesValidWeightsAndWritesHistory() throws Exception {
        mvc.perform(auth(put("/api/config")).contentType(MediaType.APPLICATION_JSON)
                        .content(GOOD_WEIGHTS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.staleThresholdDays").value(10))
                .andExpect(jsonPath("$.priorityWeight").value(0.5));

        mvc.perform(auth(get("/api/config/history")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].summary").value("weights & thresholds updated"));
    }
}
