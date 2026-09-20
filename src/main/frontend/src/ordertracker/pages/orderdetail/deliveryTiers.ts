import type { DeliveryTier } from "../../types";

/** Shared between OrderDetailPage and both its edit forms (fdup-1) — previously three
 *  separate copies of the same literal map. */
export const DELIVERY_TIER_LABELS: Record<DeliveryTier, string> = {
  SAME_CITY: "Same city",
  SAME_STATE: "Same state",
  OTHER_STATE: "Other state",
  INTERNATIONAL: "International",
};
