import { describe, expect, it } from "vitest";
import { classifySession } from "@/lib/session-result";

const user = {
  id: "e2b930b6-8661-42a7-98bf-d62d1e8ebc42",
  email: "psychologist@example.com",
  fullName: "ClinicFlow Psychologist",
  role: "PSYCHOLOGIST" as const,
};

describe("classifySession", () => {
  it("treats a missing cookie as anonymous", () => {
    // Act
    const session = classifySession(undefined);

    // Assert
    expect(session).toEqual({ status: "anonymous" });
  });

  it("treats an authentication rejection as anonymous", () => {
    // Arrange
    const rejection = { ok: false, status: 401, data: null };

    // Act
    const session = classifySession("stale", rejection);

    // Assert
    expect(session).toEqual({ status: "anonymous" });
  });

  it("reports an upstream failure as unavailable", () => {
    // Arrange
    const failure = { ok: false, status: 503, data: null, unavailable: true };

    // Act
    const session = classifySession("token", failure);

    // Assert
    expect(session).toEqual({ status: "unavailable" });
  });

  it.each([
    ["an empty user payload", { ok: true, status: 200, data: {} }],
    ["an unparseable payload", { ok: true, status: 200, data: null, invalidJson: true }],
  ])("reports %s as an invalid response", (_payload, result) => {
    // Act
    const session = classifySession("token", result);

    // Assert
    expect(session).toEqual({ status: "invalid-response" });
  });

  it("returns a validated authenticated user", () => {
    // Arrange
    const result = { ok: true, status: 200, data: user };

    // Act
    const session = classifySession("token", result);

    // Assert
    expect(session).toEqual({ status: "authenticated", user });
  });
});
