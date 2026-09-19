package com.backlogtracker.backlogtracker.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class ConfigPriorityApiTest extends ConfigApiTestSupport {

    @Test
    void addingAPriorityRecordsTheRealActorInHistory() throws Exception {
        String actorId = mapper.readTree(mvc.perform(auth(get("/api/auth/me")))
                        .andReturn().getResponse().getContentAsString())
                .get("id").asText();

        mvc.perform(auth(post("/api/config/priorities")).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Blocker","value":5}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priorities", org.hamcrest.Matchers.hasItem("Blocker")));

        mvc.perform(auth(get("/api/config/history")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].summary").value("added priority 'Blocker' = 5"))
                .andExpect(jsonPath("$[0].changedBy").value(actorId));
    }

    /** Regression coverage for removePriority's three safe-removal branches (design §7) —
     *  the identical rule already well-tested on the category side (see
     *  ConfigCategoryApiTest.blocksRemovingACategoryTwoItemsUse /
     *  removesACategoryOneItemUsesAfterReassignment), but never exercised for priorities
     *  until now. */
    @Test
    void blocksRemovingAPriorityTwoItemsUse() throws Exception {
        createItem("a", "Project", "Medium");
        createItem("b", "Project", "Medium");
        mvc.perform(auth(delete("/api/config/priorities/Medium")))
                .andExpect(status().isConflict());
    }

    @Test
    void removesAPriorityOneItemUsesAfterReassignment() throws Exception {
        createItem("only", "Project", "Medium");
        mvc.perform(auth(delete("/api/config/priorities/Medium").param("reassignTo", "Low")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priorities", org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.hasItem("Medium"))));
        mvc.perform(auth(get("/api/items")).param("groupId", groupId))
                .andExpect(jsonPath("$.content[0].priority").value("Low"));
    }

    @Test
    void removesAnUnusedPriorityOutright() throws Exception {
        mvc.perform(auth(delete("/api/config/priorities/Medium")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priorities", org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.hasItem("Medium"))));
    }

    @Test
    void refusesToRemoveTheLastRemainingPriority() throws Exception {
        for (String p : new String[] {"High", "Medium", "Low"}) {
            mvc.perform(auth(delete("/api/config/priorities/" + p))).andExpect(status().isOk());
        }
        mvc.perform(auth(delete("/api/config/priorities/Critical")))
                .andExpect(status().isBadRequest());
    }
}
