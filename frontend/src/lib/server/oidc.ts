import "server-only";

import { timingSafeEqual } from "node:crypto";
import { jwtVerify, type JWTVerifyGetKey } from "jose";
import { isKeySetUnavailable, realmKeys, verifyAccessToken, type VerifiedAccessTokenClaims } from "@/lib/server/jwt";
import { oidcConfig } from "@/lib/server/oidc-config";

const TOKEN_TIMEOUT_MS = 10_000;
// Concurrent requests that carry the same refresh token share one exchange: Keycloak rotates refresh
// tokens and refuses a reused one, so a second exchange would end the session.
const REFRESH_SHARE_MS = 30_000;

export type AuthorizationKind = "login" | "register";

export type AuthorizationTransaction = {
  state: string;
  nonce: string;
  verifier: string;
  returnTo: string | null;
};

export type TokenSet = {
  accessToken: string;
  refreshToken: string;
  idToken: string | null;
  expiresIn: number;
  refreshExpiresIn: number;
};

export type TokenResult =
  | { status: "issued"; tokens: TokenSet }
  | { status: "rejected" }
  | { status: "unavailable" };

export type CallbackFailure = "cancelled" | "expired" | "failed" | "unavailable";

export type CallbackOutcome =
  | { status: "signed-in"; tokens: TokenSet; principal: VerifiedAccessTokenClaims }
  | { status: "failed"; reason: CallbackFailure };

export async function startAuthorization(kind: AuthorizationKind, returnTo: string | null) {
  const config = oidcConfig();
  const transaction: AuthorizationTransaction = {
    state: randomToken(),
    nonce: randomToken(),
    verifier: randomToken(32),
    returnTo: safeReturnTo(returnTo),
  };
  const url = new URL(kind === "register" ? config.registrationEndpoint : config.authorizationEndpoint);
  url.search = new URLSearchParams({
    response_type: "code",
    client_id: config.clientId,
    redirect_uri: config.redirectUri,
    scope: "openid",
    state: transaction.state,
    nonce: transaction.nonce,
    code_challenge: await pkceChallenge(transaction.verifier),
    code_challenge_method: "S256",
  }).toString();
  return { url: url.toString(), transaction };
}

// Only dashboard paths on this origin; never a scheme, host or protocol-relative URL.
export function safeReturnTo(value: string | null | undefined) {
  if (!value || !/^\/dashboard(?:[/?#]|$)/.test(value) || value.includes("\\") || value.includes("//")) return null;
  return value;
}

export async function completeAuthorization(
  params: URLSearchParams,
  transaction: AuthorizationTransaction | null,
  keys?: JWTVerifyGetKey
): Promise<CallbackOutcome> {
  const config = oidcConfig();
  if (!transaction) return { status: "failed", reason: "expired" };
  if (!sameValue(params.get("state"), transaction.state)) return { status: "failed", reason: "failed" };
  const error = params.get("error");
  if (error) return { status: "failed", reason: error === "access_denied" ? "cancelled" : "failed" };
  // RFC 9207: the authorization response names its issuer, which defeats mix-up attacks.
  if (params.get("iss") !== config.issuer) return { status: "failed", reason: "failed" };
  const code = params.get("code");
  if (!code) return { status: "failed", reason: "failed" };

  const exchange = await tokenRequest({
    grant_type: "authorization_code",
    code,
    redirect_uri: config.redirectUri,
    code_verifier: transaction.verifier,
  });
  if (exchange.status !== "issued") return { status: "failed", reason: exchange.status === "unavailable" ? "unavailable" : "failed" };
  const { tokens } = exchange;
  if (!tokens.idToken) return { status: "failed", reason: "failed" };

  try {
    const { payload } = await jwtVerify(tokens.idToken, keys ?? realmKeys(), {
      algorithms: ["RS256"],
      issuer: config.issuer,
      audience: config.clientId,
      requiredClaims: ["exp", "sub", "nonce"],
    });
    if (!sameValue(typeof payload.nonce === "string" ? payload.nonce : null, transaction.nonce)) {
      return { status: "failed", reason: "failed" };
    }
  } catch (verificationError) {
    return { status: "failed", reason: isKeySetUnavailable(verificationError) ? "unavailable" : "failed" };
  }

  const access = await verifyAccessToken(tokens.accessToken, keys);
  if (access.status !== "verified") return { status: "failed", reason: access.status === "unavailable" ? "unavailable" : "failed" };
  return { status: "signed-in", tokens, principal: access.principal };
}

const sharedRefreshes = new Map<string, { result: Promise<TokenResult>; expiresAt: number }>();

export function refreshTokens(refreshToken: string, now = Date.now()): Promise<TokenResult> {
  for (const [token, entry] of sharedRefreshes) {
    if (entry.expiresAt <= now) sharedRefreshes.delete(token);
  }
  const shared = sharedRefreshes.get(refreshToken);
  if (shared) return shared.result;
  const result = tokenRequest({ grant_type: "refresh_token", refresh_token: refreshToken });
  sharedRefreshes.set(refreshToken, { result, expiresAt: now + REFRESH_SHARE_MS });
  return result;
}

// Ends the Keycloak session server-side, so no token has to travel through the browser on logout.
export async function endSession(refreshToken: string) {
  const config = oidcConfig();
  try {
    const response = await fetch(config.backchannelLogoutEndpoint, {
      method: "POST",
      headers: clientHeaders(),
      body: new URLSearchParams({ refresh_token: refreshToken }),
      cache: "no-store",
      signal: AbortSignal.timeout(TOKEN_TIMEOUT_MS),
    });
    return response.ok;
  } catch {
    return false;
  }
}

// RP-initiated logout clears Keycloak's own browser cookies. The session is already over, so the
// request carries no id_token_hint.
export function endSessionUrl() {
  const config = oidcConfig();
  const url = new URL(config.endSessionEndpoint);
  url.search = new URLSearchParams({
    client_id: config.clientId,
    post_logout_redirect_uri: config.postLogoutRedirectUri,
  }).toString();
  return url.toString();
}

export function encodeTransaction(transaction: AuthorizationTransaction) {
  return Buffer.from(JSON.stringify(transaction)).toString("base64url");
}

export function decodeTransaction(value: string | undefined): AuthorizationTransaction | null {
  if (!value) return null;
  try {
    const parsed = JSON.parse(Buffer.from(value, "base64url").toString("utf8")) as Partial<AuthorizationTransaction>;
    if (
      typeof parsed.state !== "string" ||
      typeof parsed.nonce !== "string" ||
      typeof parsed.verifier !== "string" ||
      parsed.state.length < 16 ||
      parsed.nonce.length < 16 ||
      parsed.verifier.length < 43
    ) {
      return null;
    }
    return { state: parsed.state, nonce: parsed.nonce, verifier: parsed.verifier, returnTo: safeReturnTo(parsed.returnTo) };
  } catch {
    return null;
  }
}

async function tokenRequest(fields: Record<string, string>): Promise<TokenResult> {
  const config = oidcConfig();
  try {
    const response = await fetch(config.tokenEndpoint, {
      method: "POST",
      headers: clientHeaders(),
      body: new URLSearchParams(fields),
      cache: "no-store",
      signal: AbortSignal.timeout(TOKEN_TIMEOUT_MS),
    });
    if (response.status >= 500) return { status: "unavailable" };
    if (!response.ok) return { status: "rejected" };
    const tokens = toTokenSet(await response.json());
    return tokens ? { status: "issued", tokens } : { status: "rejected" };
  } catch {
    return { status: "unavailable" };
  }
}

function toTokenSet(value: unknown): TokenSet | null {
  if (!value || typeof value !== "object") return null;
  const body = value as Record<string, unknown>;
  if (
    typeof body.access_token !== "string" ||
    typeof body.refresh_token !== "string" ||
    typeof body.expires_in !== "number" ||
    typeof body.refresh_expires_in !== "number"
  ) {
    return null;
  }
  return {
    accessToken: body.access_token,
    refreshToken: body.refresh_token,
    idToken: typeof body.id_token === "string" ? body.id_token : null,
    expiresIn: body.expires_in,
    refreshExpiresIn: body.refresh_expires_in,
  };
}

// client_secret_basic: RFC 6749 section 2.3.1 form-encodes the id and secret before Base64.
function clientHeaders() {
  const config = oidcConfig();
  const credentials = `${encodeURIComponent(config.clientId)}:${encodeURIComponent(config.clientSecret)}`;
  return {
    Accept: "application/json",
    Authorization: `Basic ${Buffer.from(credentials).toString("base64")}`,
    "Content-Type": "application/x-www-form-urlencoded",
  };
}

function randomToken(bytes = 16) {
  return Buffer.from(crypto.getRandomValues(new Uint8Array(bytes))).toString("base64url");
}

async function pkceChallenge(verifier: string) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(verifier));
  return Buffer.from(digest).toString("base64url");
}

function sameValue(received: string | null, expected: string) {
  if (received === null) return false;
  const left = Buffer.from(received);
  const right = Buffer.from(expected);
  return left.length === right.length && timingSafeEqual(left, right);
}
