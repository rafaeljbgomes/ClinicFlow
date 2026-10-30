import { createHash, randomBytes, randomUUID } from "node:crypto";
import { readFileSync } from "node:fs";
import path from "node:path";
import type { APIRequestContext, Browser, BrowserContextOptions, Page } from "@playwright/test";

// Keycloak's public URL (ADR-027). CI publishes it on the run's isolated port; Compose defaults to 8180.
export const keycloakUrl =
  process.env.PLAYWRIGHT_KEYCLOAK_URL ?? `http://localhost:${process.env.CLINICFLOW_KEYCLOAK_PORT ?? "8180"}`;
const realmUrl = `${keycloakUrl}/realms/clinicflow`;
// The admin API and the token exchange from Node. On Kubernetes the gateway routes neither the master realm
// nor the admin API, so they go through kubectl port-forward; in Compose this is the public URL.
const keycloakBackchannelUrl = process.env.PLAYWRIGHT_KEYCLOAK_BACKCHANNEL_URL ?? keycloakUrl;
const secretsDirectory = path.resolve(__dirname, "../../../secrets/keycloak");

export type StorageState = Awaited<ReturnType<APIRequestContext["storageState"]>>;

// A spec's test.use() options also reach browser.newContext(); a Host header there makes Chromium refuse
// every navigation, so browser contexts start without extra headers.
const browserDefaults: BrowserContextOptions = { extraHTTPHeaders: {} };

// Credentials are typed on Keycloak's page only; ClinicFlow never sees them.
export async function fillKeycloakLogin(page: Page, email: string, password: string) {
  await page.locator("#username").fill(email);
  await page.locator("#password").fill(password);
  await page.locator("#kc-login").click();
}

// Signs in through the BFF's authorization-code flow and returns the browser state, cookies included.
export async function signInThroughBff(browser: Browser, options: BrowserContextOptions, email: string, password: string) {
  const context = await browser.newContext({ ...browserDefaults, ...options });
  try {
    const page = await context.newPage();
    await page.goto("/api/auth/login");
    await fillKeycloakLogin(page, email, password);
    await page.waitForURL(/\/dashboard(?:\/|$)/, { timeout: 30_000 });
    return await context.storageState();
  } finally {
    await context.close();
  }
}

// An access token for direct API calls, as the BFF would receive it: the code from a real browser login is
// read from the redirect and exchanged here with the client secret.
export async function keycloakAccessToken(browser: Browser, request: APIRequestContext, uiUrl: string, email: string, password: string) {
  const redirectUri = `${new URL(uiUrl).origin}/api/auth/callback`;
  const verifier = randomBytes(32).toString("base64url");
  const state = randomBytes(16).toString("base64url");
  const authorize = new URL(`${realmUrl}/protocol/openid-connect/auth`);
  authorize.search = new URLSearchParams({
    response_type: "code",
    client_id: "clinicflow-bff",
    redirect_uri: redirectUri,
    scope: "openid",
    state,
    code_challenge: createHash("sha256").update(verifier).digest("base64url"),
    code_challenge_method: "S256",
  }).toString();

  const context = await browser.newContext(browserDefaults);
  try {
    const page = await context.newPage();
    await page.goto(authorize.toString());
    // Routing does not see a request reached through a redirect, so the code is read from the request
    // itself. The BFF callback then refuses it for lack of a sign-in of its own, without spending the code.
    const redirected = page.waitForRequest((request) => request.url().startsWith(redirectUri));
    await fillKeycloakLogin(page, email, password);
    const callback = new URL((await redirected).url());
    if (callback.searchParams.get("state") !== state || !callback.searchParams.get("code")) {
      throw new Error(`Arrange step "Keycloak login ${email}" returned no authorization code`);
    }
    const response = await request.post(`${keycloakBackchannelUrl}/realms/clinicflow/protocol/openid-connect/token`, {
      headers: { Authorization: `Basic ${Buffer.from(`clinicflow-bff:${clientSecret()}`).toString("base64")}` },
      form: { grant_type: "authorization_code", code: callback.searchParams.get("code")!, redirect_uri: redirectUri, code_verifier: verifier },
    });
    if (!response.ok()) throw new Error(`Arrange step "token exchange ${email}" returned ${response.status()}`);
    return (await response.json()).access_token as string;
  } finally {
    await context.close();
  }
}

// Psychologists are provisioned by an administrator (ADR-027). partialImport keeps the given id and adds
// no default role, so the token carries exactly PSYCHOLOGIST.
export async function provisionPsychologist(request: APIRequestContext, user: { email: string; password: string; firstName: string; lastName: string }) {
  const admin = readEnvFile("admin.env");
  const login = await request.post(`${keycloakBackchannelUrl}/realms/master/protocol/openid-connect/token`, {
    form: { grant_type: "password", client_id: "admin-cli", username: admin.KC_BOOTSTRAP_ADMIN_USERNAME, password: admin.KC_BOOTSTRAP_ADMIN_PASSWORD },
  });
  if (!login.ok()) throw new Error(`Arrange step "Keycloak administrator login" returned ${login.status()}`);
  const id = randomUUID();
  const response = await request.post(`${keycloakBackchannelUrl}/admin/realms/clinicflow/partialImport`, {
    headers: { Authorization: `Bearer ${(await login.json()).access_token}` },
    data: {
      ifResourceExists: "FAIL",
      users: [{
        id,
        username: user.email,
        email: user.email,
        emailVerified: true,
        enabled: true,
        firstName: user.firstName,
        lastName: user.lastName,
        realmRoles: ["PSYCHOLOGIST"],
        credentials: [{ type: "password", value: user.password, temporary: false }],
      }],
    },
  });
  if (!response.ok()) throw new Error(`Arrange step "provision psychologist" returned ${response.status()}`);
  return id;
}

function clientSecret() {
  return process.env.CLINICFLOW_BFF_CLIENT_SECRET ?? readEnvFile("clinicflow-bff.env").CLINICFLOW_BFF_CLIENT_SECRET;
}

// The generated, Git-ignored files the realm itself is configured from (ADR-026).
export function readEnvFile(name: string) {
  const values: Record<string, string> = {};
  for (const line of readFileSync(path.join(secretsDirectory, name), "utf8").split(/\r?\n/)) {
    const separator = line.indexOf("=");
    if (separator > 0) values[line.slice(0, separator)] = line.slice(separator + 1);
  }
  return values;
}
