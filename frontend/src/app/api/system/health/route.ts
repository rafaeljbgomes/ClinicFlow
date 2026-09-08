import { NextResponse } from "next/server";
import { backendHealthGet, jsonError, readTokenCookie } from "@/lib/server/bff";
import { verifyAccessToken } from "@/lib/server/jwt";
import type { ServiceHealth, SystemHealth } from "@/lib/types";

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
  const verification = await verifyAccessToken(token);
  if (verification.status === "unavailable") return jsonError(503, "Session could not be verified");
  if (verification.status === "invalid") return jsonError(401, "Session could not be verified");
  if (verification.principal.role !== "ADMIN") return jsonError(403, "Administrator access is required");

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
