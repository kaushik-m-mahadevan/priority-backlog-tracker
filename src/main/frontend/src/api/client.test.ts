import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { api, ApiError, getToken, setToken } from "./client";

describe("token storage", () => {
  afterEach(() => {
    localStorage.clear();
  });

  it("round-trips a token through localStorage", () => {
    expect(getToken()).toBeNull();
    setToken("abc123");
    expect(getToken()).toBe("abc123");
    setToken(null);
    expect(getToken()).toBeNull();
  });

  it("never throws when localStorage is unavailable", () => {
    const original = window.localStorage;
    // simulate a private-browsing / blocked-storage environment
    Object.defineProperty(window, "localStorage", {
      configurable: true,
      get() {
        throw new Error("storage disabled");
      },
    });
    try {
      expect(getToken()).toBeNull();
      expect(() => setToken("x")).not.toThrow();
    } finally {
      Object.defineProperty(window, "localStorage", { configurable: true, value: original });
    }
  });
});

describe("api.request (via the api.* verbs)", () => {
  beforeEach(() => {
    localStorage.clear();
    vi.stubGlobal("fetch", vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  function mockResponse(status: number, body?: unknown) {
    return {
      status,
      ok: status >= 200 && status < 300,
      text: async () => (body === undefined ? "" : JSON.stringify(body)),
    };
  }

  it("prefixes the path with /api and omits Authorization when there's no token", async () => {
    (fetch as ReturnType<typeof vi.fn>).mockResolvedValue(mockResponse(200, { ok: true }));

    await api.get("/items");

    const [url, init] = (fetch as ReturnType<typeof vi.fn>).mock.calls[0];
    expect(url).toBe("/api/items");
    expect(init.headers["Authorization"]).toBeUndefined();
  });

  it("attaches a Bearer token when one is stored", async () => {
    setToken("my-token");
    (fetch as ReturnType<typeof vi.fn>).mockResolvedValue(mockResponse(200, { ok: true }));

    await api.get("/items");

    const [, init] = (fetch as ReturnType<typeof vi.fn>).mock.calls[0];
    expect(init.headers["Authorization"]).toBe("Bearer my-token");
  });

  it("sends a JSON body and Content-Type only when a body is given", async () => {
    (fetch as ReturnType<typeof vi.fn>).mockResolvedValue(mockResponse(200, {}));

    await api.post("/items", { title: "Write docs" });
    let [, init] = (fetch as ReturnType<typeof vi.fn>).mock.calls[0];
    expect(init.headers["Content-Type"]).toBe("application/json");
    expect(init.body).toBe(JSON.stringify({ title: "Write docs" }));

    (fetch as ReturnType<typeof vi.fn>).mockClear();
    (fetch as ReturnType<typeof vi.fn>).mockResolvedValue(mockResponse(204));
    await api.delete("/items/1");
    [, init] = (fetch as ReturnType<typeof vi.fn>).mock.calls[0];
    expect(init.headers["Content-Type"]).toBeUndefined();
    expect(init.body).toBeUndefined();
  });

  it("returns undefined for a 204 No Content response without parsing the body", async () => {
    (fetch as ReturnType<typeof vi.fn>).mockResolvedValue(mockResponse(204));

    const result = await api.delete("/items/1");

    expect(result).toBeUndefined();
  });

  it("returns undefined for a 200 response with an empty body", async () => {
    (fetch as ReturnType<typeof vi.fn>).mockResolvedValue(mockResponse(200));

    const result = await api.get("/items/1");

    expect(result).toBeUndefined();
  });

  it("parses and returns the JSON body on success", async () => {
    (fetch as ReturnType<typeof vi.fn>).mockResolvedValue(mockResponse(200, { id: "1", title: "X" }));

    const result = await api.get<{ id: string; title: string }>("/items/1");

    expect(result).toEqual({ id: "1", title: "X" });
  });

  it("throws an ApiError carrying the status and the server's message field on failure", async () => {
    (fetch as ReturnType<typeof vi.fn>).mockResolvedValue(
      mockResponse(400, { message: "Unknown category 'Nonsense'" }),
    );

    await expect(api.post("/items", {})).rejects.toMatchObject({
      status: 400,
      message: "Unknown category 'Nonsense'",
    });
    await expect(api.post("/items", {})).rejects.toBeInstanceOf(ApiError);
  });

  it("falls back to the server's error field, then a generic message, when message is absent", async () => {
    (fetch as ReturnType<typeof vi.fn>).mockResolvedValue(mockResponse(403, { error: "Forbidden" }));
    await expect(api.get("/items")).rejects.toMatchObject({ status: 403, message: "Forbidden" });

    (fetch as ReturnType<typeof vi.fn>).mockResolvedValue(mockResponse(500));
    await expect(api.get("/items")).rejects.toMatchObject({
      status: 500,
      message: "Request failed (500)",
    });
  });
});
