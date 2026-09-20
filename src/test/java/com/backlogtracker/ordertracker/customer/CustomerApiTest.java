package com.backlogtracker.ordertracker.customer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

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

import com.backlogtracker.commons.group.repository.GroupRepository;
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
    @Autowired GroupRepository groups;

    private String token;
    private String outsiderToken;
    private String groupId;

    @BeforeEach
    void setUp() throws Exception {
        customers.deleteAll();
        for (String e : new String[] {"custouter@ot.test"}) {
            users.findByEmailIgnoreCase(e).ifPresent(users::delete);
        }
        // the bootstrap admin's group membership accumulates across test classes sharing the
        // embedded Mongo — without this, a full-suite run can hit the 5-group cap and fail here.
        groups.deleteAll();
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
                                 "acquisitionChannel":"INSTAGRAM",
                                 "addresses":[{"label":"Home","address":"12 MG Road, Bangalore","isDefault":true}],
                                 "notes":"Prefers pastel colours"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Priya Sharma"))
                .andExpect(jsonPath("$.contactNumber").value("+91 98765 43210"))
                .andExpect(jsonPath("$.acquisitionChannel").value("INSTAGRAM"))
                .andExpect(jsonPath("$.addresses.length()").value(1))
                .andExpect(jsonPath("$.addresses[0].label").value("Home"))
                .andExpect(jsonPath("$.addresses[0].address").value("12 MG Road, Bangalore"))
                .andExpect(jsonPath("$.addresses[0].isDefault").value(true))
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
                                 "acquisitionChannel":"REFERRAL",
                                 "addresses":[{"label":"Home","address":"12 MG Road, Bangalore","isDefault":true}],
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
        @SuppressWarnings("unchecked")
        List<Document> rawAddresses = (List<Document>) raw.get("addresses");
        assertThat(rawAddresses).hasSize(1);
        assertThat(rawAddresses.get(0).getString("address")).isNotEqualTo("12 MG Road, Bangalore");
    }

    /** ad-6: a customer can have several saved addresses; at most one stays default even
     *  if the caller tries to mark more than one, and one gets promoted to default
     *  automatically if none was marked. */
    @Test
    void multipleAddressesEnforceAtMostOneDefault() throws Exception {
        String body = mvc.perform(auth(post("/api/ordertracker/groups/" + groupId + "/customers"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Anita Rao","acquisitionChannel":"WALK_IN",
                                 "addresses":[
                                   {"label":"Home","address":"1 First St","isDefault":true},
                                   {"label":"Work","address":"2 Second St","isDefault":true}
                                 ]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.addresses.length()").value(2))
                .andExpect(jsonPath("$.addresses[0].isDefault").value(true))
                .andExpect(jsonPath("$.addresses[1].isDefault").value(false))
                .andReturn().getResponse().getContentAsString();
        String customerId = mapper.readTree(body).get("id").asText();

        mvc.perform(auth(put("/api/ordertracker/groups/" + groupId + "/customers/" + customerId), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Anita Rao","acquisitionChannel":"WALK_IN",
                                 "addresses":[
                                   {"label":"Home","address":"1 First St","isDefault":false},
                                   {"label":"Work","address":"2 Second St","isDefault":false}
                                 ]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.addresses[0].isDefault").value(true))
                .andExpect(jsonPath("$.addresses[1].isDefault").value(false));
    }

    /** Regression coverage for CustomerService.search — the entire blind-index
     *  duplicate-detection feature had zero tests at any layer before this. */
    @Test
    void searchFindsAnExistingCustomerByEmailOrInstagramOrPhoneCaseAndWhitespaceInsensitively() throws Exception {
        mvc.perform(auth(post("/api/ordertracker/groups/" + groupId + "/customers"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Priya Sharma","contactNumber":"9876543210",
                                 "email":"Priya@Example.com","instagramHandle":"@Priya.Crochets",
                                 "acquisitionChannel":"INSTAGRAM"}"""))
                .andExpect(status().isOk());

        // Case/whitespace differences from how it was originally entered still match, since
        // the blind index normalizes (trim + lowercase) before hashing.
        mvc.perform(auth(get("/api/ordertracker/groups/" + groupId + "/customers/search")
                        .param("email", "  priya@example.com  "), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Priya Sharma"));

        mvc.perform(auth(get("/api/ordertracker/groups/" + groupId + "/customers/search")
                        .param("instagramHandle", "@PRIYA.CROCHETS"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mvc.perform(auth(get("/api/ordertracker/groups/" + groupId + "/customers/search")
                        .param("contactNumber", "9876543210"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void searchReturnsNothingWhenNoFieldMatchesAndDedupesAMultiFieldMatch() throws Exception {
        String body = mvc.perform(auth(post("/api/ordertracker/groups/" + groupId + "/customers"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Rahul Nair","contactNumber":"9123456780",
                                 "email":"rahul@example.com","acquisitionChannel":"REFERRAL"}"""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String customerId = mapper.readTree(body).get("id").asText();

        mvc.perform(auth(get("/api/ordertracker/groups/" + groupId + "/customers/search")
                        .param("email", "nobody@example.com"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // Matching on both email AND phone for the same customer must not return them twice.
        mvc.perform(auth(get("/api/ordertracker/groups/" + groupId + "/customers/search")
                        .param("email", "rahul@example.com")
                        .param("contactNumber", "9123456780"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(customerId));
    }

    @Test
    void searchIsIsolatedPerGroupAndRequiresMembership() throws Exception {
        mvc.perform(auth(post("/api/ordertracker/groups/" + groupId + "/customers"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Pooja Desai","email":"pooja@example.com","acquisitionChannel":"REFERRAL"}"""))
                .andExpect(status().isOk());

        String otherGroupId = AuthTestSupport.createGroup(mvc, mapper, token, "Other Search Group " + System.nanoTime());
        mvc.perform(auth(get("/api/ordertracker/groups/" + otherGroupId + "/customers/search")
                        .param("email", "pooja@example.com"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mvc.perform(auth(get("/api/ordertracker/groups/" + groupId + "/customers/search")
                        .param("email", "pooja@example.com"), outsiderToken))
                .andExpect(status().isForbidden());
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
