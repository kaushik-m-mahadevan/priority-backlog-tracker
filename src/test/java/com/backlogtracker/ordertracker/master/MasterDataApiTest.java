package com.backlogtracker.ordertracker.master;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

import com.backlogtracker.commons.group.repository.GroupRepository;
import com.backlogtracker.commons.security.JwtService;
import com.backlogtracker.commons.user.domain.AccountStatus;
import com.backlogtracker.commons.user.domain.Role;
import com.backlogtracker.commons.user.domain.User;
import com.backlogtracker.commons.user.repository.UserRepository;
import com.backlogtracker.ordertracker.master.repository.BusinessConfigRepository;
import com.backlogtracker.ordertracker.master.repository.CreatorRepository;
import com.backlogtracker.ordertracker.master.repository.LocationCodeRepository;
import com.backlogtracker.ordertracker.master.repository.PresetOptionRepository;
import com.backlogtracker.ordertracker.master.repository.ShippingLanePresetRepository;
import com.backlogtracker.support.AuthTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class MasterDataApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired GroupRepository groups;
    @Autowired JwtService jwt;
    @Autowired BusinessConfigRepository businessConfigs;
    @Autowired CreatorRepository creators;
    @Autowired LocationCodeRepository locationCodes;
    @Autowired PresetOptionRepository presetOptions;
    @Autowired ShippingLanePresetRepository shippingLanes;

    private String token;
    private String outsiderToken;
    private String groupId;

    @BeforeEach
    void setUp() throws Exception {
        businessConfigs.deleteAll();
        creators.deleteAll();
        locationCodes.deleteAll();
        presetOptions.deleteAll();
        shippingLanes.deleteAll();
        for (String e : new String[] {"mdouter@ot.test"}) {
            users.findByEmailIgnoreCase(e).ifPresent(users::delete);
        }
        token = AuthTestSupport.devToken(mvc, mapper);
        groupId = AuthTestSupport.createGroup(mvc, mapper, token, "Crochet Co " + System.nanoTime());

        User outsider = users.save(User.builder().name("Outsider").email("mdouter@ot.test")
                .passwordHash("x").role(Role.USER).status(AccountStatus.ACTIVE).handle("mdouter").build());
        outsiderToken = jwt.issue(outsider);
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b, String t) {
        return b.header("Authorization", "Bearer " + t);
    }

    @Test
    void businessConfigSeedsSensibleDefaultsWithDistinctOrderTypeCodes() throws Exception {
        String body = mvc.perform(auth(get("/api/ordertracker/groups/" + groupId + "/business-config"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.mandatoryItemTypes.length()").value(2))
                .andExpect(jsonPath("$.workStages.length()").value(4))
                .andReturn().getResponse().getContentAsString();
        JsonNode cfg = mapper.readTree(body);
        String individual = cfg.get("individualOrderTypeCode").asText();
        String bulk = cfg.get("bulkOrderTypeCode").asText();
        org.assertj.core.api.Assertions.assertThat(individual).hasSize(2).isNotEqualTo(bulk);
        org.assertj.core.api.Assertions.assertThat(bulk).hasSize(2);
    }

    @Test
    void nonMemberCannotReadBusinessConfig() throws Exception {
        mvc.perform(auth(get("/api/ordertracker/groups/" + groupId + "/business-config"), outsiderToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void creatorProfileStartsAbsentThenCanBeSetUp() throws Exception {
        mvc.perform(auth(get("/api/ordertracker/groups/" + groupId + "/creators/me"), token))
                .andExpect(status().isOk())
                .andExpect(content().string(""));

        mvc.perform(auth(put("/api/ordertracker/groups/" + groupId + "/creators/me"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"baseLocation":"Bangalore","hoursAvailablePerDay":4}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseLocation").value("Bangalore"))
                .andExpect(jsonPath("$.locationCode").isNotEmpty())
                .andExpect(jsonPath("$.creatorCode").isNotEmpty())
                .andExpect(jsonPath("$.hoursAvailablePerDay").value(4.0));

        mvc.perform(auth(get("/api/ordertracker/groups/" + groupId + "/creators/me"), token))
                .andExpect(jsonPath("$.baseLocation").value("Bangalore"));
    }

    @Test
    void sameLocationNameReusesTheSameCodeAcrossCreators() throws Exception {
        mvc.perform(auth(put("/api/ordertracker/groups/" + groupId + "/creators/me"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"baseLocation":"Chennai","hoursAvailablePerDay":3}"""))
                .andExpect(status().isOk());

        // a second member of the same group, same city -> same location code
        User member2 = users.save(User.builder().name("Member Two").email("member2@ot.test")
                .passwordHash("x").role(Role.USER).status(AccountStatus.ACTIVE).handle("member2ot").build());
        groups.findById(groupId).ifPresent(g -> {
            g.getMemberIds().add(member2.getId());
            groups.save(g);
        });
        String member2Token = jwt.issue(member2);

        String body1 = mvc.perform(auth(get("/api/ordertracker/groups/" + groupId + "/creators/me"), token))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(auth(put("/api/ordertracker/groups/" + groupId + "/creators/me"), member2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"baseLocation":"chennai","hoursAvailablePerDay":2}"""))
                .andExpect(status().isOk());
        String body2 = mvc.perform(auth(get("/api/ordertracker/groups/" + groupId + "/creators/me"), member2Token))
                .andReturn().getResponse().getContentAsString();

        String code1 = mapper.readTree(body1).get("locationCode").asText();
        String code2 = mapper.readTree(body2).get("locationCode").asText();
        org.assertj.core.api.Assertions.assertThat(code1).isEqualTo(code2);

        users.deleteById(member2.getId());
    }

    @Test
    void packagingPresetsAndShippingLanesCanBeAddedListedAndRemoved() throws Exception {
        String presetBody = mvc.perform(auth(post("/api/ordertracker/groups/" + groupId + "/packaging-presets"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label":"Box","estimatedCost":40,"estimatedTimeHours":0.25}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.label").value("Box"))
                .andReturn().getResponse().getContentAsString();
        String presetId = mapper.readTree(presetBody).get("id").asText();

        mvc.perform(auth(get("/api/ordertracker/groups/" + groupId + "/packaging-presets"), token))
                .andExpect(jsonPath("$.length()").value(1));

        mvc.perform(auth(delete("/api/ordertracker/groups/" + groupId + "/packaging-presets/" + presetId), token))
                .andExpect(status().isNoContent());
        mvc.perform(auth(get("/api/ordertracker/groups/" + groupId + "/packaging-presets"), token))
                .andExpect(jsonPath("$.length()").value(0));

        String laneBody = mvc.perform(auth(post("/api/ordertracker/groups/" + groupId + "/shipping-lanes"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"originLocationCode":"001","destinationLocationCode":"002",
                                 "estimatedCost":150,"estimatedTimeHours":24,"note":"overnight"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.note").value("overnight"))
                .andReturn().getResponse().getContentAsString();
        String laneId = mapper.readTree(laneBody).get("id").asText();

        mvc.perform(auth(delete("/api/ordertracker/groups/" + groupId + "/shipping-lanes/" + laneId), token))
                .andExpect(status().isNoContent());
    }
}
