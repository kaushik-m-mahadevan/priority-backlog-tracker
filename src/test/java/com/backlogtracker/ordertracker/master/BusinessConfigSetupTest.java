package com.backlogtracker.ordertracker.master;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.backlogtracker.commons.group.repository.GroupRepository;
import com.backlogtracker.commons.security.JwtService;
import com.backlogtracker.commons.user.repository.UserRepository;
import com.backlogtracker.ordertracker.master.repository.BusinessConfigRepository;
import com.backlogtracker.support.AuthTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Covers the new-business setup-wizard gating flag — see
 *  {@code BusinessConfig.setupComplete} and {@code BusinessConfigService.startSetup}. */
@SpringBootTest
@AutoConfigureMockMvc
class BusinessConfigSetupTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired GroupRepository groups;
    @Autowired JwtService jwt;
    @Autowired BusinessConfigRepository businessConfigs;

    private String token;
    private String groupId;

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b, String t) {
        return b.header("Authorization", "Bearer " + t);
    }

    @BeforeEach
    void setUp() throws Exception {
        businessConfigs.deleteAll();
        groups.deleteAll();

        token = AuthTestSupport.devToken(mvc, mapper);
        groupId = AuthTestSupport.createGroup(mvc, mapper, token, "Setup Test " + System.nanoTime());
    }

    @Test
    void aBusinessDefaultsToSetupComplete() throws Exception {
        mvc.perform(get("/api/ordertracker/groups/" + groupId + "/business-config").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.setupComplete").value(true));
    }

    @Test
    void startSetupFlipsItToIncompleteAndCompleteSetupFlipsItBack() throws Exception {
        mvc.perform(auth(post("/api/ordertracker/groups/" + groupId + "/business-config/setup/start"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.setupComplete").value(false));

        mvc.perform(get("/api/ordertracker/groups/" + groupId + "/business-config").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.setupComplete").value(false));

        mvc.perform(auth(post("/api/ordertracker/groups/" + groupId + "/business-config/setup/complete"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.setupComplete").value(true));
    }
}
