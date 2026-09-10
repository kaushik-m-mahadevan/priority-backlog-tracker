package com.backlogtracker.archive;

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

import com.backlogtracker.group.repository.GroupRepository;
import com.backlogtracker.item.repository.ItemRepository;
import com.backlogtracker.security.JwtService;
import com.backlogtracker.support.AuthTestSupport;
import com.backlogtracker.user.domain.AccountStatus;
import com.backlogtracker.user.domain.Role;
import com.backlogtracker.user.domain.User;
import com.backlogtracker.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class ArchiveRequestApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired ItemRepository items;
    @Autowired GroupRepository groups;
    @Autowired UserRepository users;
    @Autowired JwtService jwt;

    private String tokenA; // group creator (dev admin)
    private String tokenB; // second member
    private String groupId;

    @BeforeEach
    void setUp() throws Exception {
        items.deleteAll();
        groups.deleteAll();
        users.findByEmailIgnoreCase("arb@demo.test").ifPresent(users::delete);

        tokenA = AuthTestSupport.devToken(mvc, mapper);
        groupId = AuthTestSupport.createGroup(mvc, mapper, tokenA, "Archive Vote Group");

        User b = users.save(User.builder().name("Bea").email("arb@demo.test")
                .passwordHash("x").role(Role.USER).status(AccountStatus.ACTIVE).handle("bea").build());
        tokenB = jwt.issue(b);
    }

    private String createItem(String title, String token) throws Exception {
        String body = mvc.perform(post("/api/items").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"groupId":"%s","title":"%s","category":"Project","priority":"Medium",
                                 "effortEstimate":{"value":1,"unit":"HOURS"},"dueDate":"2026-06-01T00:00:00Z"}"""
                                .formatted(groupId, title)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("id").asText();
    }

    private void addB() throws Exception {
        mvc.perform(post("/api/groups/" + groupId + "/invites").header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"to\":\"arb@demo.test\"}"))
                .andExpect(status().isCreated());
        String inbox = mvc.perform(get("/api/notifications").header("Authorization", "Bearer " + tokenB))
                .andReturn().getResponse().getContentAsString();
        String nid = mapper.readTree(inbox).get("items").get(0).get("id").asText();
        mvc.perform(post("/api/notifications/" + nid + "/accept").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk());
    }

    @Test
    void soloGroupArchivesImmediately() throws Exception {
        String id = createItem("solo archive", tokenA);
        mvc.perform(post("/api/items/" + id + "/archive-requests").header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        mvc.perform(get("/api/items/" + id).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());
    }

    @Test
    void multiMemberNeedsEveryApprovalThenArchives() throws Exception {
        addB();
        String id = createItem("shared archive", tokenA);

        String rq = mvc.perform(post("/api/items/" + id + "/archive-requests")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"done with this\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        String rqId = mapper.readTree(rq).get("id").asText();

        // still live; B has an ARCHIVE_REQUEST notification
        mvc.perform(get("/api/items/" + id).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());
        mvc.perform(get("/api/notifications").header("Authorization", "Bearer " + tokenB))
                .andExpect(jsonPath("$.items[0].type").value("ARCHIVE_REQUEST"))
                .andExpect(jsonPath("$.pending").value(1));

        // B approves -> unanimous -> archived
        mvc.perform(post("/api/archive-requests/" + rqId + "/approve").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        mvc.perform(get("/api/items/" + id).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());
        // B's actionable badge is clear again
        mvc.perform(get("/api/notifications").header("Authorization", "Bearer " + tokenB))
                .andExpect(jsonPath("$.pending").value(0));
    }

    @Test
    void oneRejectionKillsTheRequest() throws Exception {
        addB();
        String id = createItem("keep this", tokenA);
        String rqId = mapper.readTree(mvc.perform(post("/api/items/" + id + "/archive-requests")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andReturn().getResponse().getContentAsString()).get("id").asText();

        mvc.perform(post("/api/archive-requests/" + rqId + "/reject").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
        // item survives
        mvc.perform(get("/api/items/" + id).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());
        // both members got an informational result notice
        mvc.perform(get("/api/notifications").header("Authorization", "Bearer " + tokenA))
                .andExpect(jsonPath("$.items[0].type").value("ARCHIVE_RESULT"));
    }

    @Test
    void onlyOneOpenRequestPerItem() throws Exception {
        addB();
        String id = createItem("busy item", tokenA);
        mvc.perform(post("/api/items/" + id + "/archive-requests").header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/items/" + id + "/archive-requests").header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict());
    }

    @Test
    void directArchiveIsBlockedForMultiMemberGroups() throws Exception {
        addB();
        String id = createItem("no shortcut", tokenA);
        mvc.perform(post("/api/items/" + id + "/complete").header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"terminalStatus\":\"ARCHIVED\"}"))
                .andExpect(status().isConflict());
        // RESOLVED still works directly
        mvc.perform(post("/api/items/" + id + "/complete").header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"terminalStatus\":\"RESOLVED\"}"))
                .andExpect(status().isOk());
    }
}
