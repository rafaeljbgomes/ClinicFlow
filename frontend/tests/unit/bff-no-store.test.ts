import { NextRequest } from "next/server";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const { cookiesMock, verifyAccessTokenMock } = vi.hoisted(() => ({
  cookiesMock: vi.fn(),
  verifyAccessTokenMock: vi.fn(),
}));

vi.mock("next/headers", () => ({ cookies: cookiesMock }));
vi.mock("@/lib/server/jwt", () => ({ verifyAccessToken: verifyAccessTokenMock }));

import { GET as systemHealth } from "@/app/api/system/health/route";
import { jsonError, proxyRequest } from "@/lib/server/bff";

const signedIn = {
  get: (name: string) => (name === "clinicflow_access_token" ? { value: "signed-access-token" } : undefined),
};

function readPatients(options: Parameters<typeof proxyRequest>[3] = {}) {
  return proxyRequest(new NextRequest("http://localhost/api/patients"), "patient", "/patients", options);
}

describe("BFF response caching", () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    cookiesMock.mockResolvedValue(signedIn);
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    fetchMock.mockReset();
    vi.unstubAllGlobals();
    vi.clearAllMocks();
  });

  it("forbids storing a proxied read", async () => {
    // Arrange
    fetchMock.mockResolvedValue(new Response(JSON.stringify([{ id: "patient-1" }]), { status: 200 }));

    // Act
    const response = await readPatients();

    // Assert
    expect(response.headers.get("cache-control")).toBe("no-store");
  });

  it("forbids storing a projected read", async () => {
    // Arrange
    fetchMock.mockResolvedValue(new Response(JSON.stringify([{ id: "patient-1" }]), { status: 200 }));

    // Act
    const response = await readPatients({ project: (payload) => payload });

    // Assert
    expect(response.headers.get("cache-control")).toBe("no-store");
  });

  it("forbids storing a backend error", async () => {
    // Arrange
    fetchMock.mockResolvedValue(new Response(JSON.stringify({ detail: "Not found" }), { status: 404 }));

    // Act
    const response = await readPatients();

    // Assert
    expect(response.headers.get("cache-control")).toBe("no-store");
  });

  it("forbids storing an empty backend response", async () => {
    // Arrange
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));

    // Act
    const response = await readPatients();

    // Assert
    expect(response.headers.get("cache-control")).toBe("no-store");
  });

  it("does not let the service relax the policy", async () => {
    // Arrange
    fetchMock.mockResolvedValue(
      new Response("[]", { status: 200, headers: { "Cache-Control": "public, max-age=3600" } })
    );

    // Act
    const response = await readPatients();

    // Assert
    expect(response.headers.get("cache-control")).toBe("no-store");
  });

  it("still returns the version tag of a record", async () => {
    // Arrange
    fetchMock.mockResolvedValue(new Response("{}", { status: 200, headers: { ETag: '"3"' } }));

    // Act
    const response = await readPatients();

    // Assert
    expect(response.headers.get("etag")).toBe('"3"');
  });

  it("forbids storing an error raised by the BFF itself", () => {
    // Act
    const response = jsonError(401, "Authentication is required");

    // Assert
    expect(response.headers.get("cache-control")).toBe("no-store");
  });

  it("forbids storing the system health report", async () => {
    // Arrange
    verifyAccessTokenMock.mockResolvedValue({
      status: "verified",
      principal: {
        userId: "9b38f432-6b8f-4d75-9e6a-e775cc4b0175",
        email: "admin@example.com",
        role: "ADMIN",
        expiresAt: 2_000_000_000,
      },
    });
    fetchMock.mockImplementation(async () => new Response(JSON.stringify({ status: "UP" }), { status: 200 }));

    // Act
    const response = await systemHealth();

    // Assert
    expect(response.headers.get("cache-control")).toBe("no-store");
  });
});
