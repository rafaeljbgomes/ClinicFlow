import { jsonError } from "@/lib/server/bff";

// auth-service owned this lookup and does not accept Keycloak tokens. Its replacement, the Keycloak
// Admin API or removal, is decided when auth-service is retired (#90, ADR-027).
export async function GET() {
  return jsonError(501, "User lookup is not available while accounts move to Keycloak", { code: "user_lookup_unavailable" });
}
