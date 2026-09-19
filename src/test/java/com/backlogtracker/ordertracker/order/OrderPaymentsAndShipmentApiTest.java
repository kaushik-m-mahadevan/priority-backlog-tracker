package com.backlogtracker.ordertracker.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;

class OrderPaymentsAndShipmentApiTest extends OrderApiTestSupport {

    @Test
    void paymentsAccumulateAndDerivePaymentStatusWithAmountEncryptedAtRest() throws Exception {
        String orderId = createBasicIndividualOrder();
        String body = mvc.perform(get("/api/ordertracker/groups/" + groupId + "/orders/" + orderId)
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        double finalCost = mapper.readTree(body).get("costEstimate").get("finalCost").asDouble();

        mvc.perform(auth(post("/api/ordertracker/groups/" + groupId + "/orders/" + orderId + "/payments"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"FINAL","amount":%s,"mode":"UPI","note":"Paid in full"}""".formatted(finalCost)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("PAID_IN_FULL"))
                .andExpect(jsonPath("$.netPaid").value(finalCost));

        Document raw = mongo.findOne(Query.query(Criteria.where("_id").is(orderId)), Document.class, "orderTrackerOrders");
        assertThat(raw).isNotNull();
        java.util.List<?> payments = raw.getList("payments", Document.class);
        Document payment = (Document) payments.get(0);
        assertThat(payment.getString("amount")).isNotEqualTo(Double.toString(finalCost));
        assertThat(payment.getString("note")).isNotEqualTo("Paid in full");
    }

    @Test
    void shipmentPlanRoundTripsAndMarksStopsWithTrackingNumberEncrypted() throws Exception {
        String orderId = createBasicIndividualOrder();

        mvc.perform(auth(put("/api/ordertracker/groups/" + groupId + "/orders/" + orderId + "/shipment-plan"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stops":[{"stopOrder":1,"type":"FINAL_DELIVERY","originLocationCode":"659",
                                 "destinationLocationCode":"700","carrier":"India Post","trackingNumber":"TRK-1"}]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shipmentPlan[0].trackingNumber").value("TRK-1"));

        mvc.perform(auth(patch("/api/ordertracker/groups/" + groupId + "/orders/" + orderId + "/shipment-plan/0"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"deliveredConfirmed":true}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shipmentPlan[0].deliveredConfirmed").value(true));

        Document raw = mongo.findOne(Query.query(Criteria.where("_id").is(orderId)), Document.class, "orderTrackerOrders");
        Document stop = (Document) ((java.util.List<?>) raw.get("shipmentPlan", Document.class).get("stops")).get(0);
        assertThat(stop.getString("trackingNumber")).isNotEqualTo("TRK-1");
    }
}
