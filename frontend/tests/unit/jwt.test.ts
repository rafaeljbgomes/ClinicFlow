import { errors, generateKeyPair } from "jose";
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
import { verifyAccessToken } from "@/lib/server/jwt";
import { accessToken, stubOidcEnvironment, testRealm, USER_ID, type TestRealm } from "./realm";

let realm: TestRealm;

beforeAll(async () => {
  realm = await testRealm();
});

beforeEach(() => stubOidcEnvironment());
afterEach(() => vi.unstubAllEnvs());

describe("verifyAccessToken", () => {
  it("verifies a realm access token and identifies the caller by sub", async () => {
    // Arrange
    const token = await accessToken(realm);

    // Act
    const result = await verifyAccessToken(token, realm.keys);

    // Assert
    expect(result).toEqual({
      status: "verified",
      principal: {
        userId: USER_ID,
        email: "sofia.almeida@demo.clinicflow.local",
        role: "PSYCHOLOGIST",
        name: "Sofia Almeida",
        expiresAt: expect.any(Number),
      },
    });
  });

  it("ignores roles outside the ClinicFlow set", async () => {
    // Arrange
    const token = await accessToken(realm, { claims: { roles: ["offline_access", "ADMIN", "uma_authorization"] } });

    // Act
    const result = await verifyAccessToken(token, realm.keys);

    // Assert
    expect(result).toMatchObject({ status: "verified", principal: { role: "ADMIN" } });
  });

  it("leaves the display name empty when the token has none", async () => {
    // Arrange
    const token = await accessToken(realm, { claims: { name: undefined } });

    // Act
    const result = await verifyAccessToken(token, realm.keys);

    // Assert
    expect(result).toMatchObject({ status: "verified", principal: { name: null } });
  });

  it.each([
    ["an expired token", () => accessToken(realm, { expiresIn: "-2m" })],
    ["a token from another issuer", () => accessToken(realm, { issuer: "clinicflow-auth" })],
    ["a token for another audience", () => accessToken(realm, { audience: "clinicflow-bff" })],
    ["a token signed with another key", async () => accessToken(realm, { signingKey: (await generateKeyPair("RS256")).privateKey })],
    ["a token without roles", () => accessToken(realm, { claims: { roles: undefined } })],
    ["a token with two ClinicFlow roles", () => accessToken(realm, { claims: { roles: ["PSYCHOLOGIST", "ADMIN"] } })],
    ["a token with only unknown roles", () => accessToken(realm, { claims: { roles: ["CLINICIAN"] } })],
    ["a token with a single-string role", () => accessToken(realm, { claims: { roles: "ADMIN" } })],
    ["a token whose roles are only under realm_access", () => accessToken(realm, { claims: { roles: undefined, realm_access: { roles: ["ADMIN"] } } })],
    ["a token without an email claim", () => accessToken(realm, { claims: { email: undefined } })],
    ["a token whose subject is not a UUID", () => accessToken(realm, { subject: "sofia" })],
  ])("rejects %s", async (_scenario, createToken) => {
    // Arrange
    const token = await createToken();

    // Act
    const result = await verifyAccessToken(token, realm.keys);

    // Assert
    expect(result).toEqual({ status: "invalid" });
  });

  it("rejects a token signed with a symmetric algorithm", async () => {
    // Arrange
    const [header, payload] = (await accessToken(realm)).split(".");
    const forged = `${Buffer.from(JSON.stringify({ alg: "HS256", kid: "realm-key" })).toString("base64url")}.${payload}.${header}`;

    // Act
    const result = await verifyAccessToken(forged, realm.keys);

    // Assert
    expect(result).toEqual({ status: "invalid" });
  });

  it("reports an unreachable key set as unavailable", async () => {
    // Arrange
    const token = await accessToken(realm);
    const unreachable = async () => {
      throw new TypeError("fetch failed");
    };

    // Act
    const result = await verifyAccessToken(token, unreachable);

    // Assert
    expect(result).toEqual({ status: "unavailable" });
  });

  it("reports a key set timeout as unavailable", async () => {
    // Arrange
    const token = await accessToken(realm);
    const slow = async () => {
      throw new errors.JWKSTimeout();
    };

    // Act
    const result = await verifyAccessToken(token, slow);

    // Assert
    expect(result).toEqual({ status: "unavailable" });
  });
});
