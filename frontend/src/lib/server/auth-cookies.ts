export const ACCESS_TOKEN_COOKIE = "clinicflow_access_token";
export const CSRF_COOKIE = "clinicflow_csrf";

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
