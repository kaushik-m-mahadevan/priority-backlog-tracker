import { describe, expect, it } from "vitest";
import { blankComponent, duplicateVariant, validateSplits, type ComponentDraft, type VariantDraft } from "./OrderFormFields";
import type { ComponentTemplate } from "./types";

function variant(overrides: Partial<VariantDraft>): VariantDraft {
  return {
    label: "Blue flower",
    quantity: 3,
    mandatoryItems: [],
    addOns: [],
    components: [],
    craftingTimeHours: 0,
    assemblyTimeHours: 0,
    splitAllocation: [],
    ...overrides,
  };
}

describe("validateSplits", () => {
  it("passes a variant with no split allocation at all", () => {
    expect(validateSplits([variant({})])).toBeNull();
  });

  it("passes when split quantities sum exactly to the variant quantity", () => {
    const v = variant({
      splitAllocation: [
        { creatorId: "c1", quantityAssigned: 2 },
        { creatorId: "c2", quantityAssigned: 1 },
      ],
    });
    expect(validateSplits([v])).toBeNull();
  });

  it("rejects a split that sums under the variant quantity", () => {
    const v = variant({ quantity: 3, splitAllocation: [{ creatorId: "c1", quantityAssigned: 2 }] });
    expect(validateSplits([v])).toMatch(/adds up to 2/);
  });

  it("rejects a split that sums over the variant quantity", () => {
    const v = variant({
      quantity: 3,
      splitAllocation: [
        { creatorId: "c1", quantityAssigned: 2 },
        { creatorId: "c2", quantityAssigned: 2 },
      ],
    });
    expect(validateSplits([v])).toMatch(/adds up to 4/);
  });

  it("rejects the same creator appearing twice in one variant's split", () => {
    const v = variant({
      quantity: 3,
      splitAllocation: [
        { creatorId: "c1", quantityAssigned: 1 },
        { creatorId: "c1", quantityAssigned: 2 },
      ],
    });
    expect(validateSplits([v])).toMatch(/more than one split entry/);
  });

  it("ignores split entries with no creator selected yet", () => {
    const v = variant({
      quantity: 3,
      splitAllocation: [
        { creatorId: "c1", quantityAssigned: 3 },
        { creatorId: "", quantityAssigned: 5 },
      ],
    });
    expect(validateSplits([v])).toBeNull();
  });

  it("skips variants with a blank label (not yet a real row)", () => {
    const v = variant({ label: "  ", quantity: 3, splitAllocation: [{ creatorId: "c1", quantityAssigned: 1 }] });
    expect(validateSplits([v])).toBeNull();
  });

  it("checks every variant, not just the first", () => {
    const ok = variant({ label: "A", quantity: 2, splitAllocation: [{ creatorId: "c1", quantityAssigned: 2 }] });
    const bad = variant({ label: "B", quantity: 5, splitAllocation: [{ creatorId: "c1", quantityAssigned: 1 }] });
    expect(validateSplits([ok, bad])).toMatch(/"B"/);
  });
});

describe("duplicateVariant", () => {
  it("copies every field but drops variantId, treating the clone as new", () => {
    const source = variant({
      variantId: "v-123",
      label: "Blue flower",
      quantity: 5,
      mandatoryItems: [{ kind: "YARN", value: "Blue", quantity: 1, unitCost: 100, notes: "", linkedYarnTypeId: null, linkedNeedleTypeId: null }],
      addOns: [{ name: "Eyes", quantity: 1, unitCost: 10 }],
      components: [{ componentId: "c-1", templateId: "t-1", quantity: 2, mandatoryItems: [], addOns: [], craftingTimeHours: 1 }],
      craftingTimeHours: 2,
      assemblyTimeHours: 1,
      splitAllocation: [{ creatorId: "creator-1", quantityAssigned: 5 }],
    });

    const clone = duplicateVariant(source);

    expect(clone.variantId).toBeUndefined();
    expect(clone.label).toBe("Blue flower");
    expect(clone.quantity).toBe(5);
    expect(clone.mandatoryItems).toEqual(source.mandatoryItems);
    expect(clone.addOns).toEqual(source.addOns);
    expect(clone.splitAllocation).toEqual(source.splitAllocation);
    // each component's own componentId is dropped too -- a cloned component is also new
    expect(clone.components[0].componentId).toBeUndefined();
    expect(clone.components[0].templateId).toBe("t-1");
  });

  it("deep-copies arrays so editing the clone never mutates the source", () => {
    const source = variant({
      mandatoryItems: [{ kind: "YARN", value: "Blue", quantity: 1, unitCost: 100, notes: "", linkedYarnTypeId: null, linkedNeedleTypeId: null }],
      addOns: [{ name: "Eyes", quantity: 1, unitCost: 10 }],
      splitAllocation: [{ creatorId: "creator-1", quantityAssigned: 5 }],
    });

    const clone = duplicateVariant(source);
    clone.mandatoryItems[0].value = "Red";
    clone.addOns[0].name = "Nose";
    clone.splitAllocation[0].quantityAssigned = 99;

    expect(source.mandatoryItems[0].value).toBe("Blue");
    expect(source.addOns[0].name).toBe("Eyes");
    expect(source.splitAllocation[0].quantityAssigned).toBe(5);
  });
});

describe("blankComponent", () => {
  const templates: ComponentTemplate[] = [
    { id: "t-1", label: "Vase base", pattern: null, baseCraftingTimeHours: 2.5, notes: null },
    { id: "t-2", label: "Petal", pattern: null, baseCraftingTimeHours: 0.5, notes: null },
  ];

  it("defaults to the first template and pre-fills its base crafting time", () => {
    const c: ComponentDraft = blankComponent(templates);
    expect(c.templateId).toBe("t-1");
    expect(c.craftingTimeHours).toBe(2.5);
    expect(c.quantity).toBe(1);
    expect(c.mandatoryItems).toEqual([]);
    expect(c.addOns).toEqual([]);
  });

  it("falls back to an empty templateId and zero time when no templates exist yet", () => {
    const c = blankComponent([]);
    expect(c.templateId).toBe("");
    expect(c.craftingTimeHours).toBe(0);
  });
});
