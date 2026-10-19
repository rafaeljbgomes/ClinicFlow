import { mkdtemp, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { exportSPKI, generateKeyPair, SignJWT } from "jose";
import { afterEach, beforeAll, describe, expect, it, vi } from "vitest";

const userId = "9b38f432-6b8f-4d75-9e6a-e775cc4b0175";
let privateKey: CryptoKey;
let publicKeyPem: string;
let keyDirectory: string | undefined;

beforeAll(async () => {
  const keyPair = await generateKeyPair("RS256");
  privateKey = keyPair.privateKey;
  publicKeyPem = await exportSPKI(keyPair.publicKey);
});

afterEach(async () => {
  delete process.env.CLINICFLOW_SECURITY_JWT_PUBLIC_KEY;
  delete process.env.CLINICFLOW_SECURITY_JWT_PUBLIC_KEY_PATH;
  delete process.env.CLINICFLOW_SECURITY_JWT_ISSUER;
  vi.resetModules();
  if (keyDirectory) {
    await rm(keyDirectory, { recursive: true, force: true });
    keyDirectory = undefined;
  }
});

describe("verifyAccessToken", () => {
  it("verifies a valid RS256 token with all ClinicFlow claims", async () => {
    // Arrange
    const verifier = await inlineKeyVerifier();
    const token = await signedToken();

    // Act
    const result = await verifier.verifyAccessToken(token);

    // Assert
    expect(result).toMatchObject({
      status: "verified",
      principal: {
        userId,
        email: "admin@example.com",
        role: "ADMIN",
      },
    });
  });

  it.each([
    ["an expired token", () => signedToken({ expiresIn: "-1s" })],
    ["a token from another issuer", () => signedToken({ issuer: "other-issuer" })],
    [
      "a token signed with another key",
      async () => signedToken({ signingKey: (await generateKeyPair("RS256")).privateKey }),
    ],
    ["a token with an unknown role", () => signedToken({ claims: { role: "CLINICIAN" } })],
    ["a token without an email claim", () => signedToken({ claims: { email: undefined } })],
    [
      "a token whose subject differs from the user id",
      () => signedToken({ subject: "b1e674bd-a12d-4d16-916d-53ccde5b933d" }),
    ],
  ])("rejects %s", async (_scenario, createToken) => {
    // Arrange
    const verifier = await inlineKeyVerifier();
    const token = await createToken();

    // Act
    const result = await verifier.verifyAccessToken(token);

    // Assert
    expect(result).toEqual({ status: "invalid" });
  });

  it("loads a public key from the configured file path", async () => {
    // Arrange
    keyDirectory = await mkdtemp(join(tmpdir(), "clinicflow-jwt-"));
    const keyPath = join(keyDirectory, "jwt-public.pem");
    await writeFile(keyPath, publicKeyPem, "utf8");
    process.env.CLINICFLOW_SECURITY_JWT_PUBLIC_KEY_PATH = keyPath;
    const verifier = await import("@/lib/server/jwt");
    const token = await signedToken();

    // Act
    const result = await verifier.verifyAccessToken(token);

    // Assert
    expect(result).toMatchObject({ status: "verified" });
  });

  it("fails closed when no public key can be loaded", async () => {
    // Arrange
    const verifier = await import("@/lib/server/jwt");
    const token = await signedToken();

    // Act
    const result = await verifier.verifyAccessToken(token);

    // Assert
    expect(result).toEqual({ status: "unavailable" });
  });
});

async function inlineKeyVerifier() {
  process.env.CLINICFLOW_SECURITY_JWT_PUBLIC_KEY = publicKeyPem;
  return import("@/lib/server/jwt");
}

async function signedToken({
  claims = {},
  expiresIn = "5m",
  issuer = "clinicflow-auth",
  signingKey = privateKey,
  subject = userId,
}: {
  claims?: Record<string, unknown>;
  expiresIn?: string;
  issuer?: string;
  signingKey?: CryptoKey;
  subject?: string;
} = {}) {
  return new SignJWT({
    userId,
    email: "admin@example.com",
    role: "ADMIN",
    ...claims,
  })
    .setProtectedHeader({ alg: "RS256" })
    .setIssuer(issuer)
    .setSubject(subject)
    .setIssuedAt()
    .setExpirationTime(expiresIn)
    .sign(signingKey);
}
