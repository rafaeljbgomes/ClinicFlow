import type { NextRequest } from "next/server";
import { NextResponse } from "next/server";
import { decodeJwt } from "jose";
import {
  ACCESS_TOKEN_COOKIE,
  clearSessionCookies,
  CSRF_COOKIE,
  REFRESH_TOKEN_COOKIE,
  writeSessionCookies,
} from "@/lib/server/auth-cookies";
import { refreshTokens } from "@/lib/server/oidc";

// Refresh this long before the 5-minute access token expires, so a request never reaches a service
// with a token that lapses on the way.
const REFRESH_MARGIN_SECONDS = 30;
// The sign-in and sign-out routes manage the session cookies themselves.
const SESSION_ROUTES = /^\/api\/auth\/(?:login|register|callback|logout)(?:\/|$)/;

export async function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl;
  if (SESSION_ROUTES.test(pathname)) return NextResponse.next();

  const accessToken = request.cookies.get(ACCESS_TOKEN_COOKIE)?.value;
  const refreshToken = request.cookies.get(REFRESH_TOKEN_COOKIE)?.value;
  const isPage = pathname.startsWith("/dashboard");

  if (!refreshToken || !needsRefresh(accessToken, Date.now() / 1000)) {
    if (isPage && !accessToken) return NextResponse.redirect(new URL("/login", request.url));
    return NextResponse.next();
  }

  const refreshed = await refreshTokens(refreshToken);
  if (refreshed.status === "issued") {
    const csrfToken = request.cookies.get(CSRF_COOKIE)?.value ?? crypto.randomUUID();
    request.cookies.set(ACCESS_TOKEN_COOKIE, refreshed.tokens.accessToken);
    request.cookies.set(REFRESH_TOKEN_COOKIE, refreshed.tokens.refreshToken);
    request.cookies.set(CSRF_COOKIE, csrfToken);
    const response = NextResponse.next({ request: { headers: forwardedHeaders(request) } });
    writeSessionCookies(response.cookies, refreshed.tokens, csrfToken);
    return response;
  }

  if (refreshed.status === "rejected") {
    // The Keycloak session is over (expired, revoked or logged out elsewhere).
    request.cookies.delete(ACCESS_TOKEN_COOKIE);
    request.cookies.delete(REFRESH_TOKEN_COOKIE);
    const response = isPage
      ? NextResponse.redirect(new URL("/login", request.url))
      : NextResponse.next({ request: { headers: forwardedHeaders(request) } });
    clearSessionCookies(response.cookies);
    return response;
  }

  // Keycloak is unreachable: an unexpired access token still works, and the routes report the outage.
  if (isPage && !accessToken) return NextResponse.redirect(new URL("/login", request.url));
  return NextResponse.next();
}

export function needsRefresh(accessToken: string | undefined, nowSeconds: number) {
  if (!accessToken) return true;
  try {
    const { exp } = decodeJwt(accessToken);
    return typeof exp !== "number" || exp - nowSeconds <= REFRESH_MARGIN_SECONDS;
  } catch {
    return true;
  }
}

function forwardedHeaders(request: NextRequest) {
  const headers = new Headers(request.headers);
  headers.set("cookie", request.cookies.toString());
  return headers;
}

export const config = {
  matcher: ["/dashboard/:path*", "/api/:path*"],
};
