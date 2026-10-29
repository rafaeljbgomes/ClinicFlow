import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
import { NextRequest } from "next/server";

const { refreshTokensMock } = vi.hoisted(() => ({ refreshTokensMock: vi.fn() }));
vi.mock("@/lib/server/oidc", () => ({ refreshTokens: refreshTokensMock }));

import { needsRefresh, proxy } from "@/proxy";
import { accessToken, testRealm, type TestRealm } from "./realm";

let realm: TestRealm;
const rotated = { accessToken: "rotated-access", refreshToken: "rotated-refresh", idToken: null, expiresIn: 300, refreshExpiresIn: 1800 };

beforeAll(async () => {
  realm = await testRealm();
});

afterEach(() => vi.clearAllMocks());

function request(path: string, cookies: Record<string, string> = {}) {
  const cookie = Object.entries(cookies).map(([name, value]) => `${name}=${value}`).join("; ");
  return new NextRequest(`http://localhost${path}`, { headers: cookie ? { cookie } : {} });
}

function forwardedCookie(response: Response) {
  return response.headers.get("x-middleware-request-cookie") ?? "";
}

describe("session proxy", () => {
  it("redirects a protected page to login when there is no session", async () => {
    // Act
    const response = await proxy(request("/dashboard"));

    // Assert
    expect(response.status).toBe(307);
    expect(response.headers.get("location")).toBe("http://localhost/login");
  });

  it("lets a fresh access token through without refreshing", async () => {
    // Arrange
    const token = await accessToken(realm);

    // Act
    const response = await proxy(request("/dashboard", { clinicflow_access_token: token, clinicflow_refresh_token: "r1" }));

    // Assert
    expect(response.headers.get("x-middleware-next")).toBe("1");
    expect(refreshTokensMock).not.toHaveBeenCalled();
  });

  it("never touches the sign-in and sign-out routes", async () => {
    // Act
    const response = await proxy(request("/api/auth/logout", { clinicflow_refresh_token: "r1" }));

    // Assert
    expect(response.headers.get("x-middleware-next")).toBe("1");
    expect(refreshTokensMock).not.toHaveBeenCalled();
  });

  describe("when the access token is about to expire", () => {
    let expiring: string;

    beforeEach(async () => {
      expiring = await accessToken(realm, { expiresIn: "10s" });
    });

    it("refreshes with the refresh-token cookie", async () => {
      // Arrange
      refreshTokensMock.mockResolvedValue({ status: "issued", tokens: rotated });

      // Act
      await proxy(request("/api/patients", { clinicflow_access_token: expiring, clinicflow_refresh_token: "r1", clinicflow_csrf: "csrf-1" }));

      // Assert
      expect(refreshTokensMock).toHaveBeenCalledWith("r1");
    });

    it("hands the rotated tokens to the same request", async () => {
      // Arrange
      refreshTokensMock.mockResolvedValue({ status: "issued", tokens: rotated });

      // Act
      const response = await proxy(request("/api/patients", { clinicflow_access_token: expiring, clinicflow_refresh_token: "r1", clinicflow_csrf: "csrf-1" }));

      // Assert
      expect(response.headers.get("x-middleware-override-headers")).toContain("cookie");
      expect(forwardedCookie(response)).toContain("clinicflow_access_token=rotated-access");
    });

    it("stores the rotated tokens and keeps the CSRF token", async () => {
      // Arrange
      refreshTokensMock.mockResolvedValue({ status: "issued", tokens: rotated });

      // Act
      const response = await proxy(request("/api/patients", { clinicflow_access_token: expiring, clinicflow_refresh_token: "r1", clinicflow_csrf: "csrf-1" }));

      // Assert
      expect(response.cookies.get("clinicflow_access_token")).toMatchObject({ value: "rotated-access", httpOnly: true, maxAge: 300 });
      expect(response.cookies.get("clinicflow_refresh_token")).toMatchObject({ value: "rotated-refresh", httpOnly: true, maxAge: 1800 });
      expect(response.cookies.get("clinicflow_csrf")).toMatchObject({ value: "csrf-1", httpOnly: false });
    });

    it("refreshes a page whose access cookie has already lapsed", async () => {
      // Arrange
      refreshTokensMock.mockResolvedValue({ status: "issued", tokens: rotated });

      // Act
      const response = await proxy(request("/dashboard", { clinicflow_refresh_token: "r1" }));

      // Assert
      expect(response.headers.get("location")).toBeNull();
      expect(response.cookies.get("clinicflow_access_token")?.value).toBe("rotated-access");
    });

    it("sends a page to login when Keycloak refuses the refresh", async () => {
      // Arrange
      refreshTokensMock.mockResolvedValue({ status: "rejected" });

      // Act
      const response = await proxy(request("/dashboard", { clinicflow_access_token: expiring, clinicflow_refresh_token: "r1" }));

      // Assert
      expect(response.headers.get("location")).toBe("http://localhost/login");
      expect(response.cookies.get("clinicflow_refresh_token")?.value).toBe("");
    });

    it("strips the ended session from an API request so its route answers 401", async () => {
      // Arrange
      refreshTokensMock.mockResolvedValue({ status: "rejected" });

      // Act
      const response = await proxy(request("/api/patients", { clinicflow_access_token: expiring, clinicflow_refresh_token: "r1", clinicflow_csrf: "c" }));

      // Assert
      expect(forwardedCookie(response)).not.toContain("clinicflow_access_token");
      expect(response.cookies.get("clinicflow_access_token")?.value).toBe("");
    });

    it("keeps the unexpired token when Keycloak is unreachable", async () => {
      // Arrange
      refreshTokensMock.mockResolvedValue({ status: "unavailable" });

      // Act
      const response = await proxy(request("/dashboard", { clinicflow_access_token: expiring, clinicflow_refresh_token: "r1" }));

      // Assert
      expect(response.headers.get("x-middleware-next")).toBe("1");
      expect(response.cookies.get("clinicflow_access_token")).toBeUndefined();
    });
  });
});

describe("needsRefresh", () => {
  it.each([
    ["no token", undefined, true],
    ["an unreadable token", "not-a-jwt", true],
  ])("refreshes %s", (_case, token, expected) => {
    // Act
    const result = needsRefresh(token, 1_000);

    // Assert
    expect(result).toBe(expected);
  });

  it("refreshes within 30 seconds of expiry, not before", async () => {
    // Arrange
    const token = await accessToken(realm, { expiresIn: "60s" });
    const now = Date.now() / 1000;

    // Act
    const early = needsRefresh(token, now);
    const late = needsRefresh(token, now + 31);

    // Assert
    expect([early, late]).toEqual([false, true]);
  });
});
