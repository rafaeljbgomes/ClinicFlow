import { NextRequest, NextResponse } from "next/server";
import { clearSessionCookies, REFRESH_TOKEN_COOKIE } from "@/lib/server/auth-cookies";
import { noStore } from "@/lib/server/auth-routes";
import { validateCsrf } from "@/lib/server/bff";
import { endSession, endSessionUrl } from "@/lib/server/oidc";

// Ends the Keycloak session on the back channel, clears the cookies, and returns the end-session URL
// that clears Keycloak's own browser cookies (RP-initiated logout).
export async function POST(request: NextRequest) {
  const csrfError = await validateCsrf(request);
  if (csrfError) return csrfError;
  const refreshToken = request.cookies.get(REFRESH_TOKEN_COOKIE)?.value;
  if (refreshToken) await endSession(refreshToken);
  const response = NextResponse.json({ logoutUrl: endSessionUrl() });
  clearSessionCookies(response.cookies);
  return noStore(response);
}
