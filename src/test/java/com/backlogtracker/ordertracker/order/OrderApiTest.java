package com.backlogtracker.ordertracker.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import com.backlogtracker.commons.user.repository.UserRepository;
import com.backlogtracker.ordertracker.customer.repository.CustomerRepository;
import com.backlogtracker.ordertracker.master.repository.BusinessConfigRepository;
import com.backlogtracker.ordertracker.master.repository.CreatorRepository;
import com.backlogtracker.ordertracker.master.repository.PresetOptionRepository;
import com.backlogtracker.ordertracker.order.repository.OrderRepository;
import com.backlogtracker.support.AuthTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class OrderApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired OrderRepository orders;
    @Autowired CustomerRepository customers;
    @Autowired CreatorRepository creators;
    @Autowired BusinessConfigRepository businessConfigs;
    @Autowired PresetOptionRepository presetOptions;
    @Autowired MongoOperations mongo;

    private String token;
    private String groupId;
    private String customerId;
    private String creatorId;

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b) {
        return b.header("Authorization", "Bearer " + token);
    }

    @BeforeEach
    void setUp() throws Exception {
        orders.deleteAll();
        customers.deleteAll();
        creators.deleteAll();
        businessConfigs.deleteAll();
        presetOptions.deleteAll();

        token = AuthTestSupport.devToken(mvc, mapper);
        groupId = AuthTestSupport.createGroup(mvc, mapper, token, "Crochet Co " + System.nanoTime());

        // seed business config's default work stages (crocheting 1, assembly 2, packaging 3, shipment 4)
        mvc.perform(auth(get("/api/ordertracker/groups/" + groupId + "/business-config")))
                .andExpect(status().isOk());

        String creatorBody = mvc.perform(auth(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/ordertracker/groups/" + groupId + "/creators/me"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"baseLocation":"Bangalore","hoursAvailablePerDay":4}"""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        creatorId = mapper.readTree(creatorBody).get("id").asText();

        String customerBody = mvc.perform(auth(post("/api/ordertracker/groups/" + groupId + "/customers"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Priya Sharma","contactNumber":"+91 98765 43210",
                                 "acquisitionChannel":"INSTAGRAM"}"""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        customerId = mapper.readTree(customerBody).get("id").asText();
    }

    @Test
    void orderCanBeCreatedWithComputedNumberDueDateAndCost() throws Exception {
        String body = mvc.perform(auth(post("/api/ordertracker/groups/" + groupId + "/orders"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"%s","primaryCreatorId":"%s","description":"Amigurumi bear",
                                 "mandatoryItems":{"wool":"Merino, cream","needle":"4mm"},
                                 "addOns":["Gift wrap"],
                                 "stages":[{"stageKey":"crocheting","assigneeCreatorId":"%s","estimatedHours":4},
                                           {"stageKey":"assembly","assigneeCreatorId":"%s","estimatedHours":1},
                                           {"stageKey":"packaging","estimatedHours":0.5},
                                           {"stageKey":"shipment","estimatedHours":0}],
                                 "materialsCost":500}""".formatted(customerId, creatorId, creatorId, creatorId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNumber").value(org.hamcrest.Matchers.hasLength(14)))
                .andExpect(jsonPath("$.paymentStatus").value("UNPAID"))
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andExpect(jsonPath("$.overallCompletionPercent").value(0.0))
                .andReturn().getResponse().getContentAsString();

        JsonNode view = mapper.readTree(body);
        assertThat(view.get("totalCost").asDouble()).isGreaterThan(500.0);
        assertThat(view.get("computedDueDate").asText()).isNotEmpty();
    }

    @Test
    void stageProgressUpdatesRollUpIntoOverallCompletionPercent() throws Exception {
        String body = mvc.perform(auth(post("/api/ordertracker/groups/" + groupId + "/orders"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"%s","primaryCreatorId":"%s",
                                 "stages":[{"stageKey":"crocheting","estimatedHours":4},
                                           {"stageKey":"assembly","estimatedHours":0},
                                           {"stageKey":"packaging","estimatedHours":0},
                                           {"stageKey":"shipment","estimatedHours":0}],
                                 "materialsCost":100}""".formatted(customerId, creatorId)))
                .andReturn().getResponse().getContentAsString();
        String orderId = mapper.readTree(body).get("id").asText();

        mvc.perform(auth(patch("/api/ordertracker/groups/" + groupId + "/orders/" + orderId + "/stages/crocheting"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"completionFraction":0.5}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overallCompletionPercent").value(50.0));
    }

    @Test
    void paymentsAccumulateAndDerivePaymentStatusWithAmountEncryptedAtRest() throws Exception {
        String body = mvc.perform(auth(post("/api/ordertracker/groups/" + groupId + "/orders"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"%s","primaryCreatorId":"%s",
                                 "stages":[{"stageKey":"crocheting","estimatedHours":1},
                                           {"stageKey":"assembly","estimatedHours":0},
                                           {"stageKey":"packaging","estimatedHours":0},
                                           {"stageKey":"shipment","estimatedHours":0}],
                                 "materialsCost":0}""".formatted(customerId, creatorId)))
                .andReturn().getResponse().getContentAsString();
        String orderId = mapper.readTree(body).get("id").asText();
        double totalCost = mapper.readTree(body).get("totalCost").asDouble();

        mvc.perform(auth(post("/api/ordertracker/groups/" + groupId + "/orders/" + orderId + "/payments"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":%s,"mode":"UPI","note":"Full payment via UPI"}""".formatted(totalCost)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.payments[0].amount").value(totalCost))
                .andExpect(jsonPath("$.payments[0].note").value("Full payment via UPI"));

        Document raw = mongo.findOne(Query.query(Criteria.where("_id").is(orderId)), Document.class, "orderTrackerOrders");
        assertThat(raw).isNotNull();
        java.util.List<?> payments = raw.getList("payments", Document.class);
        Document payment = (Document) payments.get(0);
        assertThat(payment.getString("amount")).isNotEqualTo(Double.toString(totalCost));
        assertThat(payment.getString("note")).isNotEqualTo("Full payment via UPI");
    }

    @Test
    void statusCanBeSetFreelyAndEachChangeIsRecordedInTheChangeLog() throws Exception {
        String body = mvc.perform(auth(post("/api/ordertracker/groups/" + groupId + "/orders"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"%s","primaryCreatorId":"%s",
                                 "stages":[{"stageKey":"crocheting","estimatedHours":1},
                                           {"stageKey":"assembly","estimatedHours":0},
                                           {"stageKey":"packaging","estimatedHours":0},
                                           {"stageKey":"shipment","estimatedHours":0}],
                                 "materialsCost":0}""".formatted(customerId, creatorId)))
                .andReturn().getResponse().getContentAsString();
        String orderId = mapper.readTree(body).get("id").asText();

        // free-form: jump straight from RECEIVED to SHIPPED, no guard rejects the skip
        mvc.perform(auth(patch("/api/ordertracker/groups/" + groupId + "/orders/" + orderId + "/status"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"SHIPPED"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"))
                .andExpect(jsonPath("$.changeLog.length()").value(1))
                .andExpect(jsonPath("$.changeLog[0].field").value("status"))
                .andExpect(jsonPath("$.changeLog[0].oldValue").value("RECEIVED"))
                .andExpect(jsonPath("$.changeLog[0].newValue").value("SHIPPED"));

        mvc.perform(auth(patch("/api/ordertracker/groups/" + groupId + "/orders/" + orderId + "/stages/crocheting"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"completionFraction":1.0}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changeLog.length()").value(2));
    }
}
