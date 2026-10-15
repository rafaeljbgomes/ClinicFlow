import { afterEach, describe, expect, it, vi } from "vitest";
import { backendUrl, backendHealthUrl, gatewayPath } from "@/lib/server/backend-url";

afterEach(() => vi.unstubAllEnvs());

describe("gateway routing", () => {
  it.each([
    ["auth", "/auth/login", "/api/v1/auth/login"],
    ["auth", "/users/me", "/api/v1/auth/users/me"],
    ["patient", "/patients/id%20value", "/api/v1/patients/id%20value"],
    ["appointment", "/appointments/id/complete", "/api/v1/appointments/id/complete"],
    ["clinical", "/clinical-cases?patientId=a&status=OPEN", "/api/v1/clinical/clinical-cases?patientId=a&status=OPEN"],
    ["clinical", "/patients/id/clinical-history", "/api/v1/clinical/patients/id/clinical-history"],
    ["clinical", "/session-records/id", "/api/v1/clinical/session-records/id"],
    ["clinical", "/practice-profile", "/api/v1/clinical/practice-profile"],
    ["notification", "/notifications", "/api/v1/notifications"],
  ] as const)("maps %s %s", (service, path, expected) => {
    vi.stubEnv("GATEWAY_URL", "http://gateway:8080/");
    expect(backendUrl(service, path)).toBe(`http://gateway:8080${expected}`);
  });
  it("requires a gateway in production", () => {
    vi.stubEnv("NODE_ENV", "production"); vi.stubEnv("GATEWAY_URL", "");
    expect(() => backendUrl("patient", "/patients")).toThrow("GATEWAY_URL is required");
  });
  it("keeps management traffic separate", () => {
    vi.stubEnv("GATEWAY_URL", "http://gateway:8080");
    vi.stubEnv("CLINICAL_SERVICE_HEALTH_URL", "http://clinical-service:9000");
    expect(backendHealthUrl("clinical", "/actuator/health")).toBe("http://clinical-service:9000/actuator/health");
  });
  it.each(["/actuator/health", "//evil.test/path", "/patients/../actuator", "/patients\\..\\actuator"])("rejects %s", (path) => {
    expect(() => gatewayPath("patient", path)).toThrow();
  });
});
