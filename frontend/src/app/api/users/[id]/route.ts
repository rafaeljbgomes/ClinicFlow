import { NextResponse } from "next/server";
import { jsonError, readTokenCookie } from "@/lib/server/bff";
import { UUID_PATTERN, verifyAccessToken } from "@/lib/server/jwt";
import { lookUpUser } from "@/lib/server/user-directory";
import type { DirectoryUser } from "@/lib/types";

type RouteContext = {
  params: Promise<{ id: string }>;
};

// The administrator's user lookup, read from Keycloak with a read-only service account (ADR-031).
export async function GET(_request: Request, context: RouteContext) {
  const token = await readTokenCookie();
  if (!token) return jsonError(401, "Authentication is required");
  const verification = await verifyAccessToken(token);
  if (verification.status === "unavailable") return jsonError(503, "Session could not be verified");
  if (verification.status === "invalid") return jsonError(401, "Session could not be verified");
  if (verification.principal.role !== "ADMIN") return jsonError(403, "Administrator access is required");

  const { id } = await context.params;
  if (!UUID_PATTERN.test(id)) return jsonError(400, "User id must be a UUID", { code: "invalid_user_id" });

  const result = await lookUpUser(id.toLowerCase());
  if (result.status === "not-found") return jsonError(404, "User not found", { code: "user_not_found" });
  if (result.status === "unavailable") {
    return jsonError(503, "User lookup is unavailable", { code: "user_lookup_unavailable" });
  }
  return NextResponse.json<DirectoryUser>(result.user, { headers: { "Cache-Control": "no-store" } });
}
