import type { NextResponse } from "next/server";

export const ACCESS_TOKEN_COOKIE = "clinicflow_access_token";
export const REFRESH_TOKEN_COOKIE = "clinicflow_refresh_token";
export const CSRF_COOKIE = "clinicflow_csrf";
// State, nonce and PKCE verifier of one sign-in, readable only by the callback route.
export const OIDC_TRANSACTION_COOKIE = "clinicflow_oidc";
export const OIDC_TRANSACTION_PATH = "/api/auth/callback";
const OIDC_TRANSACTION_MAX_AGE = 600;

type ResponseCookies = NextResponse["cookies"];

export type SessionTokens = {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  refreshExpiresIn: number;
};

function shouldUseSecureCookies() {
  const configuredValue = process.env.CLINICFLOW_AUTH_COOKIE_SECURE;
  if (configuredValue === "true") return true;
  if (configuredValue === "false") return false;
  return process.env.NODE_ENV === "production";
}

export function secureCookieDefaults(maxAge?: number) {
  return {
    httpOnly: true,
    sameSite: "lax" as const,
    secure: shouldUseSecureCookies(),
    path: "/",
    ...(maxAge ? { maxAge } : {}),
  };
}

export function csrfCookieDefaults(maxAge?: number) {
  return {
    httpOnly: false,
    sameSite: "lax" as const,
    secure: shouldUseSecureCookies(),
    path: "/",
    ...(maxAge ? { maxAge } : {}),
  };
}

// Lax lets the cookie ride the top-level redirect back from Keycloak.
export function transactionCookieDefaults() {
  return { ...secureCookieDefaults(OIDC_TRANSACTION_MAX_AGE), path: OIDC_TRANSACTION_PATH };
}

// The access cookie lives as long as the access token; the refresh and CSRF cookies as long as the
// Keycloak session, so a refresh can extend the session without a new sign-in.
export function writeSessionCookies(cookies: ResponseCookies, tokens: SessionTokens, csrfToken: string) {
  cookies.set(ACCESS_TOKEN_COOKIE, tokens.accessToken, secureCookieDefaults(tokens.expiresIn));
  cookies.set(REFRESH_TOKEN_COOKIE, tokens.refreshToken, secureCookieDefaults(tokens.refreshExpiresIn));
  cookies.set(CSRF_COOKIE, csrfToken, csrfCookieDefaults(tokens.refreshExpiresIn));
}

export function clearSessionCookies(cookies: ResponseCookies) {
  for (const name of [ACCESS_TOKEN_COOKIE, REFRESH_TOKEN_COOKIE, CSRF_COOKIE]) cookies.delete(name);
}

export function clearTransactionCookie(cookies: ResponseCookies) {
  cookies.set(OIDC_TRANSACTION_COOKIE, "", { ...transactionCookieDefaults(), maxAge: 0 });
}
