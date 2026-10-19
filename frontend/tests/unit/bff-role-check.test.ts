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

  describe("for an allowed role", () => {
    beforeEach(() => {
      fetchMock.mockResolvedValue(new Response(JSON.stringify({ deliveries: [] }), { status: 200 }));
    });

    it("verifies the access token from the session cookie", async () => {
      // Arrange
      const request = notificationsRequest();

      // Act
      await proxyRequest(request, "notification", "/notifications", { csrf: false, roles: ["ADMIN"] });

      // Assert
      expect(verifyAccessTokenMock).toHaveBeenCalledWith("signed-access-token");
    });

    it("forwards the request once without a /users/me roundtrip", async () => {
      // Arrange
      const request = notificationsRequest();

      // Act
      await proxyRequest(request, "notification", "/notifications", { csrf: false, roles: ["ADMIN"] });

      // Assert
      expect(fetchMock).toHaveBeenCalledTimes(1);
      expect(fetchMock.mock.calls[0][0]).toBe("http://localhost:8084/notifications");
    });

    it("returns the downstream status", async () => {
      // Arrange
      const request = notificationsRequest();

      // Act
      const response = await proxyRequest(request, "notification", "/notifications", {
        csrf: false,
        roles: ["ADMIN"],
      });

      // Assert
      expect(response.status).toBe(200);
    });

    it("projects the downstream payload with the verified principal", async () => {
      // Arrange
      const request = notificationsRequest();
      const project = vi.fn((payload, verifiedPrincipal) => ({ payload, verifiedPrincipal }));

      // Act
      await proxyRequest(request, "notification", "/notifications", { csrf: false, roles: ["ADMIN"], project });

      // Assert
      expect(project).toHaveBeenCalledWith({ deliveries: [] }, principal);
    });
  });

  describe("for a valid but unauthorized role", () => {
    beforeEach(() => {
      verifyAccessTokenMock.mockResolvedValue({
        status: "verified",
        principal: { ...principal, role: "PATIENT" },
      });
    });

    it("responds with 403", async () => {
      // Arrange
      const request = userLookupRequest();

      // Act
      const response = await proxyRequest(request, "auth", "/users/123", { csrf: false, roles: ["ADMIN"] });

      // Assert
      expect(response.status).toBe(403);
    });

    it("rejects before any downstream request", async () => {
      // Arrange
      const request = userLookupRequest();

      // Act
      await proxyRequest(request, "auth", "/users/123", { csrf: false, roles: ["ADMIN"] });

      // Assert
      expect(fetchMock).not.toHaveBeenCalled();
    });
  });

  it.each([
    ["invalid", 401],
    ["unavailable", 503],
  ] as const)("fails closed with %s token verification as %i", async (status, expectedStatus) => {
    // Arrange
    verifyAccessTokenMock.mockResolvedValue({ status });
    const request = userLookupRequest();

    // Act
    const response = await proxyRequest(request, "auth", "/users/123", { csrf: false, roles: ["ADMIN"] });

    // Assert
    expect(response.status).toBe(expectedStatus);
  });

  it.each(["invalid", "unavailable"] as const)(
    "does not call downstream after %s token verification",
    async (status) => {
      // Arrange
      verifyAccessTokenMock.mockResolvedValue({ status });
      const request = userLookupRequest();

      // Act
      await proxyRequest(request, "auth", "/users/123", { csrf: false, roles: ["ADMIN"] });

      // Assert
      expect(fetchMock).not.toHaveBeenCalled();
    }
  );
});

function notificationsRequest() {
  return new NextRequest("http://localhost/api/notifications");
}

function userLookupRequest() {
  return new NextRequest("http://localhost/api/users/123");
}
