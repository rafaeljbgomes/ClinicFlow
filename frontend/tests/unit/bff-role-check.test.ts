import { NextRequest } from "next/server";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const { cookiesMock, verifyAccessTokenMock } = vi.hoisted(() => ({
  cookiesMock: vi.fn(),
  verifyAccessTokenMock: vi.fn(),
}));

vi.mock("next/headers", () => ({ cookies: cookiesMock }));
vi.mock("@/lib/server/jwt", () => ({ verifyAccessToken: verifyAccessTokenMock }));

import { proxyRequest } from "@/lib/server/bff";

const principal = {
  userId: "9b38f432-6b8f-4d75-9e6a-e775cc4b0175",
  email: "admin@example.com",
  role: "ADMIN" as const,
  expiresAt: 2_000_000_000,
};

describe("proxyRequest role checks", () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    cookiesMock.mockResolvedValue({ get: () => ({ value: "signed-access-token" }) });
    verifyAccessTokenMock.mockResolvedValue({ status: "verified", principal });
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    fetchMock.mockReset();
    vi.unstubAllGlobals();
    vi.clearAllMocks();
  });

  it("forwards an allowed request without a /users/me roundtrip", async () => {
    fetchMock.mockResolvedValue(new Response(JSON.stringify({ deliveries: [] }), { status: 200 }));
    const project = vi.fn((payload, verifiedPrincipal) => ({ payload, verifiedPrincipal }));

    const response = await proxyRequest(
      new NextRequest("http://localhost/api/notifications"),
      "notification",
      "/notifications",
      { csrf: false, roles: ["ADMIN"], project }
    );

    expect(response.status).toBe(200);
    expect(verifyAccessTokenMock).toHaveBeenCalledWith("signed-access-token");
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0][0]).toBe("http://localhost:8084/notifications");
    expect(project).toHaveBeenCalledWith({ deliveries: [] }, principal);
  });

  it("rejects a valid but unauthorized role before any downstream request", async () => {
    verifyAccessTokenMock.mockResolvedValue({
      status: "verified",
      principal: { ...principal, role: "PATIENT" },
    });

    const response = await proxyRequest(
      new NextRequest("http://localhost/api/users/123"),
      "auth",
      "/users/123",
      { csrf: false, roles: ["ADMIN"] }
    );

    expect(response.status).toBe(403);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it.each([
    ["invalid", 401],
    ["unavailable", 503],
  ] as const)("fails closed for a %s token verification result", async (status, expectedStatus) => {
    verifyAccessTokenMock.mockResolvedValue({ status });

    const response = await proxyRequest(
      new NextRequest("http://localhost/api/users/123"),
      "auth",
      "/users/123",
      { csrf: false, roles: ["ADMIN"] }
    );

    expect(response.status).toBe(expectedStatus);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
