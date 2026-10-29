import type { AccessTokenVerification } from "@/lib/server/jwt";
import type { UserView } from "@/lib/types";

export type SessionResult =
  | { status: "authenticated"; user: UserView }
  | { status: "anonymous" }
  | { status: "unavailable" };

// The access token is the profile: sub, email, name and the single ClinicFlow role (ADR-027).
export function sessionFromVerification(verification: AccessTokenVerification | null): SessionResult {
  if (!verification || verification.status === "invalid") return { status: "anonymous" };
  if (verification.status === "unavailable") return { status: "unavailable" };
  const { userId, email, name, role } = verification.principal;
  return { status: "authenticated", user: { id: userId, email, role, fullName: name ?? email, enabled: true } };
}
