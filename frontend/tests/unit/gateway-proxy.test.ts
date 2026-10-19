import { NextRequest } from "next/server";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const { cookiesMock } = vi.hoisted(() => ({ cookiesMock: vi.fn() }));
vi.mock("next/headers", () => ({ cookies: cookiesMock }));

import { proxyRequest } from "@/lib/server/bff";

describe("gateway error and forwarding behavior", () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    vi.stubEnv("GATEWAY_URL", "http://gateway:8080");
    vi.stubGlobal("fetch", fetchMock);
    cookiesMock.mockResolvedValue({ get: () => ({ value: "token" }) });
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
    vi.clearAllMocks();
  });

  describe("when the gateway rate-limits a request", () => {
    beforeEach(() => {
      fetchMock.mockResolvedValue(
        new Response("Too Many Requests", { status: 429, headers: { "retry-after": "2" } })
      );
    });

    it("keeps the 429 status", async () => {
      // Arrange
      const request = new NextRequest("http://ui/api/patients");

      // Act
      const response = await proxyRequest(request, "patient", "/patients");

      // Assert
      expect(response.status).toBe(429);
    });

    it("forwards Retry-After", async () => {
      // Arrange
      const request = new NextRequest("http://ui/api/patients");

      // Act
      const response = await proxyRequest(request, "patient", "/patients");

      // Assert
      expect(response.headers.get("retry-after")).toBe("2");
    });

    it("normalizes the plain-text body into the BFF error shape", async () => {
      // Arrange
      const request = new NextRequest("http://ui/api/patients");

      // Act
      const response = await proxyRequest(request, "patient", "/patients");

      // Assert
      expect(await response.json()).toEqual({ status: 429, message: "Too many requests. Please try again shortly." });
    });

    it("calls the gateway route exactly once", async () => {
      // Arrange
      const request = new NextRequest("http://ui/api/patients");

      // Act
      await proxyRequest(request, "patient", "/patients");

      // Assert
      expect(fetchMock).toHaveBeenCalledTimes(1);
      expect(fetchMock.mock.calls[0][0]).toBe("http://gateway:8080/api/v1/patients");
    });
  });

  describe("when the gateway is unreachable during a mutation", () => {
    beforeEach(() => {
      fetchMock.mockRejectedValue(new Error("gateway unavailable"));
    });

    it("fails with 503", async () => {
      // Arrange
      const request = syntheticPatientCreation();

      // Act
      const response = await proxyRequest(request, "patient", "/patients", { csrf: false });

      // Assert
      expect(response.status).toBe(503);
    });

    it("does not fall back to a direct service or retry the mutation", async () => {
      // Arrange
      const request = syntheticPatientCreation();

      // Act
      await proxyRequest(request, "patient", "/patients", { csrf: false });

      // Assert
      expect(fetchMock).toHaveBeenCalledTimes(1);
      expect(fetchMock.mock.calls[0][0]).toBe("http://gateway:8080/api/v1/patients");
    });

    it("forwards the session token as a bearer credential", async () => {
      // Arrange
      const request = syntheticPatientCreation();

      // Act
      await proxyRequest(request, "patient", "/patients", { csrf: false });

      // Assert
      expect(fetchMock.mock.calls[0][1].headers.Authorization).toBe("Bearer token");
    });

    it("forwards the caller correlation id", async () => {
      // Arrange
      const request = syntheticPatientCreation();

      // Act
      await proxyRequest(request, "patient", "/patients", { csrf: false });

      // Assert
      expect(fetchMock.mock.calls[0][1].headers["X-Correlation-Id"]).toBe("test-correlation");
    });

    it("forwards the request body unchanged", async () => {
      // Arrange
      const request = syntheticPatientCreation();

      // Act
      await proxyRequest(request, "patient", "/patients", { csrf: false });

      // Assert
      expect(fetchMock.mock.calls[0][1].body).toBe('{"name":"synthetic"}');
    });
  });
});

function syntheticPatientCreation() {
  return new NextRequest("http://ui/api/patients", {
    method: "POST",
    body: '{"name":"synthetic"}',
    headers: { "x-correlation-id": "test-correlation" },
  });
}
