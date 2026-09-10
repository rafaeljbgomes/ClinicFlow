import "server-only";

import { readFile } from "node:fs/promises";
import { importSPKI, jwtVerify, type JWTPayload } from "jose";
import type { Role } from "@/lib/types";

const JWT_ALGORITHM = "RS256";
const DEFAULT_ISSUER = "clinicflow-auth";
const VALID_ROLES: readonly Role[] = ["ADMIN", "PSYCHOLOGIST", "PATIENT"];
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

export type VerifiedAccessTokenClaims = {
  userId: string;
  email: string;
  role: Role;
  expiresAt: number;
};

export type AccessTokenVerification =
  | { status: "verified"; principal: VerifiedAccessTokenClaims }
  | { status: "invalid" }
  | { status: "unavailable" };

let verificationKey: Promise<CryptoKey> | undefined;

export async function verifyAccessToken(token: string): Promise<AccessTokenVerification> {
  let key: CryptoKey;
  try {
    key = await getVerificationKey();
  } catch {
    return { status: "unavailable" };
  }

  try {
    const { payload } = await jwtVerify(token, key, {
      algorithms: [JWT_ALGORITHM],
      issuer: process.env.CLINICFLOW_SECURITY_JWT_ISSUER ?? DEFAULT_ISSUER,
      requiredClaims: ["exp", "sub", "userId", "email", "role"],
    });
    const principal = toVerifiedPrincipal(payload);
    return principal ? { status: "verified", principal } : { status: "invalid" };
  } catch {
    return { status: "invalid" };
  }
}

function getVerificationKey() {
  if (!verificationKey) {
    verificationKey = loadVerificationKey().catch((error) => {
      verificationKey = undefined;
      throw error;
    });
  }
  return verificationKey;
}

async function loadVerificationKey() {
  const inlinePem = process.env.CLINICFLOW_SECURITY_JWT_PUBLIC_KEY;
  const pem = inlinePem?.trim() || (await readPublicKeyFile());
  if (!pem) {
    throw new Error("JWT public key is not configured");
  }
  return importSPKI(pem, JWT_ALGORITHM);
}

async function readPublicKeyFile() {
  const path = process.env.CLINICFLOW_SECURITY_JWT_PUBLIC_KEY_PATH?.trim();
  return path ? readFile(path, "utf8") : undefined;
}

function toVerifiedPrincipal(payload: JWTPayload): VerifiedAccessTokenClaims | null {
  const userId = payload.userId;
  const email = payload.email;
  const role = payload.role;
  const expiresAt = payload.exp;

  if (
    typeof userId !== "string" ||
    !UUID_PATTERN.test(userId) ||
    payload.sub !== userId ||
    typeof email !== "string" ||
    email.length === 0 ||
    typeof role !== "string" ||
    !isRole(role) ||
    typeof expiresAt !== "number" ||
    !Number.isFinite(expiresAt)
  ) {
    return null;
  }

  return { userId, email, role, expiresAt };
}

function isRole(value: string): value is Role {
  return (VALID_ROLES as readonly string[]).includes(value);
}
