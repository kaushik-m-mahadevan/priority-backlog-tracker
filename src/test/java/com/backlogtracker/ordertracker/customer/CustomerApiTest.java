package com.backlogtracker.ordertracker.customer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.backlogtracker.commons.security.JwtService;
import com.backlogtracker.commons.user.domain.AccountStatus;
import com.backlogtracker.commons.user.domain.Role;
import com.backlogtracker.commons.user.domain.User;
import com.backlogtracker.commons.user.repository.UserRepository;
import com.backlogtracker.ordertracker.customer.repository.CustomerRepository;
import com.backlogtracker.support.AuthTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
class CustomerApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired JwtService jwt;
    @Autowired CustomerRepository customers;
    @Autowired MongoOperations mongo;

    private String token;
    private String outsiderToken;
    private String groupId;

    @BeforeEach
    void setUp() throws Exception {
        customers.deleteAll();
        for (String e : new String[] {"custouter@ot.test"}) {
            users.findByEmailIgnoreCase(e).ifPresent(users::delete);
        }
        token = AuthTestSupport.devToken(mvc, mapper);
        groupId = AuthTestSupport.createGroup(mvc, mapper, token, "Crochet Co " + System.nanoTime());

        User outsider = users.save(User.builder().name("Outsider").email("custouter@ot.test")
                .passwordHash("x").role(Role.USER).status(AccountStatus.ACTIVE).handle("custouter").build());
        outsiderToken = jwt.issue(outsider);
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b, String t) {
        return b.header("Authorization", "Bearer " + t);
    }

    @Test
    void customerCanBeCreatedListedAndUpdatedWithPiiEncryptedAtRest() throws Exception {
        String body = mvc.perform(auth(post("/api/ordertracker/groups/" + groupId + "/customers"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Priya Sharma","contactNumber":"+91 98765 43210",
                                 "email":"priya@example.com","instagramHandle":"@priya.crochets",
                                 "acquisitionChannel":"INSTAGRAM","shippingAddress":"12 MG Road, Bangalore",
                                 "notes":"Prefers pastel colours"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Priya Sharma"))
                .andExpect(jsonPath("$.contactNumber").value("+91 98765 43210"))
                .andExpect(jsonPath("$.acquisitionChannel").value("INSTAGRAM"))
                .andReturn().getResponse().getContentAsString();
        String customerId = mapper.readTree(body).get("id").asText();

        mvc.perform(auth(get("/api/ordertracker/groups/" + groupId + "/customers"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mvc.perform(auth(put("/api/ordertracker/groups/" + groupId + "/customers/" + customerId), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Priya Sharma","contactNumber":"+91 98765 43210",
                                 "email":"priya@example.com","instagramHandle":"@priya.crochets",
                                 "acquisitionChannel":"REFERRAL","shippingAddress":"12 MG Road, Bangalore",
                                 "notes":"Now prefers earth tones"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acquisitionChannel").value("REFERRAL"))
                .andExpect(jsonPath("$.notes").value("Now prefers earth tones"));

        Document raw = mongo.findOne(Query.query(Criteria.where("_id").is(customerId)),
                Document.class, "orderTrackerCustomers");
        assertThat(raw).isNotNull();
        assertThat(raw.getString("name")).isNotEqualTo("Priya Sharma");
        assertThat(raw.getString("contactNumber")).isNotEqualTo("+91 98765 43210");
        assertThat(raw.getString("notes")).isNotEqualTo("Now prefers earth tones");
        assertThat(raw.getString("acquisitionChannel")).isEqualTo("REFERRAL");
    }

    @Test
    void nonMemberCannotAccessGroupsCustomers() throws Exception {
        mvc.perform(auth(get("/api/ordertracker/groups/" + groupId + "/customers"), outsiderToken))
                .andExpect(status().isForbidden());
        mvc.perform(auth(post("/api/ordertracker/groups/" + groupId + "/customers"), outsiderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Someone","acquisitionChannel":"WALK_IN"}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerFromAnotherGroupIsNotFound() throws Exception {
        String otherGroupId = AuthTestSupport.createGroup(mvc, mapper, token, "Other Group " + System.nanoTime());
        String body = mvc.perform(auth(post("/api/ordertracker/groups/" + groupId + "/customers"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Someone","acquisitionChannel":"WALK_IN"}"""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String customerId = mapper.readTree(body).get("id").asText();

        mvc.perform(auth(get("/api/ordertracker/groups/" + otherGroupId + "/customers/" + customerId), token))
                .andExpect(status().isNotFound());
    }
}
