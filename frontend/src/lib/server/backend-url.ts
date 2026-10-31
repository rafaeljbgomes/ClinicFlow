export type ServiceName = "patient" | "appointment" | "clinical" | "notification";

const directUrls: Record<ServiceName, string> = {
  patient: process.env.PATIENT_SERVICE_URL ?? "http://localhost:8082",
  appointment: process.env.APPOINTMENT_SERVICE_URL ?? "http://localhost:8083",
  clinical: process.env.CLINICAL_SERVICE_URL ?? "http://localhost:8085",
  notification: process.env.NOTIFICATION_SERVICE_URL ?? "http://localhost:8084",
};

export function gatewayPath(service: ServiceName, path: string): string {
  const pathname = path.split("?")[0];
  if (!path.startsWith("/") || path.startsWith("//") || path.includes("\\") || /(?:^|\/)\.\.(?:\/|$)/.test(pathname)) {
    throw new Error("Backend path must be a relative API path");
  }
  const roots: Record<ServiceName, string[]> = {
    patient: ["/patients"], appointment: ["/appointments"],
    clinical: ["/clinical-cases", "/session-records", "/practice-profile", "/patients"],
    notification: ["/notifications"],
  };
  if (!roots[service].some((root) => pathname === root || pathname.startsWith(`${root}/`))) {
    throw new Error("Path is outside the service API contract");
  }
  if (service === "clinical") return `/api/v1/clinical${path}`;
  return `/api/v1${path}`;
}

export function backendUrl(service: ServiceName, path: string): string {
  const gateway = process.env.GATEWAY_URL?.trim();
  if (gateway) {
    const url = new URL(gateway);
    if (!["http:", "https:"].includes(url.protocol) || url.username || url.password || url.search || url.hash || url.pathname !== "/") {
      throw new Error("GATEWAY_URL must be an HTTP(S) origin");
    }
    return `${url.origin}${gatewayPath(service, path)}`;
  }
  if (process.env.NODE_ENV === "production") throw new Error("GATEWAY_URL is required in deployed environments");
  return `${directUrls[service]}${path}`;
}

export function backendHealthUrl(service: ServiceName, path: string): string {
  const key = `${service.toUpperCase()}_SERVICE_HEALTH_URL`;
  return `${process.env[key] ?? directUrls[service]}${path}`;
}
