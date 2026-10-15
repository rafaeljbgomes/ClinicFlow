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
  afterEach(() => { vi.unstubAllEnvs(); vi.unstubAllGlobals(); vi.clearAllMocks(); });
  it("normalizes a plain gateway 429 and forwards Retry-After", async () => {
    fetchMock.mockResolvedValue(new Response("Too Many Requests", { status: 429, headers: { "retry-after": "2" } }));
    const response = await proxyRequest(new NextRequest("http://ui/api/patients"), "patient", "/patients");
    expect(response.status).toBe(429);
    expect(response.headers.get("retry-after")).toBe("2");
    expect(await response.json()).toEqual({ status:429, message:"Too many requests. Please try again shortly." });
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0][0]).toBe("http://gateway:8080/api/v1/patients");
  });
  it("fails without direct fallback or mutation retries", async () => {
    fetchMock.mockRejectedValue(new Error("gateway unavailable"));
    const request = new NextRequest("http://ui/api/patients", { method:"POST", body:'{"name":"synthetic"}', headers:{"x-correlation-id":"test-correlation"} });
    const response = await proxyRequest(request, "patient", "/patients", {csrf:false});
    expect(response.status).toBe(503);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0][0]).toBe("http://gateway:8080/api/v1/patients");
    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBe("Bearer token");
    expect(fetchMock.mock.calls[0][1].headers["X-Correlation-Id"]).toBe("test-correlation");
    expect(fetchMock.mock.calls[0][1].body).toBe('{"name":"synthetic"}');
  });
});
