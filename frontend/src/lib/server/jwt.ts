import "server-only";

import { createRemoteJWKSet, errors, jwtVerify, type JWTPayload, type JWTVerifyGetKey } from "jose";
import { oidcConfig } from "@/lib/server/oidc-config";
import type { Role } from "@/lib/types";

const JWT_ALGORITHM = "RS256";
const API_AUDIENCE = "clinicflow-api";
const VALID_ROLES: readonly Role[] = ["ADMIN", "PSYCHOLOGIST", "PATIENT"];
export const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

export type VerifiedAccessTokenClaims = {
  userId: string;
  email: string;
  // The realm puts exactly one ClinicFlow role in the roles claim (ADR-027).
  role: Role;
  name: string | null;
  expiresAt: number;
};

export type AccessTokenVerification =
  | { status: "verified"; principal: VerifiedAccessTokenClaims }
  | { status: "invalid" }
  | { status: "unavailable" };

let realmKeySet: JWTVerifyGetKey | undefined;

// Keys come from the realm JWKS on the private network and follow Keycloak's key rotation.
export function realmKeys(): JWTVerifyGetKey {
  realmKeySet ??= createRemoteJWKSet(new URL(oidcConfig().jwksEndpoint));
  return realmKeySet;
}

export async function verifyAccessToken(token: string, keys?: JWTVerifyGetKey): Promise<AccessTokenVerification> {
  try {
    const { payload } = await jwtVerify(token, keys ?? realmKeys(), {
      algorithms: [JWT_ALGORITHM],
      issuer: oidcConfig().issuer,
      audience: API_AUDIENCE,
      requiredClaims: ["exp", "sub", "email", "roles"],
    });
    const principal = toVerifiedPrincipal(payload);
    return principal ? { status: "verified", principal } : { status: "invalid" };
  } catch (error) {
    return isKeySetUnavailable(error) ? { status: "unavailable" } : { status: "invalid" };
  }
}

// A JWKS that cannot be fetched is an outage, not a bad token.
export function isKeySetUnavailable(error: unknown) {
  return error instanceof errors.JWKSTimeout || !(error instanceof errors.JOSEError);
}

function toVerifiedPrincipal(payload: JWTPayload): VerifiedAccessTokenClaims | null {
  const { sub: userId, email, name, exp: expiresAt } = payload;
  const role = singleRole(payload.roles);

  if (
    typeof userId !== "string" ||
    !UUID_PATTERN.test(userId) ||
    typeof email !== "string" ||
    email.length === 0 ||
    !role ||
    typeof expiresAt !== "number" ||
    !Number.isFinite(expiresAt)
  ) {
    return null;
  }

  return { userId, email, role, name: typeof name === "string" && name.length > 0 ? name : null, expiresAt };
}

// Unknown values are ignored; zero or several ClinicFlow roles break the contract.
function singleRole(roles: unknown): Role | null {
  if (!Array.isArray(roles)) return null;
  const known = roles.filter((role): role is Role => VALID_ROLES.includes(role as Role));
  return known.length === 1 ? known[0] : null;
}
