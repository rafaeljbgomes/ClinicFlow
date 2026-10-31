import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const { lookUpUserMock, readTokenCookieMock, verifyAccessTokenMock } = vi.hoisted(() => ({
  lookUpUserMock: vi.fn(),
  readTokenCookieMock: vi.fn(),
  verifyAccessTokenMock: vi.fn(),
}));

vi.mock("@/lib/server/bff", () => ({
  readTokenCookie: readTokenCookieMock,
  jsonError: (status: number, message: string, fields = {}) => Response.json({ status, message, ...fields }, { status }),
}));
vi.mock("@/lib/server/jwt", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/server/jwt")>()),
  verifyAccessToken: verifyAccessTokenMock,
}));
vi.mock("@/lib/server/user-directory", () => ({ lookUpUser: lookUpUserMock }));

import { GET } from "@/app/api/users/[id]/route";

const USER_ID = "00000000-0000-4000-8000-000000000011";
const directoryUser = { id: USER_ID, email: "sofia.almeida@demo.clinicflow.local", fullName: "Sofia Almeida", role: "PSYCHOLOGIST", enabled: true };
const adminPrincipal = { userId: "00000000-0000-4000-8000-000000000001", email: "admin@demo.clinicflow.local", role: "ADMIN" as const, name: null, expiresAt: 2_000_000_000 };

describe("user lookup route", () => {
  beforeEach(() => {
    readTokenCookieMock.mockResolvedValue("signed-access-token");
    verifyAccessTokenMock.mockResolvedValue({ status: "verified", principal: adminPrincipal });
    lookUpUserMock.mockResolvedValue({ status: "found", user: directoryUser });
  });

  afterEach(() => vi.clearAllMocks());

  describe("for an administrator", () => {
    it("verifies the session access token", async () => {
      // Act
      await lookUp(USER_ID);

      // Assert
      expect(verifyAccessTokenMock).toHaveBeenCalledWith("signed-access-token");
    });

    it("returns the directory user", async () => {
      // Act
      const response = await lookUp(USER_ID);

      // Assert
      expect([response.status, await response.json()]).toEqual([200, directoryUser]);
    });

    it("keeps the answer out of shared caches", async () => {
      // Act
      const response = await lookUp(USER_ID);

      // Assert
      expect(response.headers.get("Cache-Control")).toBe("no-store");
    });

    it("looks the id up in lower case", async () => {
      // Act
      await lookUp(USER_ID.toUpperCase());

      // Assert
      expect(lookUpUserMock).toHaveBeenCalledWith(USER_ID);
    });

    it.each(["not-a-uuid", "../../clients", `${USER_ID}/role-mappings`, "00000000-0000-0000-0000-000000000000"])(
      "refuses the id %s before calling Keycloak",
      async (id) => {
        // Act
        const response = await lookUp(id);

        // Assert
        expect([response.status, lookUpUserMock.mock.calls.length]).toEqual([400, 0]);
      }
    );

    it("answers 404 for an unknown account", async () => {
      // Arrange
      lookUpUserMock.mockResolvedValue({ status: "not-found" });

      // Act
      const response = await lookUp(USER_ID);

      // Assert
      expect([response.status, (await response.json()).code]).toEqual([404, "user_not_found"]);
    });

    it("answers 503 when Keycloak cannot answer", async () => {
      // Arrange
      lookUpUserMock.mockResolvedValue({ status: "unavailable" });

      // Act
      const response = await lookUp(USER_ID);

      // Assert
      expect([response.status, (await response.json()).code]).toEqual([503, "user_lookup_unavailable"]);
    });
  });

  it("answers 401 without a session", async () => {
    // Arrange
    readTokenCookieMock.mockResolvedValue(undefined);

    // Act
    const response = await lookUp(USER_ID);

    // Assert
    expect(response.status).toBe(401);
  });

  it.each([
    ["invalid", 401],
    ["unavailable", 503],
  ] as const)("fails closed with %s token verification as %i", async (status, expectedStatus) => {
    // Arrange
    verifyAccessTokenMock.mockResolvedValue({ status });

    // Act
    const response = await lookUp(USER_ID);

    // Assert
    expect(response.status).toBe(expectedStatus);
  });

  describe.each(["PSYCHOLOGIST", "PATIENT"] as const)("for a %s", (role) => {
    beforeEach(() => {
      verifyAccessTokenMock.mockResolvedValue({ status: "verified", principal: { ...adminPrincipal, role } });
    });

    it("answers 403", async () => {
      // Act
      const response = await lookUp(USER_ID);

      // Assert
      expect(response.status).toBe(403);
    });

    it("does not call Keycloak", async () => {
      // Act
      await lookUp(USER_ID);

      // Assert
      expect(lookUpUserMock).not.toHaveBeenCalled();
    });
  });
});

function lookUp(id: string) {
  return GET(new Request(`http://localhost/api/users/${id}`), { params: Promise.resolve({ id }) });
}
