import { createLocalJWKSet, exportJWK, generateKeyPair, SignJWT, type JWTVerifyGetKey } from "jose";
import { vi } from "vitest";

// A stand-in for the clinicflow realm: one signing key, its JWKS, and tokens in the ADR-027 shape.
export const ISSUER = "http://localhost:8180/realms/clinicflow";
export const INTERNAL_ISSUER = "http://keycloak:8080/realms/clinicflow";
export const PUBLIC_URL = "http://localhost:3000";
export const CLIENT_SECRET = "bff-client-secret-for-unit-tests";
export const USER_ID = "00000000-0000-4000-8000-000000000011";

export type TestRealm = { keys: JWTVerifyGetKey; privateKey: CryptoKey };

export async function testRealm(): Promise<TestRealm> {
  const { publicKey, privateKey } = await generateKeyPair("RS256");
  const jwk = { ...(await exportJWK(publicKey)), kid: "realm-key", alg: "RS256", use: "sig" };
  return { keys: createLocalJWKSet({ keys: [jwk] }), privateKey };
}

export function stubOidcEnvironment() {
  vi.stubEnv("CLINICFLOW_OIDC_ISSUER", ISSUER);
  vi.stubEnv("CLINICFLOW_OIDC_INTERNAL_ISSUER", INTERNAL_ISSUER);
  vi.stubEnv("CLINICFLOW_PUBLIC_URL", PUBLIC_URL);
  vi.stubEnv("CLINICFLOW_BFF_CLIENT_SECRET", CLIENT_SECRET);
}

type TokenOptions = {
  claims?: Record<string, unknown>;
  audience?: string;
  expiresIn?: string;
  issuer?: string;
  subject?: string;
  signingKey?: CryptoKey;
};

export function accessToken(realm: TestRealm, options: TokenOptions = {}) {
  return sign(realm, { email: "sofia.almeida@demo.clinicflow.local", name: "Sofia Almeida", roles: ["PSYCHOLOGIST"], azp: "clinicflow-bff" }, "clinicflow-api", options);
}

export function idToken(realm: TestRealm, nonce: string, options: TokenOptions = {}) {
  return sign(realm, { nonce, email: "sofia.almeida@demo.clinicflow.local" }, "clinicflow-bff", options);
}

function sign(realm: TestRealm, defaults: Record<string, unknown>, defaultAudience: string, options: TokenOptions) {
  const claims = { ...defaults, ...options.claims };
  for (const [name, value] of Object.entries(claims)) if (value === undefined) delete claims[name];
  return new SignJWT(claims)
    .setProtectedHeader({ alg: "RS256", kid: "realm-key", typ: "JWT" })
    .setIssuer(options.issuer ?? ISSUER)
    .setAudience(options.audience ?? defaultAudience)
    .setSubject(options.subject ?? USER_ID)
    .setIssuedAt()
    .setExpirationTime(options.expiresIn ?? "5m")
    .sign(options.signingKey ?? realm.privateKey);
}
