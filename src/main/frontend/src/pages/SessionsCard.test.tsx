import { describe, expect, it } from "vitest";
import { formatDuration } from "./SessionsCard";

describe("formatDuration", () => {
  it("shows seconds, minutes, then hours and minutes", () => {
    expect(formatDuration(45)).toBe("45s");
    expect(formatDuration(600)).toBe("10m");
    expect(formatDuration(3 * 3600 + 5 * 60)).toBe("3h 5m");
  });

  it("shows a dash when unknown", () => {
    expect(formatDuration(null)).toBe("—");
  });
});
