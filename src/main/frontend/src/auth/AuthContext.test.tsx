import { afterEach, describe, expect, it, vi } from "vitest";
import { act, renderHook, waitFor } from "@testing-library/react";
import { AuthProvider, useAuth } from "./AuthContext";
import { api, getToken, setToken } from "../api/client";
import type { User } from "../types";

vi.mock("../api/client", async () => {
  const actual = await vi.importActual<typeof import("../api/client")>("../api/client");
  return {
    ...actual,
    api: { get: vi.fn(), post: vi.fn() },
  };
});

function user(overrides: Partial<User> = {}): User {
  return { id: "u1", name: "Ann", email: "ann@example.com", role: "USER", status: "ACTIVE", ...overrides };
}

describe("useAuth", () => {
  afterEach(() => {
    vi.clearAllMocks();
    setToken(null);
  });

  it("throws when used outside an AuthProvider", () => {
    expect(() => renderHook(() => useAuth())).toThrow("useAuth must be used within AuthProvider");
  });

  it("has no user and stops loading immediately when there is no stored token", async () => {
    const { result } = renderHook(() => useAuth(), { wrapper: AuthProvider });

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.user).toBeNull();
    expect(api.get).not.toHaveBeenCalled();
  });

  it("loads the current user from /auth/me when a token is already stored", async () => {
    setToken("existing-token");
    vi.mocked(api.get).mockResolvedValue(user());

    const { result } = renderHook(() => useAuth(), { wrapper: AuthProvider });

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.user).toEqual(user());
  });

  it("clears the token and user when /auth/me rejects for a stored token", async () => {
    setToken("stale-token");
    vi.mocked(api.get).mockRejectedValue(new Error("401"));

    const { result } = renderHook(() => useAuth(), { wrapper: AuthProvider });

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.user).toBeNull();
    expect(getToken()).toBeNull();
  });

  it("login() stores the token and sets the user", async () => {
    vi.mocked(api.post).mockResolvedValue({ token: "new-token", user: user({ name: "Bob" }) });

    const { result } = renderHook(() => useAuth(), { wrapper: AuthProvider });
    await waitFor(() => expect(result.current.loading).toBe(false));

    await act(async () => {
      await result.current.login("bob@example.com", "pw");
    });

    expect(getToken()).toBe("new-token");
    expect(result.current.user).toEqual(user({ name: "Bob" }));
  });

  it("logout() clears the token and the user", async () => {
    vi.mocked(api.post).mockResolvedValue({ token: "new-token", user: user() });
    const { result } = renderHook(() => useAuth(), { wrapper: AuthProvider });
    await waitFor(() => expect(result.current.loading).toBe(false));
    await act(async () => {
      await result.current.login("ann@example.com", "pw");
    });
    expect(result.current.user).not.toBeNull();

    act(() => {
      result.current.logout();
    });

    expect(result.current.user).toBeNull();
    expect(getToken()).toBeNull();
  });

  it("refresh() re-fetches /auth/me and updates the user", async () => {
    setToken("token");
    vi.mocked(api.get).mockResolvedValue(user());
    const { result } = renderHook(() => useAuth(), { wrapper: AuthProvider });
    await waitFor(() => expect(result.current.loading).toBe(false));

    vi.mocked(api.get).mockResolvedValue(user({ name: "Updated" }));
    await act(async () => {
      await result.current.refresh();
    });

    expect(result.current.user).toEqual(user({ name: "Updated" }));
  });
});
