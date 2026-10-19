import { NextRequest } from "next/server";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const { cookiesMock, verifyAccessTokenMock } = vi.hoisted(() => ({
  cookiesMock: vi.fn(),
  verifyAccessTokenMock: vi.fn(),
}));

vi.mock("next/headers", () => ({ cookies: cookiesMock }));
vi.mock("@/lib/server/jwt", () => ({ verifyAccessToken: verifyAccessTokenMock }));

import { POST } from "@/app/api/session-records/[id]/sign/route";

const sessionId = "6f1c1a3e-56d4-4f0e-9d37-4d1f3a2b9c10";
const psychologist = {
  userId: "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
  email: "psychologist@example.com",
  role: "PSYCHOLOGIST" as const,
  expiresAt: 2_000_000_000,
};
const cookieValues: Record<string, string> = {
  clinicflow_access_token: "signed-access-token",
  clinicflow_csrf: "csrf-token",
};

function signRequest(csrf?: string) {
  return new NextRequest(`http://localhost/api/session-records/${sessionId}/sign`, {
    method: "POST",
    headers: csrf ? { "x-csrf-token": csrf } : {},
  });
}

function context(id = sessionId) {
  return { params: Promise.resolve({ id }) };
}

describe("session record sign route", () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    cookiesMock.mockResolvedValue({
      get: (name: string) => (cookieValues[name] ? { value: cookieValues[name] } : undefined),
    });
    verifyAccessTokenMock.mockResolvedValue({ status: "verified", principal: psychologist });
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    fetchMock.mockReset();
    vi.unstubAllGlobals();
    vi.clearAllMocks();
  });

  describe("for a psychologist with a matching CSRF token", () => {
    beforeEach(() => {
      fetchMock.mockResolvedValue(
        new Response(JSON.stringify({ id: sessionId, noteStatus: "SIGNED" }), { status: 200 })
      );
    });

    it("returns the downstream status", async () => {
      // Arrange
      const request = signRequest("csrf-token");

      // Act
      const response = await POST(request, context());

      // Assert
      expect(response.status).toBe(200);
    });

    it("forwards one POST to the clinical service sign endpoint", async () => {
      // Arrange
      const request = signRequest("csrf-token");

      // Act
      await POST(request, context());

      // Assert
      expect(fetchMock).toHaveBeenCalledTimes(1);
      expect(fetchMock.mock.calls[0][0]).toBe(`http://localhost:8085/session-records/${sessionId}/sign`);
      expect(fetchMock.mock.calls[0][1]).toMatchObject({ method: "POST" });
    });
  });

  describe.each([
    ["missing", undefined],
    ["mismatched", "other-token"],
  ])("with a %s CSRF token", (_label, csrf) => {
    it("rejects the request", async () => {
      // Arrange
      const request = signRequest(csrf);

      // Act
      const response = await POST(request, context());

      // Assert
      expect(response.status).toBe(403);
    });

    it("sends no downstream request", async () => {
      // Arrange
      const request = signRequest(csrf);

      // Act
      await POST(request, context());

      // Assert
      expect(fetchMock).not.toHaveBeenCalled();
    });
  });

  describe.each(["PATIENT", "ADMIN"] as const)("for the %s role", (role) => {
    beforeEach(() => {
      verifyAccessTokenMock.mockResolvedValue({ status: "verified", principal: { ...psychologist, role } });
    });

    it("rejects the request", async () => {
      // Arrange
      const request = signRequest("csrf-token");

      // Act
      const response = await POST(request, context());

      // Assert
      expect(response.status).toBe(403);
    });

    it("sends no downstream request", async () => {
      // Arrange
      const request = signRequest("csrf-token");

      // Act
      await POST(request, context());

      // Assert
      expect(fetchMock).not.toHaveBeenCalled();
    });
  });

  it("keeps the record id inside the session-records path", async () => {
    // Arrange
    fetchMock.mockResolvedValue(new Response(null, { status: 404 }));
    const request = signRequest("csrf-token");

    // Act
    await POST(request, context("../clinical-cases/x"));

    // Assert
    expect(fetchMock.mock.calls[0][0]).toBe(
      "http://localhost:8085/session-records/..%2Fclinical-cases%2Fx/sign"
    );
  });

  it("passes the stable signed-note conflict code through to the browser", async () => {
    // Arrange
    fetchMock.mockResolvedValue(
      new Response(
        JSON.stringify({ status: 409, detail: "Signed session records cannot be changed", code: "session_record_signed" }),
        { status: 409 }
      )
    );
    const request = signRequest("csrf-token");

    // Act
    const response = await POST(request, context());

    // Assert
    expect(response.status).toBe(409);
    await expect(response.json()).resolves.toMatchObject({
      code: "session_record_signed",
      message: "Signed session records cannot be changed",
    });
  });
});
