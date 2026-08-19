import { describe, expect, it } from "vitest";
import { classifySession } from "@/lib/session-result";

const user = {
  id: "e2b930b6-8661-42a7-98bf-d62d1e8ebc42",
  email: "psychologist@example.com",
  fullName: "ClinicFlow Psychologist",
  role: "PSYCHOLOGIST" as const,
};

describe("classifySession", () => {
  it("treats a missing cookie and an authentication rejection as anonymous", () => {
    expect(classifySession(undefined)).toEqual({ status: "anonymous" });
    expect(classifySession("stale", { ok: false, status: 401, data: null })).toEqual({
      status: "anonymous",
    });
  });

  it("distinguishes upstream failures from invalid authentication payloads", () => {
    expect(
      classifySession("token", {
        ok: false,
        status: 503,
        data: null,
        unavailable: true,
      })
    ).toEqual({ status: "unavailable" });
    expect(classifySession("token", { ok: true, status: 200, data: {} })).toEqual({
      status: "invalid-response",
    });
    expect(
      classifySession("token", {
        ok: true,
        status: 200,
        data: null,
        invalidJson: true,
      })
    ).toEqual({ status: "invalid-response" });
  });

  it("returns a validated authenticated user", () => {
    expect(classifySession("token", { ok: true, status: 200, data: user })).toEqual({
      status: "authenticated",
      user,
    });
  });
});
