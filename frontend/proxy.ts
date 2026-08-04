import type { NextRequest } from "next/server";
import { NextResponse } from "next/server";
import { ACCESS_TOKEN_COOKIE } from "@/lib/server/auth-cookies";

export function proxy(request: NextRequest) {
  const hasToken = request.cookies.has(ACCESS_TOKEN_COOKIE);
  const { pathname } = request.nextUrl;

  if (pathname.startsWith("/dashboard") && !hasToken) {
    return NextResponse.redirect(new URL("/login", request.url));
  }

  if ((pathname === "/login" || pathname === "/register") && hasToken) {
    return NextResponse.redirect(new URL("/dashboard/entry", request.url));
  }

  return NextResponse.next();
}

export const config = {
  matcher: ["/dashboard/:path*", "/login", "/register"],
};
