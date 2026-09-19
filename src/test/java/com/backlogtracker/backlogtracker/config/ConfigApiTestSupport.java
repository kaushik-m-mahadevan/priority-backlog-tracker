package com.backlogtracker.backlogtracker.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
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

/** to-8: shared fixtures for ConfigApiTest's split (weights, priority-list management,
 *  category-list management were previously bundled into one mixed-concern file). */
@SpringBootTest
@AutoConfigureMockMvc
abstract class ConfigApiTestSupport {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired ConfigRepository configRepo;
    @Autowired ConfigHistoryRepository historyRepo;
    @Autowired ItemRepository items;
    @Autowired GroupRepository groups;

    protected String token;
    protected String groupId;

    protected static final String GOOD_WEIGHTS = """
            {"priorityWeight":0.5,"urgencyWeight":0.3,"effortWeight":0.2,
             "urgencyWindowDays":14,"staleThresholdDays":10,"buriedThresholdDays":30,
             "defaultDueDateOffsetDays":30,"effortCapDays":30,"maxGroupsPerUser":5,
             "buriedPriorityLevels":["Low"],
             "priorityValues":{"Critical":4,"High":3,"Medium":2,"Low":1}}""";

    @BeforeEach
    void setUp() throws Exception {
        configRepo.deleteAll();
        historyRepo.deleteAll();
        items.deleteAll();
        token = AuthTestSupport.devToken(mvc, mapper);
        groups.deleteAll();
        groupId = AuthTestSupport.createGroup(mvc, mapper, token, "Config Test Group");
    }

    protected MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b) {
        return b.header("Authorization", "Bearer " + token);
    }

    protected void createItem(String title, String category, String priority) throws Exception {
        mvc.perform(auth(post("/api/items")).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"groupId":"%s","title":"%s","category":"%s","priority":"%s",
                                 "effortEstimate":{"value":30,"unit":"MINUTES"}}"""
                                .formatted(groupId, title, category, priority)))
                .andExpect(status().isCreated());
    }
}
