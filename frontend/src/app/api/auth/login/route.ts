import { NextRequest, NextResponse } from "next/server";
import {
  ACCESS_TOKEN_COOKIE,
  CSRF_COOKIE,
  csrfCookieDefaults,
  secureCookieDefaults,
} from "@/lib/server/auth-cookies";
import { proxyRequest } from "@/lib/server/bff";

type LoginBackendResponse = {
  accessToken: string;
  tokenType: "Bearer";
  expiresInSeconds: number;
  user: unknown;
};

export async function POST(request: NextRequest) {
  const response = await proxyRequest(request, "auth", "/auth/login", {
    auth: false,
    csrf: false,
  });

  if (!response.ok) {
    return response;
  }

  const payload = (await response.json()) as LoginBackendResponse;
  const csrfToken = crypto.randomUUID();
  const nextResponse = NextResponse.json({ user: payload.user });
  nextResponse.cookies.set(
    ACCESS_TOKEN_COOKIE,
    payload.accessToken,
    secureCookieDefaults(payload.expiresInSeconds)
  );
  nextResponse.cookies.set(
    CSRF_COOKIE,
    csrfToken,
    csrfCookieDefaults(payload.expiresInSeconds)
  );
  return nextResponse;
}
