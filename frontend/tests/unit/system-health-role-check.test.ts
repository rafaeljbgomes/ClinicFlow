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

  it("uses verified administrator claims and queries each health endpoint", async () => {
    const response = await GET();

    expect(response.status).toBe(200);
    expect(verifyAccessTokenMock).toHaveBeenCalledWith("signed-access-token");
    expect(backendHealthGetMock).toHaveBeenCalledTimes(5);
  });

  it("rejects a non-administrator before querying health endpoints", async () => {
    verifyAccessTokenMock.mockResolvedValue({
      status: "verified",
      principal: { ...adminPrincipal, role: "PSYCHOLOGIST" },
    });

    const response = await GET();

    expect(response.status).toBe(403);
    expect(backendHealthGetMock).not.toHaveBeenCalled();
  });
});
