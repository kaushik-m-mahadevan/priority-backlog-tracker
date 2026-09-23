import type { OrderStatus } from "./types";

/** ad-3: the Kanban board's 4 columns and the "strictly adjacent-only" transition rule —
 *  mirrors OrderStatusColumns.java exactly, since the frontend needs the same shape to
 *  build a legal-moves menu, but the backend is what actually enforces it (this file is
 *  never a substitute for that — every move still round-trips through the real endpoint,
 *  which rejects anything illegal regardless of what this offers as an option). */
export type OrderStatusColumn = "PENDING" | "IN_PROGRESS" | "COMPLETED" | "CLOSED";

export const COLUMNS: OrderStatusColumn[] = ["PENDING", "IN_PROGRESS", "COMPLETED", "CLOSED"];

export const COLUMN_LABELS: Record<OrderStatusColumn, string> = {
  PENDING: "Pending",
  IN_PROGRESS: "In Progress",
  COMPLETED: "Completed",
  CLOSED: "Closed",
};

export const COLUMN_STATUSES: Record<OrderStatusColumn, OrderStatus[]> = {
  PENDING: ["INQUIRY", "CONFIRMED"],
  IN_PROGRESS: ["IN_PROGRESS"],
  COMPLETED: ["READY_TO_SHIP", "SHIPPED"],
  CLOSED: ["DELIVERED"],
};

/** Null for CANCELLED — it sits outside the board/column model entirely (a separate,
 *  always-available action, not a status move). */
export function columnOf(status: OrderStatus): OrderStatusColumn | null {
  if (status === "CANCELLED") return null;
  return COLUMNS.find((c) => COLUMN_STATUSES[c].includes(status)) ?? null;
}

export interface LegalMove {
  status: OrderStatus;
  needsJustification: boolean;
}

/** Every status this order could legally move to next: any other status in the same
 *  column (free), every status in the immediately next column (free), every status in the
 *  immediately previous column (needs a justification). Never includes a multi-column
 *  jump or CANCELLED — cancelling is its own guided flow, not a status move. */
export function legalMoves(status: OrderStatus): LegalMove[] {
  const column = columnOf(status);
  if (column === null) return []; // CANCELLED — no further moves
  const idx = COLUMNS.indexOf(column);
  const moves: LegalMove[] = [];
  for (const s of COLUMN_STATUSES[column]) {
    if (s !== status) moves.push({ status: s, needsJustification: false });
  }
  if (idx + 1 < COLUMNS.length) {
    for (const s of COLUMN_STATUSES[COLUMNS[idx + 1]]) moves.push({ status: s, needsJustification: false });
  }
  if (idx - 1 >= 0) {
    for (const s of COLUMN_STATUSES[COLUMNS[idx - 1]]) moves.push({ status: s, needsJustification: true });
  }
  return moves;
}

export const CANNED_TRANSITION_REASONS = ["Item damaged", "Rework needed", "Customer changed request"];
export const CANNED_CANCEL_REASONS = ["Item damaged", "Customer changed their mind", "Unable to source materials", "Duplicate order"];
