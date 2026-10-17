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

  it("forwards a psychologist's CSRF-protected sign request to the clinical service", async () => {
    fetchMock.mockResolvedValue(
      new Response(JSON.stringify({ id: sessionId, noteStatus: "SIGNED" }), { status: 200 })
    );

    const response = await POST(signRequest("csrf-token"), context());

    expect(response.status).toBe(200);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0][0]).toBe(`http://localhost:8085/session-records/${sessionId}/sign`);
    expect(fetchMock.mock.calls[0][1]).toMatchObject({ method: "POST" });
  });

  it("rejects a missing or mismatched CSRF token before any downstream request", async () => {
    expect((await POST(signRequest(), context())).status).toBe(403);
    expect((await POST(signRequest("other-token"), context())).status).toBe(403);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("rejects non-psychologist roles before any downstream request", async () => {
    for (const role of ["PATIENT", "ADMIN"] as const) {
      verifyAccessTokenMock.mockResolvedValue({ status: "verified", principal: { ...psychologist, role } });
      expect((await POST(signRequest("csrf-token"), context())).status).toBe(403);
    }
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("keeps the record id inside the session-records path", async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 404 }));

    await POST(signRequest("csrf-token"), context("../clinical-cases/x"));

    expect(fetchMock.mock.calls[0][0]).toBe(
      "http://localhost:8085/session-records/..%2Fclinical-cases%2Fx/sign"
    );
  });

  it("passes the stable signed-note conflict code through to the browser", async () => {
    fetchMock.mockResolvedValue(
      new Response(
        JSON.stringify({ status: 409, detail: "Signed session records cannot be changed", code: "session_record_signed" }),
        { status: 409 }
      )
    );

    const response = await POST(signRequest("csrf-token"), context());

    expect(response.status).toBe(409);
    await expect(response.json()).resolves.toMatchObject({
      code: "session_record_signed",
      message: "Signed session records cannot be changed",
    });
  });
});
