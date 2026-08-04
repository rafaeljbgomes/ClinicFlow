import { NextRequest, NextResponse } from "next/server";
import { ACCESS_TOKEN_COOKIE, CSRF_COOKIE } from "@/lib/server/auth-cookies";
import { validateCsrf } from "@/lib/server/bff";

export async function POST(request: NextRequest) {
  const csrfError = await validateCsrf(request);
  if (csrfError) return csrfError;
  const response = NextResponse.json({ ok: true });
  response.cookies.delete(ACCESS_TOKEN_COOKIE);
  response.cookies.delete(CSRF_COOKIE);
  return response;
}
