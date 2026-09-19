package com.backlogtracker.backlogtracker.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.backlogtracker.support.AuthTestSupport;

/** Categories are per-group (see Group.categories / GroupService), not part of the shared
 *  app config, but the safety rules (>1 use blocked, ==1 needs reassignTo) are the same
 *  shape as ConfigPriorityApiTest's — kept alongside config's other tests since this suite
 *  already has the group + item fixtures set up (to-8). */
class ConfigCategoryApiTest extends ConfigApiTestSupport {

    @Test
    void addsAndRemovesAnUnusedCategoryOnTheGroup() throws Exception {
        mvc.perform(auth(post("/api/groups/" + groupId + "/categories")).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Marketing"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories", org.hamcrest.Matchers.hasItem("Marketing")));

        mvc.perform(auth(delete("/api/groups/" + groupId + "/categories/Marketing")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories", org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.hasItem("Marketing"))));
    }

    @Test
    void blocksRemovingACategoryTwoItemsUse() throws Exception {
        createItem("a", "Research", "Low");
        createItem("b", "Research", "Low");
        mvc.perform(auth(delete("/api/groups/" + groupId + "/categories/Research")))
                .andExpect(status().isConflict());
    }

    @Test
    void removesACategoryOneItemUsesAfterReassignment() throws Exception {
        createItem("only", "Admin-Ops", "Low");
        mvc.perform(auth(delete("/api/groups/" + groupId + "/categories/Admin-Ops")
                        .param("reassignTo", "Project")))
                .andExpect(status().isOk());
        mvc.perform(auth(get("/api/items")).param("groupId", groupId))
                .andExpect(jsonPath("$.content[0].category").value("Project"));
    }

    @Test
    void categoriesAreIsolatedPerGroup() throws Exception {
        String otherGroupId = AuthTestSupport.createGroup(mvc, mapper, token, "Other Group");

        mvc.perform(auth(post("/api/groups/" + groupId + "/categories")).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Only In First"}"""))
                .andExpect(status().isOk());

        mvc.perform(auth(get("/api/groups/" + otherGroupId + "/categories")))
                .andExpect(jsonPath("$.categories", org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.hasItem("Only In First"))));
    }
}
