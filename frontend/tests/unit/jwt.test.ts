import { mkdtemp, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { exportSPKI, generateKeyPair, SignJWT } from "jose";
import { afterEach, beforeAll, describe, expect, it, vi } from "vitest";

const userId = "9b38f432-6b8f-4d75-9e6a-e775cc4b0175";
let privateKey: CryptoKey;
let publicKeyPem: string;

beforeAll(async () => {
  const keyPair = await generateKeyPair("RS256");
  privateKey = keyPair.privateKey;
  publicKeyPem = await exportSPKI(keyPair.publicKey);
});

afterEach(() => {
  delete process.env.CLINICFLOW_SECURITY_JWT_PUBLIC_KEY;
  delete process.env.CLINICFLOW_SECURITY_JWT_PUBLIC_KEY_PATH;
  delete process.env.CLINICFLOW_SECURITY_JWT_ISSUER;
  vi.resetModules();
});

describe("verifyAccessToken", () => {
  it("verifies a valid RS256 token with all ClinicFlow claims", async () => {
    const result = await verifyInline(await signedToken());

    expect(result).toMatchObject({
      status: "verified",
      principal: {
        userId,
        email: "admin@example.com",
        role: "ADMIN",
      },
    });
  });

  it("rejects expired, wrong-issuer, wrongly signed, and incomplete tokens", async () => {
    expect(await verifyInline(await signedToken({ expiresIn: "-1s" }))).toEqual({ status: "invalid" });
    expect(await verifyInline(await signedToken({ issuer: "other-issuer" }))).toEqual({ status: "invalid" });

    const anotherKeyPair = await generateKeyPair("RS256");
    expect(await verifyInline(await signedToken({ signingKey: anotherKeyPair.privateKey }))).toEqual({ status: "invalid" });
    expect(await verifyInline(await signedToken({ claims: { role: "CLINICIAN" } }))).toEqual({ status: "invalid" });
    expect(await verifyInline(await signedToken({ claims: { email: undefined } }))).toEqual({ status: "invalid" });
    expect(await verifyInline(await signedToken({ subject: "b1e674bd-a12d-4d16-916d-53ccde5b933d" }))).toEqual({ status: "invalid" });
  });

  it("loads a public key from the configured file path", async () => {
    const directory = await mkdtemp(join(tmpdir(), "clinicflow-jwt-"));
    const keyPath = join(directory, "jwt-public.pem");
    await writeFile(keyPath, publicKeyPem, "utf8");
    process.env.CLINICFLOW_SECURITY_JWT_PUBLIC_KEY_PATH = keyPath;

    try {
      const verifier = await import("@/lib/server/jwt");
      await expect(verifier.verifyAccessToken(await signedToken())).resolves.toMatchObject({ status: "verified" });
    } finally {
      await rm(directory, { recursive: true, force: true });
    }
  });

  it("fails closed when no public key can be loaded", async () => {
    const verifier = await import("@/lib/server/jwt");

    await expect(verifier.verifyAccessToken(await signedToken())).resolves.toEqual({ status: "unavailable" });
  });
});

async function verifyInline(token: string) {
  process.env.CLINICFLOW_SECURITY_JWT_PUBLIC_KEY = publicKeyPem;
  const verifier = await import("@/lib/server/jwt");
  return verifier.verifyAccessToken(token);
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
