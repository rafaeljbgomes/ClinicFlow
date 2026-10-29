import { describe, expect, it } from "vitest";
import { sessionFromVerification } from "@/lib/session-result";

const principal = {
  userId: "00000000-0000-4000-8000-000000000011",
  email: "sofia.almeida@demo.clinicflow.local",
  role: "PSYCHOLOGIST" as const,
  name: "Sofia Almeida",
  expiresAt: 2_000_000_000,
};

describe("sessionFromVerification", () => {
  it("treats a missing cookie as anonymous", () => {
    // Act
    const session = sessionFromVerification(null);

    // Assert
    expect(session).toEqual({ status: "anonymous" });
  });

  it("treats a rejected token as anonymous", () => {
    // Act
    const session = sessionFromVerification({ status: "invalid" });

    // Assert
    expect(session).toEqual({ status: "anonymous" });
  });

  it("reports an unreachable key set as unavailable", () => {
    // Act
    const session = sessionFromVerification({ status: "unavailable" });

    // Assert
    expect(session).toEqual({ status: "unavailable" });
  });

  it("builds the profile from the verified claims", () => {
    // Act
    const session = sessionFromVerification({ status: "verified", principal });

    // Assert
    expect(session).toEqual({
      status: "authenticated",
      user: { id: principal.userId, email: principal.email, role: "PSYCHOLOGIST", fullName: "Sofia Almeida", enabled: true },
    });
  });

  it("shows the email when the token carries no name", () => {
    // Act
    const session = sessionFromVerification({ status: "verified", principal: { ...principal, name: null } });

    // Assert
    expect(session).toMatchObject({ user: { fullName: principal.email } });
  });
});
