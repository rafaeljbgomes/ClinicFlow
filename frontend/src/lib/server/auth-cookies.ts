export const ACCESS_TOKEN_COOKIE = "clinicflow_access_token";
export const CSRF_COOKIE = "clinicflow_csrf";

export function secureCookieDefaults(maxAge?: number) {
  return {
    httpOnly: true,
    sameSite: "lax" as const,
    secure: process.env.NODE_ENV === "production",
    path: "/",
    ...(maxAge ? { maxAge } : {}),
  };
}

export function csrfCookieDefaults(maxAge?: number) {
  return {
    httpOnly: false,
    sameSite: "lax" as const,
    secure: process.env.NODE_ENV === "production",
    path: "/",
    ...(maxAge ? { maxAge } : {}),
  };
}
