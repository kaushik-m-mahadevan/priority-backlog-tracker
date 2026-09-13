package com.backlogtracker.backlogtracker.ranking;

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

import com.backlogtracker.backlogtracker.item.repository.ItemRepository;
import com.backlogtracker.commons.group.repository.GroupRepository;
import com.backlogtracker.support.AuthTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class QuickWinsApiTest {

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
        groupId = AuthTestSupport.createGroup(mvc, mapper, token, "QW Test Group");
    }

    private void create(String title, String priority, int v, String unit) throws Exception {
        mvc.perform(post("/api/items").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"groupId":"%s","title":"%s","category":"Project","priority":"%s",
                                 "effortEstimate":{"value":%d,"unit":"%s"},
                                 "dueDate":"2026-06-01T00:00:00Z"}"""
                                .formatted(groupId, title, priority, v, unit)))
                .andExpect(status().isCreated());
    }

    @Test
    void surfacesSmallestEffortFirstRegardlessOfPriority() throws Exception {
        create("big-critical", "Critical", 5, "DAYS");
        create("tiny-low", "Low", 15, "MINUTES");
        create("small-medium", "Medium", 45, "MINUTES");

        mvc.perform(get("/api/items/quick-wins").param("groupId", groupId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].item.title").value("tiny-low"))
                .andExpect(jsonPath("$[1].item.title").value("small-medium"))
                .andExpect(jsonPath("$[2].item.title").value("big-critical"));
    }

    @Test
    void effortTieBrokenBySortScore() throws Exception {
        create("urgent", "Critical", 30, "MINUTES");
        create("meh", "Low", 30, "MINUTES");

        mvc.perform(get("/api/items/quick-wins").param("groupId", groupId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].item.title").value("urgent"));
    }

    @Test
    void limitAndAuth() throws Exception {
        create("a", "Low", 15, "MINUTES");
        create("b", "Low", 30, "MINUTES");

        mvc.perform(get("/api/items/quick-wins").param("groupId", groupId).param("limit", "1").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/items/quick-wins")).andExpect(status().isUnauthorized());
    }
}
