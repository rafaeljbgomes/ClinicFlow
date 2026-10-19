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
    // Arrange
    vi.stubEnv("GATEWAY_URL", "http://gateway:8080/");

    // Act
    const url = backendUrl(service, path);

    // Assert
    expect(url).toBe(`http://gateway:8080${expected}`);
  });

  it("requires a gateway in production", () => {
    // Arrange
    vi.stubEnv("NODE_ENV", "production");
    vi.stubEnv("GATEWAY_URL", "");

    // Act
    const error = captureError(() => backendUrl("patient", "/patients"));

    // Assert
    expect(error).toMatchObject({ message: expect.stringContaining("GATEWAY_URL is required") });
  });

  it("keeps management traffic separate", () => {
    // Arrange
    vi.stubEnv("GATEWAY_URL", "http://gateway:8080");
    vi.stubEnv("CLINICAL_SERVICE_HEALTH_URL", "http://clinical-service:9000");

    // Act
    const url = backendHealthUrl("clinical", "/actuator/health");

    // Assert
    expect(url).toBe("http://clinical-service:9000/actuator/health");
  });

  it.each(["/actuator/health", "//evil.test/path", "/patients/../actuator", "/patients\\..\\actuator"])("rejects %s", (path) => {
    // Act
    const error = captureError(() => gatewayPath("patient", path));

    // Assert
    expect(error).toBeInstanceOf(Error);
  });
});

function captureError(action: () => unknown) {
  try {
    action();
  } catch (error) {
    return error;
  }
  return undefined;
}
