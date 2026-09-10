package com.backlogtracker.ranking;

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

    private void create(String title, String priority, int effortValue, String effortUnit,
                        String dueDate) throws Exception {
        mvc.perform(post("/api/items").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"groupId":"%s","title":"%s","category":"Project","priority":"%s",
                                 "effortEstimate":{"value":%d,"unit":"%s"},"dueDate":"%s"}"""
                                .formatted(groupId, title, priority, effortValue, effortUnit, dueDate)))
                .andExpect(status().isCreated());
    }

    @Test
    void topRanksCriticalUrgentQuickWorkFirst() throws Exception {
        create("urgent-critical", "Critical", 15, "MINUTES", "2026-01-05T00:00:00Z");
        create("someday-low", "Low", 20, "DAYS", "2027-01-01T00:00:00Z");
        create("mid", "Medium", 2, "HOURS", "2026-06-01T00:00:00Z");

        mvc.perform(get("/api/items/top").param("groupId", groupId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].item.title").value("urgent-critical"))
                .andExpect(jsonPath("$[2].item.title").value("someday-low"))
                .andExpect(jsonPath("$[0].sortScore").isNumber())
                .andExpect(jsonPath("$[0].priorityFactor").value(1.0));
    }

    @Test
    void limitCapsResults() throws Exception {
        for (int i = 0; i < 5; i++) {
            create("item-" + i, "Medium", 1, "HOURS", "2026-03-01T00:00:00Z");
        }
        mvc.perform(get("/api/items/top").param("groupId", groupId).param("limit", "2").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void requiresAuth() throws Exception {
        mvc.perform(get("/api/items/top")).andExpect(status().isUnauthorized());
    }
}
