import "server-only";

import { oidcConfig } from "@/lib/server/oidc-config";
import type { DirectoryUser, Role } from "@/lib/types";

const REQUEST_TIMEOUT_MS = 10_000;
// Renew the service-account token before Keycloak would refuse it.
const TOKEN_RENEWAL_MARGIN_MS = 30_000;
const CLINICFLOW_ROLES: readonly Role[] = ["ADMIN", "PSYCHOLOGIST", "PATIENT"];

export type UserLookupResult =
  | { status: "found"; user: DirectoryUser }
  | { status: "not-found" }
  | { status: "unavailable" };

let serviceAccountToken: { value: string; renewAt: number } | null = null;

// Reads one account from the Keycloak Admin API with the read-only clinicflow-user-lookup client
// (ADR-031). The caller has already checked that the signed-in user is an administrator, and the id is
// a UUID. Keycloak's own error bodies never leave this module.
export async function lookUpUser(id: string, now = Date.now()): Promise<UserLookupResult> {
  const { usersEndpoint } = oidcConfig().userLookup;
  if (!usersEndpoint) return { status: "unavailable" };
  const userUrl = `${usersEndpoint}/${encodeURIComponent(id)}`;

  let token = await currentToken(now);
  if (!token) return { status: "unavailable" };
  let responses = await readUser(userUrl, token);
  if (responses?.some((response) => response.status === 401)) {
    // Keycloak restarted or rotated its keys: one retry with a new token.
    serviceAccountToken = null;
    token = await currentToken(now);
    if (!token) return { status: "unavailable" };
    responses = await readUser(userUrl, token);
  }
  if (!responses) return { status: "unavailable" };

  const [account, roles] = responses;
  if (account.status === 404 || roles.status === 404) return { status: "not-found" };
  if (!account.ok || !roles.ok) return { status: "unavailable" };
  try {
    const user = toDirectoryUser(await account.json(), await roles.json());
    return user ? { status: "found", user } : { status: "unavailable" };
  } catch {
    return { status: "unavailable" };
  }
}

async function readUser(userUrl: string, token: string) {
  const headers = { Accept: "application/json", Authorization: `Bearer ${token}` };
  const get = (url: string) => fetch(url, { headers, cache: "no-store", signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS) });
  try {
    // Effective roles, so a role held through a composite such as default-roles-clinicflow counts.
    return await Promise.all([get(userUrl), get(`${userUrl}/role-mappings/realm/composite`)]);
  } catch {
    return null;
  }
}

async function currentToken(now: number) {
  if (serviceAccountToken && serviceAccountToken.renewAt > now) return serviceAccountToken.value;
  const config = oidcConfig();
  const { clientId, clientSecret } = config.userLookup;
  if (!clientSecret) return null;
  // client_secret_basic: RFC 6749 section 2.3.1 form-encodes the id and secret before Base64.
  const credentials = `${encodeURIComponent(clientId)}:${encodeURIComponent(clientSecret)}`;
  try {
    const response = await fetch(config.tokenEndpoint, {
      method: "POST",
      headers: {
        Accept: "application/json",
        Authorization: `Basic ${Buffer.from(credentials).toString("base64")}`,
        "Content-Type": "application/x-www-form-urlencoded",
      },
      body: new URLSearchParams({ grant_type: "client_credentials" }),
      cache: "no-store",
      signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
    });
    if (!response.ok) return null;
    const body = (await response.json()) as Record<string, unknown>;
    if (typeof body.access_token !== "string" || typeof body.expires_in !== "number") return null;
    serviceAccountToken = { value: body.access_token, renewAt: now + body.expires_in * 1000 - TOKEN_RENEWAL_MARGIN_MS };
    return body.access_token;
  } catch {
    return null;
  }
}

function toDirectoryUser(account: unknown, roleMappings: unknown): DirectoryUser | null {
  if (!account || typeof account !== "object" || !Array.isArray(roleMappings)) return null;
  const user = account as Record<string, unknown>;
  if (typeof user.id !== "string" || typeof user.username !== "string" || typeof user.enabled !== "boolean") return null;
  const names = [user.firstName, user.lastName].filter((part): part is string => typeof part === "string" && part.trim() !== "");
  const roles = CLINICFLOW_ROLES.filter((role) => roleMappings.some((mapping) => (mapping as { name?: unknown })?.name === role));
  return {
    id: user.id,
    email: typeof user.email === "string" ? user.email : user.username,
    fullName: names.length ? names.join(" ") : user.username,
    // The BFF signs in only accounts with exactly one ClinicFlow role (ADR-027); null shows the others.
    role: roles.length === 1 ? roles[0] : null,
    enabled: user.enabled,
  };
}
