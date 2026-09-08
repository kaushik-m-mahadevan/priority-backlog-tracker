package com.backlogtracker.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Query;

import com.backlogtracker.counter.domain.Counter;
import com.backlogtracker.item.domain.Item;
import com.backlogtracker.item.repository.ItemRepository;
import com.backlogtracker.support.AuthTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class ItemApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired ItemRepository items;
    @Autowired MongoOperations mongo;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        items.deleteAll();
        mongo.remove(new Query(), Counter.class); // reset ITM- sequence for deterministic ids
        token = AuthTestSupport.devToken(mvc, mapper);
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b) {
        return b.header("Authorization", "Bearer " + token);
    }

    private static String createBody(String title, String category, String priority,
                                     int effortValue, String effortUnit) {
        return """
                {"title":"%s","category":"%s","priority":"%s",
                 "effortEstimate":{"value":%d,"unit":"%s"}}"""
                .formatted(title, category, priority, effortValue, effortUnit);
    }

    private JsonNode create(String body) throws Exception {
        String res = mvc.perform(auth(post("/api/items"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(res);
    }

    @Test
    void createsItemWithGeneratedIdDefaultsAndAudit() throws Exception {
        Instant before = Instant.now();
        JsonNode created = create(createBody("Write onboarding doc", "Research", "High", 30, "MINUTES"));

        assertThat(created.get("itemId").asText()).isEqualTo("ITM-001");
        assertThat(created.get("status").asText()).isEqualTo("BACKLOG");
        assertThat(created.get("scope").asText()).isEqualTo("SHARED");
        assertThat(created.get("effort").get("minutes").asLong()).isEqualTo(30);
        assertThat(created.get("createdBy").asText()).isNotBlank();
        assertThat(created.get("lastUpdatedBy").asText())
                .isEqualTo(created.get("createdBy").asText());

        Instant due = Instant.parse(created.get("dueDate").asText());
        // default = now + 30 days (config.defaultDueDateOffsetDays)
        assertThat(due).isBetween(before.plus(Duration.ofDays(29)),
                Instant.now().plus(Duration.ofDays(31)));
    }

    @Test
    void secondItemGetsNextSequentialId() throws Exception {
        create(createBody("A", "Project", "Low", 1, "HOURS"));
        JsonNode second = create(createBody("B", "Project", "Low", 1, "HOURS"));
        assertThat(second.get("itemId").asText()).isEqualTo("ITM-002");
    }

    @Test
    void rejectsInvalidEffort() throws Exception {
        mvc.perform(auth(post("/api/items")).contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("Bad", "Research", "High", 20, "MINUTES")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsUnknownCategoryAndPriority() throws Exception {
        mvc.perform(auth(post("/api/items")).contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("X", "Nonsense", "High", 15, "MINUTES")))
                .andExpect(status().isBadRequest());
        mvc.perform(auth(post("/api/items")).contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("X", "Research", "Whenever", 15, "MINUTES")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listReturnsLiveItems() throws Exception {
        create(createBody("Listed", "Admin-Ops", "Medium", 2, "HOURS"));
        mvc.perform(auth(get("/api/items")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Listed"));
    }

    @Test
    void updatesEditableFields() throws Exception {
        String id = create(createBody("Old", "Research", "Low", 15, "MINUTES")).get("id").asText();
        String due = Instant.now().plus(Duration.ofDays(5)).toString();

        mvc.perform(auth(put("/api/items/" + id)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"New title","category":"Project","priority":"Critical",
                                 "effortEstimate":{"value":2,"unit":"DAYS"},"dueDate":"%s"}"""
                                .formatted(due)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("New title"))
                .andExpect(jsonPath("$.priority").value("Critical"))
                .andExpect(jsonPath("$.effort.unit").value("DAYS"));
    }

    @Test
    void setsAssigneeAndNotesThenClearsNotes() throws Exception {
        String id = create(createBody("Notable", "Research", "Low", 15, "MINUTES")).get("id").asText();
        String due = Instant.now().plus(Duration.ofDays(5)).toString();

        mvc.perform(auth(put("/api/items/" + id)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Notable","category":"Research","priority":"Low",
                                 "effortEstimate":{"value":15,"unit":"MINUTES"},"dueDate":"%s",
                                 "ownerId":"u-42","notes":"call the vendor first"}"""
                                .formatted(due)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerId").value("u-42"))
                .andExpect(jsonPath("$.notes.content").value("call the vendor first"))
                .andExpect(jsonPath("$.notes.format").value("markdown"));

        mvc.perform(auth(put("/api/items/" + id)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Notable","category":"Research","priority":"Low",
                                 "effortEstimate":{"value":15,"unit":"MINUTES"},"dueDate":"%s",
                                 "notes":""}"""
                                .formatted(due)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes").doesNotExist())
                .andExpect(jsonPath("$.ownerId").doesNotExist());
    }

    @Test
    void rejectsStaleUpdateWithConflict() throws Exception {
        JsonNode created = create(createBody("Concurrent", "Project", "Low", 1, "HOURS"));
        String id = created.get("id").asText();
        long v0 = created.get("version").asLong();
        String due = Instant.now().plus(Duration.ofDays(3)).toString();

        String body = """
                {"title":"edit %s","category":"Project","priority":"Low",
                 "effortEstimate":{"value":1,"unit":"HOURS"},"dueDate":"%s","version":%d}""";

        mvc.perform(auth(put("/api/items/" + id)).contentType(MediaType.APPLICATION_JSON)
                        .content(body.formatted("one", due, v0)))
                .andExpect(status().isOk());

        // the version the client held (v0) is now stale
        mvc.perform(auth(put("/api/items/" + id)).contentType(MediaType.APPLICATION_JSON)
                        .content(body.formatted("two", due, v0)))
                .andExpect(status().isConflict());
    }

    @Test
    void togglesStatusBetweenLiveStates() throws Exception {
        String id = create(createBody("Flow", "Project", "High", 1, "HOURS")).get("id").asText();

        mvc.perform(auth(patch("/api/items/" + id + "/status"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"status":"IN_PROGRESS"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        mvc.perform(auth(patch("/api/items/" + id + "/status"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"status":"BACKLOG"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BACKLOG"));
    }

    @Test
    void rejectsTerminalOrUnknownStatus() throws Exception {
        String id = create(createBody("Flow", "Project", "High", 1, "HOURS")).get("id").asText();
        mvc.perform(auth(patch("/api/items/" + id + "/status"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"status":"RESOLVED"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownIdIs404() throws Exception {
        mvc.perform(auth(get("/api/items/does-not-exist")))
                .andExpect(status().isNotFound());
    }

    @Test
    void requiresAuth() throws Exception {
        mvc.perform(get("/api/items")).andExpect(status().isUnauthorized());
    }

    @Test
    void persistsToRepository() throws Exception {
        String itemId = create(createBody("Persisted", "Other", "Low", 15, "MINUTES"))
                .get("itemId").asText();
        Item saved = items.findByItemId(itemId).orElseThrow();
        assertThat(saved.getTitle()).isEqualTo("Persisted");
    }
}
