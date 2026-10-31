import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const { backendHealthGetMock, readTokenCookieMock, verifyAccessTokenMock } = vi.hoisted(() => ({
  backendHealthGetMock: vi.fn(),
  readTokenCookieMock: vi.fn(),
  verifyAccessTokenMock: vi.fn(),
}));

vi.mock("@/lib/server/bff", () => ({
  backendHealthGet: backendHealthGetMock,
  readTokenCookie: readTokenCookieMock,
  jsonError: (status: number, message: string) => Response.json({ status, message }, { status }),
}));
vi.mock("@/lib/server/jwt", () => ({ verifyAccessToken: verifyAccessTokenMock }));

import { GET } from "@/app/api/system/health/route";

const adminPrincipal = {
  userId: "9b38f432-6b8f-4d75-9e6a-e775cc4b0175",
  email: "admin@example.com",
  role: "ADMIN" as const,
  expiresAt: 2_000_000_000,
};

describe("system health role check", () => {
  beforeEach(() => {
    readTokenCookieMock.mockResolvedValue("signed-access-token");
    verifyAccessTokenMock.mockResolvedValue({ status: "verified", principal: adminPrincipal });
    backendHealthGetMock.mockResolvedValue({
      ok: true,
      data: { status: "UP" },
      latencyMs: 12,
    });
  });

  afterEach(() => vi.clearAllMocks());

  describe("for an administrator", () => {
    it("verifies the session access token", async () => {
      // Act
      await GET();

      // Assert
      expect(verifyAccessTokenMock).toHaveBeenCalledWith("signed-access-token");
    });

    it("queries each service health endpoint", async () => {
      // Act
      await GET();

      // Assert
      expect(backendHealthGetMock).toHaveBeenCalledTimes(4);
    });

    it("responds with 200", async () => {
      // Act
      const response = await GET();

      // Assert
      expect(response.status).toBe(200);
    });
  });

  describe("for a non-administrator", () => {
    beforeEach(() => {
      verifyAccessTokenMock.mockResolvedValue({
        status: "verified",
        principal: { ...adminPrincipal, role: "PSYCHOLOGIST" },
      });
    });

    it("responds with 403", async () => {
      // Act
      const response = await GET();

      // Assert
      expect(response.status).toBe(403);
    });

    it("rejects before querying health endpoints", async () => {
      // Act
      await GET();

      // Assert
      expect(backendHealthGetMock).not.toHaveBeenCalled();
    });
  });
});
