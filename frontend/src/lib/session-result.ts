import type { UserView } from "@/lib/types";

export type SessionResult =
  | { status: "authenticated"; user: UserView }
  | { status: "anonymous" }
  | { status: "unavailable" }
  | { status: "invalid-response" };

export type SessionBackendResponse = {
  ok: boolean;
  status: number;
  data: unknown;
  invalidJson?: boolean;
  unavailable?: boolean;
};

export function classifySession(
  token: string | undefined,
  response?: SessionBackendResponse
): SessionResult {
  if (!token) return { status: "anonymous" };
  if (!response || response.unavailable) return { status: "unavailable" };
  if (response.status === 401) return { status: "anonymous" };
  if (response.status >= 500) return { status: "unavailable" };
  if (!response.ok || response.invalidJson || !isUserView(response.data)) {
    return { status: "invalid-response" };
  }
  return { status: "authenticated", user: response.data };
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
