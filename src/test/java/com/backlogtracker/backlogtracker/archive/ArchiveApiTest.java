package com.backlogtracker.backlogtracker.archive;

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

import com.backlogtracker.backlogtracker.archive.repository.ArchivedItemRepository;
import com.backlogtracker.backlogtracker.item.repository.ItemRepository;
import com.backlogtracker.commons.group.repository.GroupRepository;
import com.backlogtracker.support.AuthTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class ArchiveApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired ItemRepository items;
    @Autowired ArchivedItemRepository archived;
    @Autowired GroupRepository groups;

    private String token;
    private String groupId;

    @BeforeEach
    void setUp() throws Exception {
        items.deleteAll();
        archived.deleteAll();
        token = AuthTestSupport.devToken(mvc, mapper);
        groups.deleteAll();
        groupId = AuthTestSupport.createGroup(mvc, mapper, token, "Archive Test Group");
    }

    private JsonNode createItem(String title) throws Exception {
        String res = mvc.perform(post("/api/items").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"groupId":"%s","title":"%s","category":"Project","priority":"High",
                                 "effortEstimate":{"value":30,"unit":"MINUTES"}}""".formatted(groupId, title)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(res);
    }

    @Test
    void completingMovesItemOutOfLiveTableIntoArchive() throws Exception {
        String id = createItem("Ship v1").get("id").asText();

        mvc.perform(post("/api/items/" + id + "/complete").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"terminalStatus":"RESOLVED"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.terminalStatus").value("RESOLVED"))
                .andExpect(jsonPath("$.completionDate").isNotEmpty())
                .andExpect(jsonPath("$.movedAt").isNotEmpty());

        // gone from live views
        mvc.perform(get("/api/items").param("groupId", groupId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/items/top").param("groupId", groupId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(get("/api/items/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());

        // present in the completed view
        mvc.perform(get("/api/archived").param("groupId", groupId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Ship v1"))
                .andExpect(jsonPath("$.content[0].terminalStatus").value("RESOLVED"));
    }

    @Test
    void archivedListIsNewestFirst() throws Exception {
        String a = createItem("first").get("id").asText();
        String b = createItem("second").get("id").asText();
        complete(a, "REJECTED");
        complete(b, "ARCHIVED");

        mvc.perform(get("/api/archived").param("groupId", groupId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.content[0].title").value("second"))
                .andExpect(jsonPath("$.content[1].title").value("first"));
    }

    @Test
    void unknownIdIs404() throws Exception {
        mvc.perform(post("/api/items/nope/complete").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"terminalStatus":"RESOLVED"}"""))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidTerminalStatusIs400() throws Exception {
        String id = createItem("x").get("id").asText();
        mvc.perform(post("/api/items/" + id + "/complete").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"terminalStatus":"DONE"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requiresAuth() throws Exception {
        mvc.perform(get("/api/archived")).andExpect(status().isUnauthorized());
    }

    private void complete(String id, String terminal) throws Exception {
        mvc.perform(post("/api/items/" + id + "/complete").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"terminalStatus\":\"" + terminal + "\"}"))
                .andExpect(status().isOk());
    }
}
