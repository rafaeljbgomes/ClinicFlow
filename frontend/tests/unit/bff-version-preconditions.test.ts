import { NextRequest } from "next/server";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const { cookiesMock } = vi.hoisted(() => ({ cookiesMock: vi.fn() }));
vi.mock("next/headers", () => ({ cookies: cookiesMock }));

import { proxyRequest } from "@/lib/server/bff";

function write(headers: Record<string, string>) {
  return new NextRequest("http://ui/api/patients/p-1", {
    method: "PUT",
    headers: { "content-type": "application/json", ...headers },
    body: JSON.stringify({ fullName: "Patient" }),
  });
}

function forwardedHeaders(fetchMock: ReturnType<typeof vi.fn>) {
  return fetchMock.mock.calls[0][1].headers as Record<string, string>;
}

describe("version preconditions through the BFF", () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    vi.stubGlobal("fetch", fetchMock);
    cookiesMock.mockResolvedValue({ get: () => ({ value: "token" }) });
    fetchMock.mockResolvedValue(
      new Response(JSON.stringify({ id: "p-1", version: 4 }), { status: 200, headers: { etag: '"4"' } })
    );
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.clearAllMocks();
  });

  it("forwards If-Match on a write", async () => {
    // Arrange
    const request = write({ "if-match": '"3"' });

    // Act
    await proxyRequest(request, "patient", "/patients/p-1", { csrf: false });

    // Assert
    expect(forwardedHeaders(fetchMock)["If-Match"]).toBe('"3"');
  });

  it("forwards If-None-Match: * on a write that creates the record", async () => {
    // Arrange
    const request = write({ "if-none-match": "*" });

    // Act
    await proxyRequest(request, "clinical", "/practice-profile", { csrf: false });

    // Assert
    expect(forwardedHeaders(fetchMock)["If-None-Match"]).toBe("*");
  });

  it("forwards the entity tag of the response", async () => {
    // Arrange
    const request = write({ "if-match": '"3"' });

    // Act
    const response = await proxyRequest(request, "patient", "/patients/p-1", { csrf: false });

    // Assert
    expect(response.headers.get("etag")).toBe('"4"');
  });

  describe.each([
    ["a wildcard If-Match", { "if-match": "*" }],
    ["a weak tag", { "if-match": 'W/"3"' }],
    ["an unquoted version", { "if-match": "3" }],
    ["several tags", { "if-match": '"3", "4"' }],
    ["an If-None-Match tag", { "if-none-match": '"3"' }],
  ])("with %s", (_label, headers) => {
    it("rejects the write as an invalid precondition", async () => {
      // Arrange
      const request = write(headers);

      // Act
      const response = await proxyRequest(request, "patient", "/patients/p-1", { csrf: false });

      // Assert
      expect(response.status).toBe(400);
      expect(await response.json()).toMatchObject({ code: "invalid_precondition" });
    });

    it("sends no downstream request", async () => {
      // Arrange
      const request = write(headers);

      // Act
      await proxyRequest(request, "patient", "/patients/p-1", { csrf: false });

      // Assert
      expect(fetchMock).not.toHaveBeenCalled();
    });
  });

  // Browsers add If-None-Match to GET revalidations on their own; reads must neither fail nor forward it.
  it("ignores If-None-Match on a read", async () => {
    // Arrange
    const request = new NextRequest("http://ui/api/patients/p-1", { headers: { "if-none-match": '"3"' } });

    // Act
    const response = await proxyRequest(request, "patient", "/patients/p-1");

    // Assert
    expect(response.status).toBe(200);
    expect(forwardedHeaders(fetchMock)["If-None-Match"]).toBeUndefined();
  });
});
