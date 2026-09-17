import { describe, expect, it } from "vitest";
import { validateSplits, type VariantDraft } from "./OrderFormFields";

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
