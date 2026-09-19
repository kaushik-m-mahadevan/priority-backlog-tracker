import type { Creator, OrderView } from "./types";

/** Hours-based completion for crocheting/assembly (the two stages with real time
 *  tracking) — packaging/shipment have no hour estimate, so they stay on the existing
 *  units-completed/total-units mechanism instead. Extracted from OrderDetailPage (tf-4)
 *  so this math can be tested without rendering the page. */
export function hoursPct(logged: number, estimated: number): number {
  return estimated > 0 ? (logged / estimated) * 100 : 0;
}

export function craftHoursForIndividual(order: OrderView): { logged: number; estimated: number } {
  const logged = order.timeLogEntries.filter((e) => e.stage === "CRAFTING").reduce((s, e) => s + e.hours, 0)
    + order.components.flatMap((c) => c.timeLogEntries).reduce((s, e) => s + e.hours, 0);
  const estimated = order.craftingTimeHours + order.components.reduce((s, c) => s + c.totalTimeHours, 0);
  return { logged, estimated };
}

export function assemblyHoursForIndividual(order: OrderView): { logged: number; estimated: number } {
  const logged = order.timeLogEntries.filter((e) => e.stage === "ASSEMBLY").reduce((s, e) => s + e.hours, 0);
  return { logged, estimated: order.assemblyTimeHours };
}

export function craftHoursForBulk(order: OrderView): { logged: number; estimated: number } {
  const variants = order.bulkDetails?.variants ?? [];
  const logged = variants.reduce((sum, v) =>
    sum + v.timeLogEntries.filter((e) => e.stage === "CRAFTING").reduce((s, e) => s + e.hours, 0)
      + v.components.flatMap((c) => c.timeLogEntries).reduce((s, e) => s + e.hours, 0), 0);
  const estimated = variants.reduce((sum, v) =>
    sum + v.craftingTimeHours * v.quantity + v.components.reduce((s, c) => s + c.totalTimeHours, 0), 0);
  return { logged, estimated };
}

export function assemblyHoursForBulk(order: OrderView): { logged: number; estimated: number } {
  const variants = order.bulkDetails?.variants ?? [];
  const logged = variants.reduce((sum, v) =>
    sum + v.timeLogEntries.filter((e) => e.stage === "ASSEMBLY").reduce((s, e) => s + e.hours, 0), 0);
  const estimated = variants.reduce((sum, v) => sum + v.assemblyTimeHours * v.quantity, 0);
  return { logged, estimated };
}

export interface CreatorEta {
  creatorId: string;
  hours: number;
  hoursPerDay: number;
  /** null when the creator has no configured hoursAvailablePerDay yet — an ETA can't be
   *  computed, only the raw assigned-hours total shown. */
  days: number | null;
  /** null whenever days is null, or the order has no orderReceivedDate to count from. */
  targetDate: Date | null;
}

/** Each bulk-order creator's own target finish date, based on their total assigned hours
 *  (across every variant's split allocation, weighted by that variant's per-unit time) and
 *  their own daily pace — never a shared, order-wide estimate. */
export function bulkCreatorEtas(order: OrderView, creators: Creator[]): CreatorEta[] {
  const variants = order.bulkDetails?.variants ?? [];
  const creatorIds = [...new Set(variants.flatMap((v) => v.splitAllocation.map((s) => s.creatorId)))];
  return creatorIds.map((creatorId) => {
    const hours = variants.reduce((sum, v) => {
      const split = v.splitAllocation.find((s) => s.creatorId === creatorId);
      return sum + (split ? split.quantityAssigned * v.perUnitTimeHours : 0);
    }, 0);
    const hoursPerDay = creators.find((c) => c.id === creatorId)?.hoursAvailablePerDay ?? 0;
    const days = hoursPerDay > 0 ? Math.max(1, Math.ceil(hours / hoursPerDay)) : null;
    const targetDate = days !== null && order.orderReceivedDate
      ? new Date(new Date(order.orderReceivedDate).getTime() + days * 86400000)
      : null;
    return { creatorId, hours, hoursPerDay, days, targetDate };
  });
}
