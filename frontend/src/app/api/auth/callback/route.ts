import { NextRequest, NextResponse } from "next/server";
import { clearTransactionCookie, OIDC_TRANSACTION_COOKIE, writeSessionCookies } from "@/lib/server/auth-cookies";
import { noStore } from "@/lib/server/auth-routes";
import { homeForRole } from "@/lib/server/current-user";
import { completeAuthorization, decodeTransaction } from "@/lib/server/oidc";
import { oidcConfig } from "@/lib/server/oidc-config";

// Keycloak's redirect target. The code, state and iss in the URL are spent here; the browser leaves
// with a redirect, so they do not stay in the address bar or reach another page's Referer.
export async function GET(request: NextRequest) {
  const transaction = decodeTransaction(request.cookies.get(OIDC_TRANSACTION_COOKIE)?.value);
  const outcome = await completeAuthorization(request.nextUrl.searchParams, transaction);
  const target =
    outcome.status === "signed-in"
      ? (transaction?.returnTo ?? homeForRole(outcome.principal.role))
      : `/login?error=${outcome.reason}`;

  const response = NextResponse.redirect(new URL(target, oidcConfig().publicUrl), 303);
  clearTransactionCookie(response.cookies);
  if (outcome.status === "signed-in") writeSessionCookies(response.cookies, outcome.tokens, crypto.randomUUID());
  response.headers.set("Referrer-Policy", "no-referrer");
  return noStore(response);
}
