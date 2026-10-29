import { NextRequest } from "next/server";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const { completeAuthorizationMock, endSessionMock, cookiesMock } = vi.hoisted(() => ({
  completeAuthorizationMock: vi.fn(),
  endSessionMock: vi.fn(),
  cookiesMock: vi.fn(),
}));

vi.mock("next/headers", () => ({ cookies: cookiesMock }));
vi.mock("@/lib/server/oidc", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/server/oidc")>()),
  completeAuthorization: completeAuthorizationMock,
  endSession: endSessionMock,
}));

import { GET as callback } from "@/app/api/auth/callback/route";
import { GET as login } from "@/app/api/auth/login/route";
import { POST as logout } from "@/app/api/auth/logout/route";
import { encodeTransaction } from "@/lib/server/oidc";
import { ISSUER, PUBLIC_URL, stubOidcEnvironment } from "./realm";

const transaction = { state: "state-value-123456", nonce: "nonce-value-123456", verifier: "v".repeat(43), returnTo: null };
const tokens = { accessToken: "access-1", refreshToken: "refresh-1", idToken: "id-1", expiresIn: 300, refreshExpiresIn: 1800 };
const principal = { userId: "00000000-0000-4000-8000-000000000001", email: "admin@demo.clinicflow.local", role: "ADMIN" as const, name: null, expiresAt: 0 };

beforeEach(() => {
  stubOidcEnvironment();
  vi.stubEnv("CLINICFLOW_AUTH_COOKIE_SECURE", "true");
});

afterEach(() => {
  vi.unstubAllEnvs();
  vi.clearAllMocks();
});

function callbackRequest(cookie = `clinicflow_oidc=${encodeTransaction(transaction)}`) {
  return new NextRequest(`${PUBLIC_URL}/api/auth/callback?code=c&state=${transaction.state}&iss=${encodeURIComponent(ISSUER)}`, { headers: { cookie } });
}

describe("GET /api/auth/login", () => {
  it("redirects to Keycloak and keeps the transaction in a callback-only HttpOnly cookie", async () => {
    // Arrange
    const request = new NextRequest(`${PUBLIC_URL}/api/auth/login`, { headers: { host: "localhost:3000" } });

    // Act
    const response = await login(request);

    // Assert
    expect(response.status).toBe(303);
    expect(response.headers.get("location")).toMatch(new RegExp(`^${ISSUER}/protocol/openid-connect/auth\\?`));
    expect(response.cookies.get("clinicflow_oidc")).toMatchObject({ httpOnly: true, secure: true, sameSite: "lax", path: "/api/auth/callback", maxAge: 600 });
    expect(response.headers.get("cache-control")).toBe("no-store");
  });

  it("first moves a request on another host to the public origin", async () => {
    // Arrange
    const request = new NextRequest("http://127.0.0.1:3000/api/auth/login?returnTo=/dashboard", { headers: { host: "127.0.0.1:3000" } });

    // Act
    const response = await login(request);

    // Assert
    expect(response.headers.get("location")).toBe(`${PUBLIC_URL}/api/auth/login?returnTo=/dashboard`);
    expect(response.cookies.get("clinicflow_oidc")).toBeUndefined();
  });
});

describe("GET /api/auth/callback", () => {
  it("passes the callback parameters and the stored transaction on", async () => {
    // Arrange
    completeAuthorizationMock.mockResolvedValue({ status: "signed-in", tokens, principal });

    // Act
    await callback(callbackRequest());

    // Assert
    const [params, stored] = completeAuthorizationMock.mock.calls[0];
    expect(params.get("state")).toBe(transaction.state);
    expect(stored).toEqual(transaction);
  });

  it("sends a signed-in user to the home of their role", async () => {
    // Arrange
    completeAuthorizationMock.mockResolvedValue({ status: "signed-in", tokens, principal });

    // Act
    const response = await callback(callbackRequest());

    // Assert
    expect(response.status).toBe(303);
    expect(response.headers.get("location")).toBe(`${PUBLIC_URL}/dashboard/system`);
  });

  it("keeps every token in HttpOnly cookies and out of the URL", async () => {
    // Arrange
    completeAuthorizationMock.mockResolvedValue({ status: "signed-in", tokens, principal });

    // Act
    const response = await callback(callbackRequest());

    // Assert
    expect(response.cookies.get("clinicflow_access_token")).toMatchObject({ value: "access-1", httpOnly: true, secure: true, sameSite: "lax", maxAge: 300 });
    expect(response.cookies.get("clinicflow_refresh_token")).toMatchObject({ value: "refresh-1", httpOnly: true, secure: true, maxAge: 1800 });
    expect(response.cookies.get("clinicflow_csrf")).toMatchObject({ httpOnly: false, maxAge: 1800 });
    expect(response.headers.getSetCookie().join("\n")).not.toContain("id-1");
    expect(response.headers.get("location")).not.toMatch(/access-1|refresh-1|id-1/);
  });

  it("spends the transaction cookie and hides the code from referrers", async () => {
    // Arrange
    completeAuthorizationMock.mockResolvedValue({ status: "signed-in", tokens, principal });

    // Act
    const response = await callback(callbackRequest());

    // Assert
    expect(response.cookies.get("clinicflow_oidc")).toMatchObject({ value: "", maxAge: 0, path: "/api/auth/callback" });
    expect(response.headers.get("referrer-policy")).toBe("no-referrer");
    expect(response.headers.get("cache-control")).toBe("no-store");
  });

  it("returns to the dashboard page the sign-in started from", async () => {
    // Arrange
    completeAuthorizationMock.mockResolvedValue({ status: "signed-in", tokens, principal });
    const cookie = `clinicflow_oidc=${encodeTransaction({ ...transaction, returnTo: "/dashboard/notifications" })}`;

    // Act
    const response = await callback(callbackRequest(cookie));

    // Assert
    expect(response.headers.get("location")).toBe(`${PUBLIC_URL}/dashboard/notifications`);
  });

  it("sends a failed sign-in back to login without a session", async () => {
    // Arrange
    completeAuthorizationMock.mockResolvedValue({ status: "failed", reason: "failed" });

    // Act
    const response = await callback(callbackRequest());

    // Assert
    expect(response.headers.get("location")).toBe(`${PUBLIC_URL}/login?error=failed`);
    expect(response.cookies.get("clinicflow_access_token")).toBeUndefined();
  });

  it("treats a tampered transaction cookie as no transaction", async () => {
    // Arrange
    completeAuthorizationMock.mockResolvedValue({ status: "failed", reason: "expired" });

    // Act
    await callback(callbackRequest("clinicflow_oidc=tampered"));

    // Assert
    expect(completeAuthorizationMock.mock.calls[0][1]).toBeNull();
  });
});

describe("POST /api/auth/logout", () => {
  function logoutRequest(csrfHeader?: string) {
    return new NextRequest(`${PUBLIC_URL}/api/auth/logout`, {
      method: "POST",
      headers: { cookie: "clinicflow_refresh_token=refresh-1; clinicflow_csrf=csrf-1", ...(csrfHeader ? { "x-csrf-token": csrfHeader } : {}) },
    });
  }

  beforeEach(() => {
    cookiesMock.mockResolvedValue({ get: (name: string) => (name === "clinicflow_csrf" ? { value: "csrf-1" } : undefined) });
    endSessionMock.mockResolvedValue(true);
  });

  it("refuses a request without the CSRF token", async () => {
    // Act
    const response = await logout(logoutRequest());

    // Assert
    expect(response.status).toBe(403);
    expect(endSessionMock).not.toHaveBeenCalled();
  });

  it("ends the Keycloak session with the refresh token", async () => {
    // Act
    await logout(logoutRequest("csrf-1"));

    // Assert
    expect(endSessionMock).toHaveBeenCalledWith("refresh-1");
  });

  it("clears the session cookies and returns the end-session URL", async () => {
    // Act
    const response = await logout(logoutRequest("csrf-1"));

    // Assert
    expect(await response.json()).toEqual({
      logoutUrl: `${ISSUER}/protocol/openid-connect/logout?client_id=clinicflow-bff&post_logout_redirect_uri=${encodeURIComponent(`${PUBLIC_URL}/login`)}`,
    });
    for (const name of ["clinicflow_access_token", "clinicflow_refresh_token", "clinicflow_csrf"]) {
      expect(response.cookies.get(name)?.value).toBe("");
    }
  });
});
