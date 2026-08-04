import { NextResponse } from "next/server";
import { backendGet, backendHealthGet, jsonError, readTokenCookie } from "@/lib/server/bff";
import type { ServiceHealth, SystemHealth, UserView } from "@/lib/types";

const services = [
  { service: "auth", label: "Auth Service" },
  { service: "patient", label: "Patient Service" },
  { service: "appointment", label: "Appointment Service" },
  { service: "clinical", label: "Clinical Service" },
  { service: "notification", label: "Notification Service" },
] as const;

export async function GET() {
  const token = await readTokenCookie();
  if (!token) return jsonError(401, "Authentication is required");
  const identity = await backendGet("auth", "/users/me", token);
  const user = identity.data as Partial<UserView> | null;
  if (!identity.ok) return jsonError(401, "Session could not be verified");
  if (user?.role !== "ADMIN") return jsonError(403, "Administrator access is required");

  const results = await Promise.all(
    services.map(async ({ service, label }) => {
      const result = await backendHealthGet(service, "/actuator/health");
      const rawStatus =
        result.ok && result.data && typeof result.data === "object"
          ? ((result.data as { status?: string }).status ?? "UNKNOWN")
          : "DOWN";
      const status: ServiceHealth["status"] = rawStatus === "UP" ? "UP" : "DOWN";
      return {
        service,
        label,
        status,
        latencyMs: result.latencyMs,
      };
    })
  );

  return NextResponse.json<SystemHealth>({ services: results });
}
