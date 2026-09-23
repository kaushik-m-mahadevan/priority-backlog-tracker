package com.backlogtracker.ordertracker.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * ad-2 end-to-end: usage logging, manual/auto sync, and live reservation — all through real
 * HTTP endpoints, so the cross-applet {@code InventoryUsageConsumer}/{@code
 * ReservationProvider} bean wiring between Order Tracker and Material Inventory (both in
 * the same Spring context — no real network hop) is exercised for real, not mocked.
 */
class UsageLogAndReservationApiTest extends OrderApiTestSupport {

    /** Each test creates an extra Material Inventory group beyond {@code OrderApiTestSupport}'s
     *  own — {@code groups.deleteAll()} in that base class's {@code setUp()} only resets
     *  before the *next* OrderApiTestSupport-based test, not before an unrelated test class
     *  later in a full-suite run, so a leftover group here can push the shared bootstrap
     *  admin account over another test's own group-count cap (same class of issue that base
     *  class's own {@code setUp()} comment already flags). */
    @AfterEach
    void tearDownExtraGroups() {
        groups.deleteAll();
    }

    private String createInventoryGroupLinkedAndSeeded(double startingSkeins) throws Exception {
        // OrderApiTestSupport's shared `groupId` is created by the generic AuthTestSupport
        // helper with no appletKey, so it defaults to backlogtracker — harmless everywhere
        // else, since nothing in Order Tracker's own code reads Group.appletKey, but
        // GroupLinkService does (it stores each side's real appletKey on the link), so
        // linking needs the group's appletKey corrected to ordertracker first.
        groups.findById(groupId).ifPresent(g -> {
            g.setAppletKey(com.backlogtracker.commons.group.domain.Group.APPLET_ORDER_TRACKER);
            groups.save(g);
        });

        String invGroupBody = mvc.perform(auth(post("/api/groups?appletKey=materialinventory"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Yarn Stash\"}"))
                .andReturn().getResponse().getContentAsString();
        String invGroupId = mapper.readTree(invGroupBody).get("id").asText();

        mvc.perform(auth(post("/api/groups/" + groupId + "/links"), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"groupId\":\"" + invGroupId + "\"}"));

        String yarnBody = mvc.perform(auth(post("/api/materialinventory/groups/" + invGroupId + "/yarn-types"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"brand":"Sample","thickness":"Worsted","colour":"Cream"}"""))
                .andReturn().getResponse().getContentAsString();
        String yarnTypeId = mapper.readTree(yarnBody).get("id").asText();

        mvc.perform(auth(put("/api/materialinventory/groups/" + invGroupId + "/inventory/mine/" + yarnTypeId), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":" + startingSkeins + "}"));

        this.inventoryGroupId = invGroupId;
        return yarnTypeId;
    }

    private String inventoryGroupId;

    private String createIndividualOrderWithYarn(String yarnTypeId, double quantity) throws Exception {
        String body = mvc.perform(auth(post("/api/ordertracker/groups/" + groupId + "/orders"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"%s","orderType":"INDIVIDUAL","createdByCreatorId":"%s",
                                 "itemName":"Amigurumi bear","orderReceivedDate":"2026-01-01T00:00:00Z",
                                 "mandatoryItems":[{"kind":"YARN","value":"Cream","quantity":%s,"unitCost":100,
                                   "linkedYarnTypeId":"%s"}],
                                 "craftingTimeHours":1}""".formatted(customerId, creatorAId, quantity, yarnTypeId)))
                .andReturn().getResponse().getContentAsString();
        String orderId = mapper.readTree(body).get("id").asText();
        // assign the crafting stage to creatorA so the reservation attributes to them
        mvc.perform(auth(patch("/api/ordertracker/groups/" + groupId + "/orders/" + orderId + "/stage-assignments/crocheting"), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"assignedCreatorId\":\"" + creatorAId + "\",\"unitsCompleted\":0}"));
        return orderId;
    }

    private double reservedFor(String yarnTypeId) throws Exception {
        String body = mvc.perform(auth(get("/api/materialinventory/groups/" + inventoryGroupId + "/inventory/mine"), token))
                .andReturn().getResponse().getContentAsString();
        for (JsonNode e : mapper.readTree(body)) {
            if (e.get("yarnTypeId").asText().equals(yarnTypeId)) {
                return e.get("reserved").asDouble();
            }
        }
        return 0.0;
    }

    private double quantityFor(String yarnTypeId) throws Exception {
        String body = mvc.perform(auth(get("/api/materialinventory/groups/" + inventoryGroupId + "/inventory/mine"), token))
                .andReturn().getResponse().getContentAsString();
        for (JsonNode e : mapper.readTree(body)) {
            if (e.get("yarnTypeId").asText().equals(yarnTypeId)) {
                return e.get("quantity").asDouble();
            }
        }
        return 0.0;
    }

    @Test
    void individualOrderReservesYarnAndUsageLoggingReducesTheReservation() throws Exception {
        String yarnTypeId = createInventoryGroupLinkedAndSeeded(10);
        createIndividualOrderWithYarn(yarnTypeId, 1.0);

        assertThat(reservedFor(yarnTypeId)).isEqualTo(1.0);
        assertThat(quantityFor(yarnTypeId)).isEqualTo(10.0); // manual sync by default: real stock untouched
    }

    @Test
    void manualSyncPreviewThenApplyDecrementsRealInventoryAndMarksEntriesSynced() throws Exception {
        String yarnTypeId = createInventoryGroupLinkedAndSeeded(10);
        String orderId = createIndividualOrderWithYarn(yarnTypeId, 1.0);

        String addBody = mvc.perform(auth(post("/api/ordertracker/groups/" + groupId + "/orders/" + orderId + "/usage-log"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"yarnTypeId\":\"" + yarnTypeId + "\",\"quantity\":0.5}"))
                .andReturn().getResponse().getContentAsString();
        JsonNode usageEntries = mapper.readTree(addBody).get("usageLogEntries");
        assertThat(usageEntries).hasSize(1);
        assertThat(usageEntries.get(0).get("synced").asBoolean()).isFalse();

        // reservation eats the usage first: 1.0 planned - 0.5 used = 0.5 still outstanding
        assertThat(reservedFor(yarnTypeId)).isEqualTo(0.5);
        assertThat(quantityFor(yarnTypeId)).isEqualTo(10.0); // still pending, real stock untouched

        String previewBody = mvc.perform(auth(get("/api/ordertracker/groups/" + groupId + "/inventory-sync/preview"), token))
                .andReturn().getResponse().getContentAsString();
        assertThat(mapper.readTree(previewBody).get(yarnTypeId).asDouble()).isEqualTo(0.5);
        assertThat(quantityFor(yarnTypeId)).isEqualTo(10.0); // preview never mutates

        mvc.perform(auth(post("/api/ordertracker/groups/" + groupId + "/inventory-sync/apply"), token));
        assertThat(quantityFor(yarnTypeId)).isEqualTo(9.5);

        String orderBody = mvc.perform(get("/api/ordertracker/groups/" + groupId + "/orders/" + orderId)
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        assertThat(mapper.readTree(orderBody).get("usageLogEntries").get(0).get("synced").asBoolean()).isTrue();
    }

    @Test
    void autoSyncAppliesImmediatelyAndRemovingASyncedEntryRestoresTheQuantity() throws Exception {
        String yarnTypeId = createInventoryGroupLinkedAndSeeded(10);
        String orderId = createIndividualOrderWithYarn(yarnTypeId, 1.0);

        mvc.perform(auth(patch("/api/ordertracker/groups/" + groupId + "/creators/me/sync-setting"), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"autoSyncInventory\":true}"));

        String addBody = mvc.perform(auth(post("/api/ordertracker/groups/" + groupId + "/orders/" + orderId + "/usage-log"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"yarnTypeId\":\"" + yarnTypeId + "\",\"quantity\":0.25}"))
                .andReturn().getResponse().getContentAsString();
        JsonNode entry = mapper.readTree(addBody).get("usageLogEntries").get(0);
        assertThat(entry.get("synced").asBoolean()).isTrue();
        assertThat(quantityFor(yarnTypeId)).isEqualTo(9.75);

        String entryId = entry.get("entryId").asText();
        mvc.perform(auth(delete("/api/ordertracker/groups/" + groupId + "/orders/" + orderId + "/usage-log/" + entryId), token));
        assertThat(quantityFor(yarnTypeId)).isEqualTo(10.0);
    }

    @Test
    void bulkOrderSplitsReservationProportionallyAcrossCreators() throws Exception {
        String yarnTypeId = createInventoryGroupLinkedAndSeeded(10);
        String body = mvc.perform(auth(post("/api/ordertracker/groups/" + groupId + "/orders"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"%s","orderType":"BULK","createdByCreatorId":"%s",
                                 "itemName":"Coasters","orderReceivedDate":"2026-01-01T00:00:00Z",
                                 "variants":[{"label":"Blue","quantity":8,
                                   "mandatoryItems":[{"kind":"YARN","value":"Blue","quantity":1,"unitCost":100,
                                     "linkedYarnTypeId":"%s"}],
                                   "craftingTimeHours":1,
                                   "splitAllocation":[{"creatorId":"%s","quantityAssigned":5},
                                                       {"creatorId":"%s","quantityAssigned":3}]}]}"""
                                .formatted(customerId, creatorAId, yarnTypeId, creatorAId, creatorBId)))
                .andReturn().getResponse().getContentAsString();
        assertThat(mapper.readTree(body).get("id").asText()).isNotBlank();

        // variant needs 1 skein/unit * 8 units = 8 total; split 5/3 → 5 reserved from Alex
        assertThat(reservedFor(yarnTypeId)).isEqualTo(5.0);
    }

    @Test
    void cancellingAnOrderReleasesItsReservation() throws Exception {
        String yarnTypeId = createInventoryGroupLinkedAndSeeded(10);
        String orderId = createIndividualOrderWithYarn(yarnTypeId, 1.0);
        assertThat(reservedFor(yarnTypeId)).isEqualTo(1.0);

        mvc.perform(auth(post("/api/ordertracker/groups/" + groupId + "/orders/" + orderId + "/cancel"), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Customer changed their mind\"}"));

        assertThat(reservedFor(yarnTypeId)).isEqualTo(0.0);
    }
}
