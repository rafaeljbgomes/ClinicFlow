import "server-only";

import { redirect } from "next/navigation";
import { backendGet, readTokenCookie } from "@/lib/server/bff";
import type { Role, UserView } from "@/lib/types";

export async function getCurrentUser(): Promise<UserView | null> {
  const token = await readTokenCookie();
  if (!token) return null;

  const response = await backendGet("auth", "/users/me", token);
  if (!response.ok || !isUserView(response.data)) return null;
  return response.data;
}

export async function requireCurrentUser() {
  const user = await getCurrentUser();
  if (!user) redirect("/login");
  return user;
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

function isUserView(value: unknown): value is UserView {
  if (!value || typeof value !== "object") return false;
  const user = value as Partial<UserView>;
  return (
    typeof user.id === "string" &&
    typeof user.email === "string" &&
    typeof user.fullName === "string" &&
    ["ADMIN", "PSYCHOLOGIST", "PATIENT"].includes(user.role ?? "")
  );
}
