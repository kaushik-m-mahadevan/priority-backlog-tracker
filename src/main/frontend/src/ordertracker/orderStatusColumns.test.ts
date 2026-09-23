import { describe, expect, it } from "vitest";
import { columnOf, legalMoves, nextColumnFirstStatus } from "./orderStatusColumns";

describe("orderStatusColumns", () => {
  it("maps every status to its column, and CANCELLED to none", () => {
    expect(columnOf("INQUIRY")).toBe("PENDING");
    expect(columnOf("CONFIRMED")).toBe("PENDING");
    expect(columnOf("IN_PROGRESS")).toBe("IN_PROGRESS");
    expect(columnOf("READY_TO_SHIP")).toBe("COMPLETED");
    expect(columnOf("SHIPPED")).toBe("COMPLETED");
    expect(columnOf("DELIVERED")).toBe("CLOSED");
    expect(columnOf("CANCELLED")).toBeNull();
  });

  it("offers the same-column sibling as a free move", () => {
    const moves = legalMoves("INQUIRY");
    expect(moves).toContainEqual({ status: "CONFIRMED", needsJustification: false });
  });

  it("offers the next column's statuses as a free forward move", () => {
    const moves = legalMoves("CONFIRMED");
    expect(moves).toContainEqual({ status: "IN_PROGRESS", needsJustification: false });
  });

  it("offers the previous column's statuses as a backward move requiring justification", () => {
    const moves = legalMoves("IN_PROGRESS");
    expect(moves).toContainEqual({ status: "INQUIRY", needsJustification: true });
    expect(moves).toContainEqual({ status: "CONFIRMED", needsJustification: true });
  });

  it("never offers a multi-column jump or CANCELLED", () => {
    const moves = legalMoves("INQUIRY");
    expect(moves.map((m) => m.status)).not.toContain("READY_TO_SHIP");
    expect(moves.map((m) => m.status)).not.toContain("DELIVERED");
    expect(moves.map((m) => m.status)).not.toContain("CANCELLED");
  });

  it("offers nothing once CANCELLED — it's terminal", () => {
    expect(legalMoves("CANCELLED")).toEqual([]);
  });

  it("the Closed column (Delivered) can still move back to Completed with justification", () => {
    const moves = legalMoves("DELIVERED");
    expect(moves).toContainEqual({ status: "READY_TO_SHIP", needsJustification: true });
    expect(moves).toContainEqual({ status: "SHIPPED", needsJustification: true });
  });

  it("nextColumnFirstStatus gives the first status of the immediately next column", () => {
    expect(nextColumnFirstStatus("INQUIRY")).toBe("IN_PROGRESS");
    expect(nextColumnFirstStatus("CONFIRMED")).toBe("IN_PROGRESS");
    expect(nextColumnFirstStatus("IN_PROGRESS")).toBe("READY_TO_SHIP");
    expect(nextColumnFirstStatus("READY_TO_SHIP")).toBe("DELIVERED");
  });

  it("nextColumnFirstStatus is null once there's no further column to promote into, or for CANCELLED", () => {
    expect(nextColumnFirstStatus("DELIVERED")).toBeNull();
    expect(nextColumnFirstStatus("CANCELLED")).toBeNull();
  });
});
