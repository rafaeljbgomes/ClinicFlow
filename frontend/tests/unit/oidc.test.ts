import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
import {
  completeAuthorization,
  decodeTransaction,
  encodeTransaction,
  endSession,
  endSessionUrl,
  refreshTokens,
  safeReturnTo,
  startAuthorization,
  type AuthorizationTransaction,
} from "@/lib/server/oidc";
import { accessToken, CLIENT_SECRET, idToken, INTERNAL_ISSUER, ISSUER, PUBLIC_URL, stubOidcEnvironment, testRealm, type TestRealm } from "./realm";

let realm: TestRealm;
const fetchMock = vi.fn();

beforeAll(async () => {
  realm = await testRealm();
});

beforeEach(() => {
  stubOidcEnvironment();
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  fetchMock.mockReset();
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});

describe("startAuthorization", () => {
  it("sends the browser to the public authorization endpoint with PKCE S256", async () => {
    // Act
    const { url, transaction } = await startAuthorization("login", null);

    // Assert
    const request = new URL(url);
    const challenge = Buffer.from(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(transaction.verifier))).toString("base64url");
    expect(request.origin + request.pathname).toBe(`${ISSUER}/protocol/openid-connect/auth`);
    expect(Object.fromEntries(request.searchParams)).toEqual({
      response_type: "code",
      client_id: "clinicflow-bff",
      redirect_uri: `${PUBLIC_URL}/api/auth/callback`,
      scope: "openid",
      state: transaction.state,
      nonce: transaction.nonce,
      code_challenge: challenge,
      code_challenge_method: "S256",
    });
  });

  it("sends registrations to Keycloak's registration page", async () => {
    // Act
    const { url } = await startAuthorization("register", null);

    // Assert
    expect(new URL(url).pathname).toBe("/realms/clinicflow/protocol/openid-connect/registrations");
  });

  it("uses a fresh state, nonce and verifier for every sign-in", async () => {
    // Act
    const first = await startAuthorization("login", null);
    const second = await startAuthorization("login", null);

    // Assert
    expect(new Set([first.transaction.state, second.transaction.state, first.transaction.nonce, second.transaction.nonce]).size).toBe(4);
    expect(first.transaction.verifier).not.toBe(second.transaction.verifier);
    expect(first.transaction.verifier.length).toBeGreaterThanOrEqual(43);
  });

  it("never puts the verifier in the browser's URL", async () => {
    // Act
    const { url, transaction } = await startAuthorization("login", null);

    // Assert
    expect(url).not.toContain(transaction.verifier);
  });
});

describe("safeReturnTo", () => {
  it.each(["/dashboard", "/dashboard/patients?status=ACTIVE"])("keeps the dashboard path %s", (path) => {
    // Act
    const result = safeReturnTo(path);

    // Assert
    expect(result).toBe(path);
  });

  it.each(["https://evil.example/dashboard", "//evil.example/dashboard", "/dashboard//evil.example", "/\\evil.example", "/login", "/api/auth/logout", null])(
    "drops %s",
    (path) => {
      // Act
      const result = safeReturnTo(path);

      // Assert
      expect(result).toBeNull();
    }
  );
});

describe("transaction cookie", () => {
  it("round-trips a transaction", async () => {
    // Arrange
    const { transaction } = await startAuthorization("login", "/dashboard/clinical");

    // Act
    const decoded = decodeTransaction(encodeTransaction(transaction));

    // Assert
    expect(decoded).toEqual(transaction);
  });

  it.each(["", "not-base64-json", Buffer.from(JSON.stringify({ state: "short" })).toString("base64url")])("rejects the malformed value %j", (value) => {
    // Act
    const decoded = decodeTransaction(value);

    // Assert
    expect(decoded).toBeNull();
  });
});

describe("completeAuthorization", () => {
  async function signInTransaction(): Promise<AuthorizationTransaction> {
    return (await startAuthorization("login", null)).transaction;
  }

  function callback(transaction: AuthorizationTransaction, overrides: Record<string, string | null> = {}) {
    const params = new URLSearchParams({ code: "code-1", state: transaction.state, iss: ISSUER, session_state: "s" });
    for (const [name, value] of Object.entries(overrides)) {
      if (value === null) params.delete(name);
      else params.set(name, value);
    }
    return params;
  }

  async function tokenResponse(nonce: string, idTokenOptions = {}) {
    return new Response(
      JSON.stringify({
        access_token: await accessToken(realm),
        refresh_token: "refresh-1",
        id_token: await idToken(realm, nonce, idTokenOptions),
        expires_in: 300,
        refresh_expires_in: 1800,
        token_type: "Bearer",
      }),
      { status: 200, headers: { "Content-Type": "application/json" } }
    );
  }

  it("exchanges the code on the private network with the verifier and client secret", async () => {
    // Arrange
    const transaction = await signInTransaction();
    fetchMock.mockResolvedValue(await tokenResponse(transaction.nonce));

    // Act
    await completeAuthorization(callback(transaction), transaction, realm.keys);

    // Assert
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe(`${INTERNAL_ISSUER}/protocol/openid-connect/token`);
    expect(Object.fromEntries(init.body as URLSearchParams)).toEqual({
      grant_type: "authorization_code",
      code: "code-1",
      redirect_uri: `${PUBLIC_URL}/api/auth/callback`,
      code_verifier: transaction.verifier,
    });
    expect(init.headers.Authorization).toBe(`Basic ${Buffer.from(`clinicflow-bff:${CLIENT_SECRET}`).toString("base64")}`);
  });

  it("signs in with verified tokens", async () => {
    // Arrange
    const transaction = await signInTransaction();
    fetchMock.mockResolvedValue(await tokenResponse(transaction.nonce));

    // Act
    const outcome = await completeAuthorization(callback(transaction), transaction, realm.keys);

    // Assert
    expect(outcome).toMatchObject({
      status: "signed-in",
      tokens: { refreshToken: "refresh-1", expiresIn: 300, refreshExpiresIn: 1800 },
      principal: { role: "PSYCHOLOGIST" },
    });
  });

  it.each([
    ["a missing transaction", "expired", (transaction: AuthorizationTransaction) => ({ params: callback(transaction), transaction: null })],
    ["a state mismatch", "failed", (transaction: AuthorizationTransaction) => ({ params: callback(transaction, { state: "forged-state-value" }), transaction })],
    ["a missing state", "failed", (transaction: AuthorizationTransaction) => ({ params: callback(transaction, { state: null }), transaction })],
    ["another issuer in the response", "failed", (transaction: AuthorizationTransaction) => ({ params: callback(transaction, { iss: "https://idp.example/realms/x" }), transaction })],
    ["no issuer in the response", "failed", (transaction: AuthorizationTransaction) => ({ params: callback(transaction, { iss: null }), transaction })],
    ["a missing code", "failed", (transaction: AuthorizationTransaction) => ({ params: callback(transaction, { code: null }), transaction })],
    ["a cancelled sign-in", "cancelled", (transaction: AuthorizationTransaction) => ({ params: callback(transaction, { code: null, error: "access_denied" }), transaction })],
    ["a provider error", "failed", (transaction: AuthorizationTransaction) => ({ params: callback(transaction, { code: null, error: "server_error" }), transaction })],
  ])("rejects %s without calling Keycloak", async (_scenario, reason, arrange) => {
    // Arrange
    const { params, transaction } = arrange(await signInTransaction());

    // Act
    const outcome = await completeAuthorization(params, transaction, realm.keys);

    // Assert
    expect(outcome).toEqual({ status: "failed", reason });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("rejects an ID token whose nonce belongs to another sign-in", async () => {
    // Arrange
    const transaction = await signInTransaction();
    fetchMock.mockResolvedValue(await tokenResponse("nonce-of-another-sign-in"));

    // Act
    const outcome = await completeAuthorization(callback(transaction), transaction, realm.keys);

    // Assert
    expect(outcome).toEqual({ status: "failed", reason: "failed" });
  });

  it("rejects an ID token issued to another client", async () => {
    // Arrange
    const transaction = await signInTransaction();
    fetchMock.mockResolvedValue(await tokenResponse(transaction.nonce, { audience: "another-client" }));

    // Act
    const outcome = await completeAuthorization(callback(transaction), transaction, realm.keys);

    // Assert
    expect(outcome).toEqual({ status: "failed", reason: "failed" });
  });

  it("rejects a refused code exchange", async () => {
    // Arrange
    const transaction = await signInTransaction();
    fetchMock.mockResolvedValue(new Response(JSON.stringify({ error: "invalid_grant" }), { status: 400 }));

    // Act
    const outcome = await completeAuthorization(callback(transaction), transaction, realm.keys);

    // Assert
    expect(outcome).toEqual({ status: "failed", reason: "failed" });
  });

  it("reports an unreachable token endpoint as unavailable", async () => {
    // Arrange
    const transaction = await signInTransaction();
    fetchMock.mockRejectedValue(new TypeError("fetch failed"));

    // Act
    const outcome = await completeAuthorization(callback(transaction), transaction, realm.keys);

    // Assert
    expect(outcome).toEqual({ status: "failed", reason: "unavailable" });
  });
});

describe("refreshTokens", () => {
  function refreshed(token: string) {
    return new Response(JSON.stringify({ access_token: "a", refresh_token: token, expires_in: 300, refresh_expires_in: 1800 }), { status: 200 });
  }

  it("shares one exchange between concurrent requests with the same refresh token", async () => {
    // Arrange
    fetchMock.mockImplementation(async () => refreshed("rotated-1"));

    // Act
    const [first, second] = await Promise.all([refreshTokens("shared-refresh", 1_000), refreshTokens("shared-refresh", 1_001)]);

    // Assert
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(first).toEqual(second);
  });

  it("exchanges again once the shared result has lapsed", async () => {
    // Arrange
    fetchMock.mockImplementation(async () => refreshed("rotated-2"));
    await refreshTokens("lapsing-refresh", 1_000);

    // Act
    await refreshTokens("lapsing-refresh", 1_000 + 31_000);

    // Assert
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it("reports a refused refresh as rejected", async () => {
    // Arrange
    fetchMock.mockResolvedValue(new Response(JSON.stringify({ error: "invalid_grant" }), { status: 400 }));

    // Act
    const result = await refreshTokens("revoked-refresh", 5_000);

    // Assert
    expect(result).toEqual({ status: "rejected" });
  });

  it("reports a Keycloak outage as unavailable", async () => {
    // Arrange
    fetchMock.mockResolvedValue(new Response("", { status: 503 }));

    // Act
    const result = await refreshTokens("outage-refresh", 6_000);

    // Assert
    expect(result).toEqual({ status: "unavailable" });
  });
});

describe("logout", () => {
  it("ends the Keycloak session on the back channel with the refresh token", async () => {
    // Arrange
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));

    // Act
    const ended = await endSession("refresh-to-end");

    // Assert
    const [url, init] = fetchMock.mock.calls[0];
    expect(ended).toBe(true);
    expect(url).toBe(`${INTERNAL_ISSUER}/protocol/openid-connect/logout`);
    expect(Object.fromEntries(init.body as URLSearchParams)).toEqual({ refresh_token: "refresh-to-end" });
  });

  it("returns an end-session URL that carries no token", () => {
    // Act
    const url = new URL(endSessionUrl());

    // Assert
    expect(url.origin + url.pathname).toBe(`${ISSUER}/protocol/openid-connect/logout`);
    expect(Object.fromEntries(url.searchParams)).toEqual({ client_id: "clinicflow-bff", post_logout_redirect_uri: `${PUBLIC_URL}/login` });
  });
});
