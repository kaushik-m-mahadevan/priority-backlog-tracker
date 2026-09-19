package com.backlogtracker.ordertracker.order;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoOperations;
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
import com.backlogtracker.ordertracker.master.repository.BusinessConfigRepository;
import com.backlogtracker.ordertracker.master.repository.CreatorRepository;
import com.backlogtracker.ordertracker.order.repository.OrderChangeLogRepository;
import com.backlogtracker.ordertracker.order.repository.OrderRepository;
import com.backlogtracker.support.AuthTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
abstract class OrderApiTestSupport {

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper mapper;
    @Autowired protected UserRepository users;
    @Autowired protected GroupRepository groups;
    @Autowired protected JwtService jwt;
    @Autowired protected OrderRepository orders;
    @Autowired protected OrderChangeLogRepository changeLogs;
    @Autowired protected CustomerRepository customers;
    @Autowired protected CreatorRepository creators;
    @Autowired protected BusinessConfigRepository businessConfigs;
    @Autowired protected MongoOperations mongo;

    protected String token;
    protected String groupId;
    protected String customerId;
    protected String creatorAId;
    protected String creatorBId;

    protected MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b, String t) {
        return b.header("Authorization", "Bearer " + t);
    }

    @BeforeEach
    void setUp() throws Exception {
        orders.deleteAll();
        changeLogs.deleteAll();
        customers.deleteAll();
        creators.deleteAll();
        businessConfigs.deleteAll();
        for (String e : new String[] {"orderapitest-b@ot.test"}) {
            users.findByEmailIgnoreCase(e).ifPresent(users::delete);
        }
        // the bootstrap admin's group membership accumulates across test classes sharing the
        // embedded Mongo — without this, a full-suite run can hit the 5-group cap and fail here.
        groups.deleteAll();

        token = AuthTestSupport.devToken(mvc, mapper);
        groupId = AuthTestSupport.createGroup(mvc, mapper, token, "Crochet Co " + System.nanoTime());

        String creatorABody = mvc.perform(auth(put("/api/ordertracker/groups/" + groupId + "/creators/me"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"baseLocation":"Bangalore","hoursAvailablePerDay":4}"""))
                .andReturn().getResponse().getContentAsString();
        creatorAId = mapper.readTree(creatorABody).get("id").asText();

        User userB = users.save(User.builder().name("Creator B").email("orderapitest-b@ot.test")
                .passwordHash("x").role(Role.USER).status(AccountStatus.ACTIVE).handle("orderapitestb").build());
        groups.findById(groupId).ifPresent(g -> {
            g.getMemberIds().add(userB.getId());
            groups.save(g);
        });
        String tokenB = jwt.issue(userB);
        String creatorBBody = mvc.perform(auth(put("/api/ordertracker/groups/" + groupId + "/creators/me"), tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"baseLocation":"Chennai","hoursAvailablePerDay":8}"""))
                .andReturn().getResponse().getContentAsString();
        creatorBId = mapper.readTree(creatorBBody).get("id").asText();

        String customerBody = mvc.perform(auth(post("/api/ordertracker/groups/" + groupId + "/customers"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Priya Sharma","acquisitionChannel":"INSTAGRAM"}"""))
                .andReturn().getResponse().getContentAsString();
        customerId = mapper.readTree(customerBody).get("id").asText();
    }

    // ---- helpers ----

    protected String createBasicIndividualOrder() throws Exception {
        String body = mvc.perform(auth(post("/api/ordertracker/groups/" + groupId + "/orders"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"%s","orderType":"INDIVIDUAL","createdByCreatorId":"%s",
                                 "itemName":"Amigurumi bear","orderReceivedDate":"2026-01-01T00:00:00Z",
                                 "mandatoryItems":[{"kind":"YARN","value":"Cream","quantity":1,"unitCost":100}],
                                 "craftingTimeHours":1}""".formatted(customerId, creatorAId)))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("id").asText();
    }

    protected String createBasicBulkOrder() throws Exception {
        String body = mvc.perform(auth(post("/api/ordertracker/groups/" + groupId + "/orders"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"%s","orderType":"BULK","createdByCreatorId":"%s",
                                 "itemName":"Mini succulent crochet pots","orderReceivedDate":"2026-01-01T00:00:00Z",
                                 "variants":[{"label":"Blue flower","quantity":20,
                                   "mandatoryItems":[{"kind":"YARN","value":"Blue","quantity":1,"unitCost":100}],
                                   "craftingTimeHours":1,
                                   "splitAllocation":[{"creatorId":"%s","quantityAssigned":20}]}]}"""
                                .formatted(customerId, creatorAId, creatorAId)))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("id").asText();
    }

    protected String firstVariantId(String orderId) throws Exception {
        String body = mvc.perform(get("/api/ordertracker/groups/" + groupId + "/orders/" + orderId)
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("bulkDetails").get("variants").get(0).get("variantId").asText();
    }
}
