import "server-only";

import { NextRequest, NextResponse } from "next/server";
import { OIDC_TRANSACTION_COOKIE, transactionCookieDefaults } from "@/lib/server/auth-cookies";
import { encodeTransaction, startAuthorization, type AuthorizationKind } from "@/lib/server/oidc";
import { oidcConfig } from "@/lib/server/oidc-config";

// Starts the authorization-code flow with PKCE (ADR-027). The browser never sees the verifier or a token.
export async function beginAuthorization(request: NextRequest, kind: AuthorizationKind) {
  const config = oidcConfig();
  const publicUrl = new URL(config.publicUrl);
  // Keycloak returns to the registered public origin, so the transaction cookie must be set there.
  if (request.headers.get("host") !== publicUrl.host) {
    return noStore(NextResponse.redirect(new URL(request.nextUrl.pathname + request.nextUrl.search, publicUrl), 303));
  }

  const { url, transaction } = await startAuthorization(kind, request.nextUrl.searchParams.get("returnTo"));
  const response = NextResponse.redirect(url, 303);
  response.cookies.set(OIDC_TRANSACTION_COOKIE, encodeTransaction(transaction), transactionCookieDefaults());
  return noStore(response);
}

export function noStore(response: NextResponse) {
  response.headers.set("Cache-Control", "no-store");
  return response;
}
