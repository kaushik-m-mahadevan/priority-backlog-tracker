import { describe, expect, it } from "vitest";
import {
  assemblyHoursForBulk,
  assemblyHoursForIndividual,
  bulkCreatorEtas,
  craftHoursForBulk,
  craftHoursForIndividual,
  hoursPct,
} from "./orderProgress";
import type { ComponentInstance, Creator, OrderView, TimeLogEntryView, Variant } from "./types";

function timeLog(stage: TimeLogEntryView["stage"], hours: number): TimeLogEntryView {
  return { entryId: "e", stage, hours, date: "2026-01-01T00:00:00Z", loggedByCreatorId: "c1", note: null };
}

function component(overrides: Partial<ComponentInstance> = {}): ComponentInstance {
  return {
    componentId: "comp-1", templateId: "t-1", label: "Base", templateCraftingTimeHours: 0,
    quantity: 1, mandatoryItems: [], addOns: [], craftingTimeHours: 0,
    perUnitCost: 0, totalCost: 0, perUnitTimeHours: 0, totalTimeHours: 0, timeLogEntries: [],
    ...overrides,
  };
}

function variant(overrides: Partial<Variant> = {}): Variant {
  return {
    variantId: "v1", label: "Blue flower", quantity: 1, mandatoryItems: [], addOns: [],
    components: [], packaging: null, craftingTimeHours: 0, assemblyTimeHours: 0,
    perUnitCost: 0, totalCost: 0, perUnitTimeHours: 0, totalTimeHours: 0,
    splitAllocation: [], timeLogEntries: [],
    ...overrides,
  };
}

function order(overrides: Partial<OrderView> = {}): OrderView {
  return {
    id: "o1", orderNumber: "1", orderType: "INDIVIDUAL", customerId: "cust1", createdByCreatorId: "c1",
    status: "IN_PROGRESS", itemName: "Bear", orderReceivedDate: "2026-01-01T00:00:00Z",
    quotedDeliveryDate: null, deliveryTier: "SAME_CITY", actualDeliveryDate: null, pattern: null,
    researchItems: [], researchTimeHours: 0, assemblyPackagingInstructions: null, assemblyPresetId: null, notes: null,
    mandatoryItems: [], addOns: [], components: [], packaging: null,
    craftingTimeHours: 0, assemblyTimeHours: 0, costEstimate: null, stageAssignments: [],
    completionPercentage: 0, payments: [], paymentStatus: "UNPAID", netPaid: 0, balanceAmount: 0,
    shipmentPlan: [], timeLogEntries: [], usageLogEntries: [], bulkDetails: null, cancellation: null,
    createdAt: "2026-01-01T00:00:00Z", updatedAt: "2026-01-01T00:00:00Z",
    ...overrides,
  };
}

function creator(overrides: Partial<Creator> = {}): Creator {
  return {
    id: "c1", groupId: "g1", userId: "u1", name: "Creator A", baseLocation: "Bangalore",
    locationCode: "BLR", creatorCode: "CR-001", hoursAvailablePerDay: 4, autoSyncInventory: false,
    ...overrides,
  };
}

describe("hoursPct", () => {
  it("is the logged/estimated ratio as a percentage", () => {
    expect(hoursPct(2, 4)).toBe(50);
    expect(hoursPct(4, 4)).toBe(100);
    expect(hoursPct(6, 4)).toBe(150);
  });

  it("returns 0 rather than dividing by zero when nothing is estimated yet", () => {
    expect(hoursPct(0, 0)).toBe(0);
    expect(hoursPct(5, 0)).toBe(0);
  });
});

describe("craftHoursForIndividual", () => {
  it("sums CRAFTING time-log hours plus every component's own crafting log, against crafting + components' total time", () => {
    const o = order({
      craftingTimeHours: 4,
      timeLogEntries: [timeLog("CRAFTING", 1), timeLog("ASSEMBLY", 9)],
      components: [component({ totalTimeHours: 2, timeLogEntries: [timeLog("CRAFTING", 0.5)] })],
    });
    expect(craftHoursForIndividual(o)).toEqual({ logged: 1.5, estimated: 6 });
  });
});

describe("assemblyHoursForIndividual", () => {
  it("sums only ASSEMBLY time-log hours, against the order's own assemblyTimeHours", () => {
    const o = order({
      assemblyTimeHours: 3,
      timeLogEntries: [timeLog("CRAFTING", 9), timeLog("ASSEMBLY", 1)],
    });
    expect(assemblyHoursForIndividual(o)).toEqual({ logged: 1, estimated: 3 });
  });
});

describe("craftHoursForBulk", () => {
  it("sums across every variant's own CRAFTING logs + components, against crafting*quantity + components' time", () => {
    const o = order({
      orderType: "BULK",
      bulkDetails: {
        variants: [
          variant({
            quantity: 10, craftingTimeHours: 1,
            timeLogEntries: [timeLog("CRAFTING", 2)],
            components: [component({ totalTimeHours: 5, timeLogEntries: [timeLog("CRAFTING", 1)] })],
          }),
          variant({ variantId: "v2", quantity: 4, craftingTimeHours: 0.5, timeLogEntries: [timeLog("CRAFTING", 1)] }),
        ],
        totalQuantity: 14, totalFinalCost: 0, totalTimeHours: 0, coordinatingCreatorId: null,
        stageAssignments: [], stageProgress: [], logisticsBufferDays: 0, deliveryBufferDays: 0, computedDueDate: null,
      },
    });
    // logged = (2 + 1) + 1 = 4; estimated = (10*1 + 5) + (4*0.5) = 17
    expect(craftHoursForBulk(o)).toEqual({ logged: 4, estimated: 17 });
  });

  it("returns zero/zero when there's no bulkDetails at all", () => {
    expect(craftHoursForBulk(order({ bulkDetails: null }))).toEqual({ logged: 0, estimated: 0 });
  });
});

describe("assemblyHoursForBulk", () => {
  it("sums each variant's ASSEMBLY logs, against assemblyTimeHours*quantity", () => {
    const o = order({
      orderType: "BULK",
      bulkDetails: {
        variants: [
          variant({ quantity: 10, assemblyTimeHours: 0.5, timeLogEntries: [timeLog("ASSEMBLY", 3)] }),
          variant({ variantId: "v2", quantity: 4, assemblyTimeHours: 1, timeLogEntries: [timeLog("ASSEMBLY", 1)] }),
        ],
        totalQuantity: 14, totalFinalCost: 0, totalTimeHours: 0, coordinatingCreatorId: null,
        stageAssignments: [], stageProgress: [], logisticsBufferDays: 0, deliveryBufferDays: 0, computedDueDate: null,
      },
    });
    // logged = 3 + 1 = 4; estimated = 10*0.5 + 4*1 = 9
    expect(assemblyHoursForBulk(o)).toEqual({ logged: 4, estimated: 9 });
  });
});

describe("bulkCreatorEtas", () => {
  it("computes each assigned creator's total hours, days at their own pace, and a target date", () => {
    const o = order({
      orderType: "BULK",
      orderReceivedDate: "2026-01-01T00:00:00Z",
      bulkDetails: {
        variants: [
          variant({
            perUnitTimeHours: 2,
            splitAllocation: [
              { creatorId: "c1", quantityAssigned: 10, stageProgress: [] },
              { creatorId: "c2", quantityAssigned: 5, stageProgress: [] },
            ],
          }),
        ],
        totalQuantity: 15, totalFinalCost: 0, totalTimeHours: 0, coordinatingCreatorId: null,
        stageAssignments: [], stageProgress: [], logisticsBufferDays: 0, deliveryBufferDays: 0, computedDueDate: null,
      },
    });
    const creators = [creator({ id: "c1", hoursAvailablePerDay: 4 }), creator({ id: "c2", hoursAvailablePerDay: 8 })];

    const etas = bulkCreatorEtas(o, creators);

    // c1: 10*2=20h / 4h-per-day = 5 days -> target 2026-01-06
    const c1 = etas.find((e) => e.creatorId === "c1")!;
    expect(c1.hours).toBe(20);
    expect(c1.days).toBe(5);
    expect(c1.targetDate?.toISOString()).toBe("2026-01-06T00:00:00.000Z");

    // c2: 5*2=10h / 8h-per-day = ceil(1.25) = 2 days -> target 2026-01-03
    const c2 = etas.find((e) => e.creatorId === "c2")!;
    expect(c2.hours).toBe(10);
    expect(c2.days).toBe(2);
    expect(c2.targetDate?.toISOString()).toBe("2026-01-03T00:00:00.000Z");
  });

  it("sums hours across every variant a creator is split into, not just the first", () => {
    const o = order({
      orderType: "BULK",
      bulkDetails: {
        variants: [
          variant({ perUnitTimeHours: 1, splitAllocation: [{ creatorId: "c1", quantityAssigned: 10, stageProgress: [] }] }),
          variant({ variantId: "v2", perUnitTimeHours: 3, splitAllocation: [{ creatorId: "c1", quantityAssigned: 2, stageProgress: [] }] }),
        ],
        totalQuantity: 12, totalFinalCost: 0, totalTimeHours: 0, coordinatingCreatorId: null,
        stageAssignments: [], stageProgress: [], logisticsBufferDays: 0, deliveryBufferDays: 0, computedDueDate: null,
      },
    });
    const etas = bulkCreatorEtas(o, [creator({ id: "c1", hoursAvailablePerDay: 4 })]);
    // 10*1 + 2*3 = 16h
    expect(etas[0].hours).toBe(16);
  });

  it("gives null days/targetDate when the creator has no configured hoursAvailablePerDay", () => {
    const o = order({
      orderType: "BULK",
      bulkDetails: {
        variants: [variant({ perUnitTimeHours: 1, splitAllocation: [{ creatorId: "c1", quantityAssigned: 10, stageProgress: [] }] })],
        totalQuantity: 10, totalFinalCost: 0, totalTimeHours: 0, coordinatingCreatorId: null,
        stageAssignments: [], stageProgress: [], logisticsBufferDays: 0, deliveryBufferDays: 0, computedDueDate: null,
      },
    });
    const etas = bulkCreatorEtas(o, [creator({ id: "c1", hoursAvailablePerDay: 0 })]);
    expect(etas[0].hours).toBe(10);
    expect(etas[0].days).toBeNull();
    expect(etas[0].targetDate).toBeNull();
  });

  it("gives null targetDate when the order has no orderReceivedDate, even with a computable days", () => {
    const o = order({
      orderType: "BULK",
      orderReceivedDate: null,
      bulkDetails: {
        variants: [variant({ perUnitTimeHours: 1, splitAllocation: [{ creatorId: "c1", quantityAssigned: 10, stageProgress: [] }] })],
        totalQuantity: 10, totalFinalCost: 0, totalTimeHours: 0, coordinatingCreatorId: null,
        stageAssignments: [], stageProgress: [], logisticsBufferDays: 0, deliveryBufferDays: 0, computedDueDate: null,
      },
    });
    const etas = bulkCreatorEtas(o, [creator({ id: "c1", hoursAvailablePerDay: 4 })]);
    expect(etas[0].days).toBe(3);
    expect(etas[0].targetDate).toBeNull();
  });

  it("returns an empty list when the order has no bulkDetails or nobody is assigned yet", () => {
    expect(bulkCreatorEtas(order({ bulkDetails: null }), [])).toEqual([]);
  });
});
