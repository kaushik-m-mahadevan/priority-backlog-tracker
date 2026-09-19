import { afterEach, describe, expect, it, vi } from "vitest";
import { act, renderHook, waitFor } from "@testing-library/react";
import { useSetupGate } from "./useSetupGate";
import { orderTrackerApi } from "./api";
import type { BusinessConfig, Creator } from "./types";

vi.mock("./api", () => ({
  orderTrackerApi: {
    businessConfig: vi.fn(),
    myCreatorProfile: vi.fn(),
  },
}));

function config(overrides: Partial<BusinessConfig> = {}): BusinessConfig {
  return {
    setupComplete: true,
    currency: "INR",
    workStages: [],
    deliveryBufferSameCityDays: 1,
    deliveryBufferSameStateDays: 2,
    deliveryBufferOtherStateDays: 3,
    deliveryBufferInternationalDays: 7,
    ...overrides,
  } as BusinessConfig;
}

function creator(overrides: Partial<Creator> = {}): Creator {
  return {
    id: "c1", groupId: "g1", userId: "u1", name: "Creator A", baseLocation: "Bangalore",
    locationCode: "BLR", creatorCode: "CR-001", hoursAvailablePerDay: 4,
    ...overrides,
  };
}

describe("useSetupGate", () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  it("is immediately 'ready' with no API calls when there is no current group", async () => {
    const { result } = renderHook(() => useSetupGate(null));

    expect(result.current.status).toBe("ready");
    expect(orderTrackerApi.businessConfig).not.toHaveBeenCalled();
    expect(orderTrackerApi.myCreatorProfile).not.toHaveBeenCalled();
  });

  it("is 'wizard' when the business hasn't completed setup", async () => {
    vi.mocked(orderTrackerApi.businessConfig).mockResolvedValue(config({ setupComplete: false }));
    vi.mocked(orderTrackerApi.myCreatorProfile).mockResolvedValue(null as unknown as Creator);

    const { result } = renderHook(() => useSetupGate("g1"));

    await waitFor(() => expect(result.current.status).toBe("wizard"));
  });

  it("is 'profile-gate' when setup is complete but the caller has no creator profile yet", async () => {
    vi.mocked(orderTrackerApi.businessConfig).mockResolvedValue(config({ setupComplete: true }));
    vi.mocked(orderTrackerApi.myCreatorProfile).mockRejectedValue(new Error("404"));

    const { result } = renderHook(() => useSetupGate("g1"));

    await waitFor(() => expect(result.current.status).toBe("profile-gate"));
    expect(result.current.profile).toBeNull();
  });

  it("is 'ready' with the profile set when both setup and the profile are present", async () => {
    const me = creator();
    vi.mocked(orderTrackerApi.businessConfig).mockResolvedValue(config({ setupComplete: true }));
    vi.mocked(orderTrackerApi.myCreatorProfile).mockResolvedValue(me);

    const { result } = renderHook(() => useSetupGate("g1"));

    await waitFor(() => expect(result.current.status).toBe("ready"));
    expect(result.current.profile).toEqual(me);
  });

  it("refresh() re-fetches both business-config and the creator profile", async () => {
    vi.mocked(orderTrackerApi.businessConfig).mockResolvedValue(config({ setupComplete: true }));
    vi.mocked(orderTrackerApi.myCreatorProfile).mockResolvedValue(creator());

    const { result } = renderHook(() => useSetupGate("g1"));
    await waitFor(() => expect(result.current.status).toBe("ready"));
    expect(orderTrackerApi.businessConfig).toHaveBeenCalledTimes(1);

    await act(async () => {
      await result.current.refresh();
    });

    expect(orderTrackerApi.businessConfig).toHaveBeenCalledTimes(2);
    expect(orderTrackerApi.myCreatorProfile).toHaveBeenCalledTimes(2);
  });
});
