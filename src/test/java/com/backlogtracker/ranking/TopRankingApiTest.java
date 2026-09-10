package com.backlogtracker.ranking;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.backlogtracker.item.repository.ItemRepository;
import com.backlogtracker.group.repository.GroupRepository;
import com.backlogtracker.support.AuthTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class TopRankingApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired ItemRepository items;
    @Autowired GroupRepository groups;

    private String token;
    private String groupId;

    @BeforeEach
    void setUp() throws Exception {
        items.deleteAll();
        token = AuthTestSupport.devToken(mvc, mapper);
        groups.deleteAll();
        groupId = AuthTestSupport.createGroup(mvc, mapper, token, "Top Test Group");
    }

    private String create(String title, String priority, int effortValue, String effortUnit,
                          String dueDate) throws Exception {
        String body = mvc.perform(post("/api/items").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"groupId":"%s","title":"%s","category":"Project","priority":"%s",
                                 "effortEstimate":{"value":%d,"unit":"%s"},"dueDate":"%s"}"""
                                .formatted(groupId, title, priority, effortValue, effortUnit, dueDate)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("id").asText();
    }

    @Test
    void topRanksCriticalUrgentQuickWorkFirst() throws Exception {
        create("urgent-critical", "Critical", 15, "MINUTES", "2026-01-05T00:00:00Z");
        create("someday-low", "Low", 20, "DAYS", "2027-01-01T00:00:00Z");
        create("mid", "Medium", 2, "HOURS", "2026-06-01T00:00:00Z");

        mvc.perform(get("/api/items/top").param("groupId", groupId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.pinnedCount").value(0))
                .andExpect(jsonPath("$.overPinned").value(false))
                .andExpect(jsonPath("$.items[0].item.title").value("urgent-critical"))
                .andExpect(jsonPath("$.items[0].item.pinned").value(false))
                .andExpect(jsonPath("$.items[2].item.title").value("someday-low"))
                .andExpect(jsonPath("$.items[0].sortScore").isNumber())
                .andExpect(jsonPath("$.items[0].priorityFactor").value(1.0));
    }

    @Test
    void limitCapsResults() throws Exception {
        for (int i = 0; i < 5; i++) {
            create("item-" + i, "Medium", 1, "HOURS", "2026-03-01T00:00:00Z");
        }
        mvc.perform(get("/api/items/top").param("groupId", groupId).param("limit", "2").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2));
    }

    @Test
    void pinnedItemLeadsRegardlessOfScore() throws Exception {
        create("urgent-critical", "Critical", 15, "MINUTES", "2026-01-05T00:00:00Z");
        String lowId = create("someday-low", "Low", 20, "DAYS", "2027-01-01T00:00:00Z");

        // pin the lowest-ranked item — it must jump to the front
        mvc.perform(post("/api/items/" + lowId + "/pin").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pinned").value(true));

        mvc.perform(get("/api/items/top").param("groupId", groupId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.pinnedCount").value(1))
                .andExpect(jsonPath("$.overPinned").value(false))
                .andExpect(jsonPath("$.items[0].item.title").value("someday-low"))
                .andExpect(jsonPath("$.items[0].item.pinned").value(true))
                .andExpect(jsonPath("$.items[1].item.title").value("urgent-critical"));

        // unpinning restores score order
        mvc.perform(delete("/api/items/" + lowId + "/pin").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(get("/api/items/top").param("groupId", groupId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.pinnedCount").value(0))
                .andExpect(jsonPath("$.items[0].item.title").value("urgent-critical"));
    }

    @Test
    void tooManyPinsAreCappedWithAWarning() throws Exception {
        for (int i = 0; i < 12; i++) {
            String id = create("p-" + i, "Medium", 1, "HOURS", "2026-03-01T00:00:00Z");
            mvc.perform(post("/api/items/" + id + "/pin").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }
        mvc.perform(get("/api/items/top").param("groupId", groupId).param("limit", "10")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.items.length()").value(10))
                .andExpect(jsonPath("$.pinnedCount").value(12))
                .andExpect(jsonPath("$.overPinned").value(true));
    }

    @Test
    void requiresAuth() throws Exception {
        mvc.perform(get("/api/items/top")).andExpect(status().isUnauthorized());
    }
}
