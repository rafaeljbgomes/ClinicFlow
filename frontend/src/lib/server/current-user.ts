import "server-only";

import { cache } from "react";
import { redirect } from "next/navigation";
import { classifySession, type SessionResult } from "@/lib/session-result";
import { backendGet, readTokenCookie } from "@/lib/server/bff";
import type { Role } from "@/lib/types";

export const getCurrentSession = cache(async (): Promise<SessionResult> => {
  const token = await readTokenCookie();
  if (!token) return classifySession(token);

  const response = await backendGet("auth", "/users/me", token);
  return classifySession(token, response);
});

export async function requireCurrentUser() {
  const session = await getCurrentSession();
  if (session.status === "anonymous") redirect("/login");
  if (session.status !== "authenticated") {
    throw new SessionResolutionError(session.status);
  }
  return session.user;
}

export async function requireDashboardRole(roles: Role[]) {
  const user = await requireCurrentUser();
  if (!roles.includes(user.role)) redirect(homeForRole(user.role));
  return user;
}

export function homeForRole(role: Role) {
  if (role === "ADMIN") return "/dashboard/system";
  if (role === "PATIENT") return "/dashboard/access-limited";
  return "/dashboard";
}

export class SessionResolutionError extends Error {
  constructor(readonly reason: "unavailable" | "invalid-response") {
    super("The session could not be verified.");
    this.name = "SessionResolutionError";
  }
}
