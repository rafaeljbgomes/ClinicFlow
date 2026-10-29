import { NextRequest } from "next/server";
import { beginAuthorization } from "@/lib/server/auth-routes";

// Keycloak's registration page creates PATIENT accounts only (ADR-027).
export async function GET(request: NextRequest) {
  return beginAuthorization(request, "register");
}
