package com.backlogtracker.backlogtracker.insights;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.backlogtracker.backlogtracker.item.domain.EffortEstimate;
import com.backlogtracker.backlogtracker.item.domain.EffortUnit;
import com.backlogtracker.backlogtracker.item.domain.Item;
import com.backlogtracker.backlogtracker.item.domain.ItemStatus;
import com.backlogtracker.backlogtracker.item.repository.ItemRepository;
import com.backlogtracker.commons.group.repository.GroupRepository;
import com.backlogtracker.support.AuthTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class InsightsApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired ItemRepository items;
    @Autowired MongoOperations mongo;
    @Autowired GroupRepository groups;

    private String token;
    private String groupId;

    @BeforeEach
    void setUp() throws Exception {
        items.deleteAll();
        token = AuthTestSupport.devToken(mvc, mapper);
        groups.deleteAll();
        groupId = AuthTestSupport.createGroup(mvc, mapper, token, "Insights Test Group");
    }

    /** Saves an item then back-dates createdAt/dueDate past the auditing callbacks. */
    private void seed(String itemId, String priority, ItemStatus status,
                      int createdDaysAgo, int dueDaysFromNow) {
        Item i = items.save(Item.builder()
                .itemId(itemId).title(itemId).category("Project").priority(priority)
                .effortEstimate(new EffortEstimate(30, EffortUnit.MINUTES))
                .status(status).groupId(groupId)
                .createdBy("seed").lastUpdatedBy("seed")
                .dueDate(Instant.now())
                .build());
        mongo.updateFirst(new Query(Criteria.where("_id").is(i.getId())),
                new Update()
                        .set("createdAt", Instant.now().minus(createdDaysAgo, ChronoUnit.DAYS))
                        .set("dueDate", Instant.now().plus(dueDaysFromNow, ChronoUnit.DAYS)),
                Item.class);
    }

    @Test
    void needsAttentionFlagsStaleOverdueAndBuriedLowPriority() throws Exception {
        // staleThresholdDays = 14, buriedThresholdDays = 30, buriedPriorityLevels = [Low]
        seed("ITM-STALE", "High", ItemStatus.BACKLOG, 5, -20);      // 20 days overdue -> stale
        seed("ITM-FRESH", "High", ItemStatus.IN_PROGRESS, 5, -5);   // only 5 days overdue -> not
        seed("ITM-BURIED", "Low", ItemStatus.BACKLOG, 40, 60);      // 40 days old, Low -> buried
        seed("ITM-YOUNGLOW", "Low", ItemStatus.BACKLOG, 10, 60);    // 10 days old -> not buried
        seed("ITM-OLDHIGH", "High", ItemStatus.BACKLOG, 40, 60);    // old but not Low -> not buried

        mvc.perform(get("/api/insights/needs-attention").param("groupId", groupId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.staleAndOverdue.length()").value(1))
                .andExpect(jsonPath("$.staleAndOverdue[0].item.itemId").value("ITM-STALE"))
                .andExpect(jsonPath("$.staleAndOverdue[0].days").value(20))
                .andExpect(jsonPath("$.buriedLowPriority.length()").value(1))
                .andExpect(jsonPath("$.buriedLowPriority[0].item.itemId").value("ITM-BURIED"));
    }

    @Test
    void workloadGroupsByOwnerWithUnassignedBucket() throws Exception {
        String uid = mapper.readTree(mvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString()).get("id").asText();

        createViaApi("owned-critical", "Critical", uid);
        createViaApi("owned-project", "Medium", uid);
        createViaApi("nobody-1", "Low", null);

        mvc.perform(get("/api/insights/workload").param("groupId", groupId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.owners[?(@.ownerName=='Unassigned')].openCount").value(1))
                .andExpect(jsonPath("$.owners[?(@.ownerId=='" + uid + "')].openCount").value(2))
                .andExpect(jsonPath("$.owners[?(@.ownerId=='" + uid + "')].criticalHighCount").value(1));
    }

    /** Regression test: WorkloadService used to hardcode "Critical"/"High" as literal
     *  strings — renaming or reweighting priorities silently zeroed the hot count instead
     *  of tracking whatever the two highest-weighted tiers actually are. Adding a new
     *  higher-weighted priority should bump "High" (weight 3) out of the top two. */
    @Test
    void hotPriorityCountFollowsConfiguredWeightsNotHardcodedNames() throws Exception {
        mvc.perform(post("/api/config/priorities").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Urgent","value":10}"""))
                .andExpect(status().isOk());

        String uid = mapper.readTree(mvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString()).get("id").asText();

        createViaApi("owned-urgent", "Urgent", uid);
        createViaApi("owned-critical", "Critical", uid);
        createViaApi("owned-high", "High", uid);

        mvc.perform(get("/api/insights/workload").param("groupId", groupId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                // Urgent(10) and Critical(4) are now the top two weights; High(3) no longer
                // qualifies, even though it was one of the two hardcoded literals before.
                .andExpect(jsonPath("$.owners[?(@.ownerId=='" + uid + "')].criticalHighCount").value(2));
    }

    @Test
    void healthCountsOverdueAndStaleAndDerivesAStage() throws Exception {
        seed("ITM-OD1", "High", ItemStatus.BACKLOG, 3, -10);   // overdue
        seed("ITM-OD2", "High", ItemStatus.BACKLOG, 3, -2);    // overdue
        seed("ITM-OK", "High", ItemStatus.BACKLOG, 3, 30);     // fine

        mvc.perform(get("/api/insights/health").param("groupId", groupId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overdue").value(2))
                .andExpect(jsonPath("$.neglect").value(2))
                .andExpect(jsonPath("$.stage").value(1));
    }

    @Test
    void requiresAuth() throws Exception {
        mvc.perform(get("/api/insights/needs-attention")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/insights/workload")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/insights/health")).andExpect(status().isUnauthorized());
    }

    private void createViaApi(String title, String priority, String ownerId) throws Exception {
        String owner = ownerId == null ? "" : ",\"ownerId\":\"" + ownerId + "\"";
        mvc.perform(post("/api/items").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"groupId":"%s","title":"%s","category":"Project","priority":"%s",
                                 "effortEstimate":{"value":30,"unit":"MINUTES"}%s}"""
                                .formatted(groupId, title, priority, owner)))
                .andExpect(status().isCreated());
    }
}
