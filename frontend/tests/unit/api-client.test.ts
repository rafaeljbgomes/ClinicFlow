import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiClientError, apiFetch, isStaleVersion } from "@/lib/api-client";

function sentHeaders(fetchMock: ReturnType<typeof vi.fn>) {
  return fetchMock.mock.calls[0][1].headers as Headers;
}

describe("apiFetch version preconditions", () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    vi.stubGlobal("fetch", fetchMock);
    fetchMock.mockResolvedValue(new Response(JSON.stringify({ version: 4 }), { status: 200 }));
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.clearAllMocks();
  });

  it("sends the expected version as If-Match", async () => {
    // Act
    await apiFetch("/api/patients/p-1", { method: "PUT", body: {}, expectedVersion: 3 });

    // Assert
    expect(sentHeaders(fetchMock).get("If-Match")).toBe('"3"');
  });

  it("sends If-Match for version zero", async () => {
    // Act
    await apiFetch("/api/patients/p-1", { method: "PUT", body: {}, expectedVersion: 0 });

    // Assert
    expect(sentHeaders(fetchMock).get("If-Match")).toBe('"0"');
  });

  it("asks to create the record when no version is expected", async () => {
    // Act
    await apiFetch("/api/practice-profile", { method: "PUT", body: {}, expectedVersion: null });

    // Assert
    expect(sentHeaders(fetchMock).get("If-None-Match")).toBe("*");
    expect(sentHeaders(fetchMock).has("If-Match")).toBe(false);
  });

  it("sends no precondition when none is given", async () => {
    // Act
    await apiFetch("/api/patients", { method: "POST", body: {} });

    // Assert
    expect(sentHeaders(fetchMock).has("If-Match")).toBe(false);
    expect(sentHeaders(fetchMock).has("If-None-Match")).toBe(false);
  });

  it("does not pass the expected version on to fetch", async () => {
    // Act
    await apiFetch("/api/patients/p-1", { method: "PUT", body: {}, expectedVersion: 3 });

    // Assert
    expect(fetchMock.mock.calls[0][1]).not.toHaveProperty("expectedVersion");
  });
});

describe("isStaleVersion", () => {
  it("recognises a rejected stale write", () => {
    // Act
    const stale = isStaleVersion(new ApiClientError("Stale", 412, "stale_resource_version"));

    // Assert
    expect(stale).toBe(true);
  });

  it.each([
    ["another API error", new ApiClientError("Conflict", 409, "session_record_signed")],
    ["a plain error", new Error("Network")],
  ])("ignores %s", (_label, error) => {
    // Act
    const stale = isStaleVersion(error);

    // Assert
    expect(stale).toBe(false);
  });
});
