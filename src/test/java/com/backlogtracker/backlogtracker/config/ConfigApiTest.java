package com.backlogtracker.backlogtracker.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.backlogtracker.backlogtracker.config.repository.ConfigHistoryRepository;
import com.backlogtracker.backlogtracker.config.repository.ConfigRepository;
import com.backlogtracker.backlogtracker.item.repository.ItemRepository;
import com.backlogtracker.commons.group.repository.GroupRepository;
import com.backlogtracker.support.AuthTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class ConfigApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired ConfigRepository configRepo;
    @Autowired ConfigHistoryRepository historyRepo;
    @Autowired ItemRepository items;
    @Autowired GroupRepository groups;

    private String token;
    private String groupId;

    @BeforeEach
    void setUp() throws Exception {
        configRepo.deleteAll();
        historyRepo.deleteAll();
        items.deleteAll();
        token = AuthTestSupport.devToken(mvc, mapper);
        groups.deleteAll();
        groupId = AuthTestSupport.createGroup(mvc, mapper, token, "Config Test Group");
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b) {
        return b.header("Authorization", "Bearer " + token);
    }

    private static final String GOOD_WEIGHTS = """
            {"priorityWeight":0.5,"urgencyWeight":0.3,"effortWeight":0.2,
             "urgencyWindowDays":14,"staleThresholdDays":10,"buriedThresholdDays":30,
             "defaultDueDateOffsetDays":30,"effortCapDays":30,"maxGroupsPerUser":5,
             "buriedPriorityLevels":["Low"],
             "priorityValues":{"Critical":4,"High":3,"Medium":2,"Low":1}}""";

    private void createItem(String title, String category, String priority) throws Exception {
        mvc.perform(auth(post("/api/items")).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"groupId":"%s","title":"%s","category":"%s","priority":"%s",
                                 "effortEstimate":{"value":30,"unit":"MINUTES"}}"""
                                .formatted(groupId, title, category, priority)))
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsWeightsThatDoNotSumToOne() throws Exception {
        mvc.perform(auth(put("/api/config")).contentType(MediaType.APPLICATION_JSON)
                        .content(GOOD_WEIGHTS.replace("\"effortWeight\":0.2", "\"effortWeight\":0.9")))
                .andExpect(status().isBadRequest());
    }

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

    // Categories are per-group (see Group.categories / GroupService), not part of this
    // shared config, but the safety rules (>1 use blocked, ==1 needs reassignTo) are the
    // same shape as the priority ones tested above — covered here since this file already
    // has a group + item fixtures set up.

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
