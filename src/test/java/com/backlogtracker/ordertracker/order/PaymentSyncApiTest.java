package com.backlogtracker.ordertracker.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.backlogtracker.commons.group.domain.Group;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * ad-1 end-to-end: recording/removing a payment on an order auto-creates/removes the
 * matching Finance Tracker ledger row through the new commons.finance.PaymentSyncConsumer
 * bean — real HTTP round-trip through both applets' controllers in one Spring context, same
 * discipline as ad-2's UsageLogAndReservationApiTest.
 */
class PaymentSyncApiTest extends OrderApiTestSupport {

    private String financeGroupId;

    @AfterEach
    void tearDownExtraGroup() {
        groups.deleteAll();
    }

    private String linkFinanceGroup() throws Exception {
        // OrderApiTestSupport's shared `groupId` defaults to appletKey=backlogtracker (a
        // pre-existing test-helper quirk, harmless everywhere else since nothing in Order
        // Tracker's own code reads Group.appletKey) — GroupLinkService does, so it needs
        // correcting before a link records the right appletKey on each side.
        groups.findById(groupId).ifPresent(g -> {
            g.setAppletKey(Group.APPLET_ORDER_TRACKER);
            groups.save(g);
        });
        String financeBody = mvc.perform(auth(post("/api/groups?appletKey=financetracker"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Money Matters\"}"))
                .andReturn().getResponse().getContentAsString();
        String financeGroupId = mapper.readTree(financeBody).get("id").asText();

        mvc.perform(auth(post("/api/groups/" + groupId + "/links"), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"groupId\":\"" + financeGroupId + "\"}"));

        this.financeGroupId = financeGroupId;
        return financeGroupId;
    }

    private JsonNode ledgerEntries() throws Exception {
        String body = mvc.perform(auth(get("/api/financetracker/groups/" + financeGroupId + "/ledger"), token))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body);
    }

    @Test
    void recordingAPaymentAutoCreatesADebitCustomerCreditMemberLedgerRow() throws Exception {
        linkFinanceGroup();
        String orderId = createBasicIndividualOrder();

        mvc.perform(auth(post("/api/ordertracker/groups/" + groupId + "/orders/" + orderId + "/payments"), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"type":"ADVANCE","amount":500,"mode":"UPI","note":"Advance"}"""));

        JsonNode entries = ledgerEntries();
        assertThat(entries).hasSize(1);
        JsonNode entry = entries.get(0);
        assertThat(entry.get("amount").asDouble()).isEqualTo(500.0);
        assertThat(entry.get("debit").get("type").asText()).isEqualTo("CUSTOMER");
        assertThat(entry.get("credit").get("type").asText()).isEqualTo("MEMBER");
        assertThat(entry.get("sourceRef").asText()).startsWith(orderId + ":");

        String orderBody = mvc.perform(auth(get("/api/ordertracker/groups/" + groupId + "/orders/" + orderId), token))
                .andReturn().getResponse().getContentAsString();
        String orderNumber = mapper.readTree(orderBody).get("orderNumber").asText();
        assertThat(entry.get("orderReference").asText()).isEqualTo(orderNumber);
    }

    @Test
    void aRefundReversesTheDebitCreditDirection() throws Exception {
        linkFinanceGroup();
        String orderId = createBasicIndividualOrder();

        mvc.perform(auth(post("/api/ordertracker/groups/" + groupId + "/orders/" + orderId + "/payments"), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"type":"REFUND","amount":200,"mode":"UPI"}"""));

        JsonNode entry = ledgerEntries().get(0);
        assertThat(entry.get("debit").get("type").asText()).isEqualTo("MEMBER");
        assertThat(entry.get("credit").get("type").asText()).isEqualTo("CUSTOMER");
    }

    @Test
    void receivedByBusinessCreditsTheBusinessAccountInstead() throws Exception {
        linkFinanceGroup();
        String orderId = createBasicIndividualOrder();

        mvc.perform(auth(post("/api/ordertracker/groups/" + groupId + "/orders/" + orderId + "/payments"), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"type":"ADVANCE","amount":500,"mode":"UPI","receivedBy":"BUSINESS"}"""));

        JsonNode entry = ledgerEntries().get(0);
        assertThat(entry.get("credit").get("type").asText()).isEqualTo("BUSINESS");
    }

    @Test
    void removingAPaymentDeletesTheMatchingLedgerRow() throws Exception {
        linkFinanceGroup();
        String orderId = createBasicIndividualOrder();

        String orderBody = mvc.perform(auth(post("/api/ordertracker/groups/" + groupId + "/orders/" + orderId + "/payments"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"ADVANCE","amount":500,"mode":"UPI"}"""))
                .andReturn().getResponse().getContentAsString();
        String paymentId = mapper.readTree(orderBody).get("payments").get(0).get("paymentId").asText();
        assertThat(ledgerEntries()).hasSize(1);

        mvc.perform(auth(delete("/api/ordertracker/groups/" + groupId + "/orders/" + orderId + "/payments/" + paymentId), token));

        assertThat(ledgerEntries()).isEmpty();
    }

    @Test
    void backfillSyncsEveryHistoricalPaymentIdempotently() throws Exception {
        String orderId = createBasicIndividualOrder();
        // record the payment BEFORE the finance group is linked — nothing to sync to yet
        mvc.perform(auth(post("/api/ordertracker/groups/" + groupId + "/orders/" + orderId + "/payments"), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"type":"ADVANCE","amount":500,"mode":"UPI"}"""));

        linkFinanceGroup();
        assertThat(ledgerEntries()).isEmpty(); // recording happened before the link existed

        mvc.perform(auth(post("/api/ordertracker/groups/" + groupId + "/finance-sync/backfill"), token));
        assertThat(ledgerEntries()).hasSize(1);

        // re-running the backfill must not double the row
        mvc.perform(auth(post("/api/ordertracker/groups/" + groupId + "/finance-sync/backfill"), token));
        assertThat(ledgerEntries()).hasSize(1);
    }
}
