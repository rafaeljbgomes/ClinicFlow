import { expect, test as base, type APIRequestContext, type APIResponse, type Browser, type PlaywrightWorkerArgs } from "@playwright/test";
import { keycloakAccessToken, provisionPsychologist, signInThroughBff, type StorageState } from "./keycloak";

type DemoTokens = { psychologist: string; patient: string; administrator: string };
// auth-service checks only its own tokens until it is removed (#90); the other services only Keycloak's.
type AuthServiceTokens = { psychologist: string; administrator: string };
type OwnedRecords = { owner: string; patientId: string; caseId: string };

const uiBaseURL = process.env.PLAYWRIGHT_BASE_URL ?? "http://localhost:3000";
const requestBaseURL = process.env.PLAYWRIGHT_REQUEST_BASE_URL ?? process.env.PLAYWRIGHT_BASE_URL;
const uiHost = { Host: "clinicflow.localhost" };
const apiHost = { Host: "api.clinicflow.localhost" };
const demoPassword = "ClinicFlowDemo!2026";
const demoAccounts = {
  psychologist: "sofia.almeida@demo.clinicflow.local",
  patient: "ana.martins@demo.clinicflow.local",
  administrator: "admin@demo.clinicflow.local",
};

// Sign-ins go through Keycloak's login page, so demo tokens, owned records, and the BFF session are
// arranged once per worker and shared read-only; every test still performs its own single action.
// Sign-out tests use a session of their own, because sign-out ends the Keycloak session.
const test = base.extend<{ bffRequest: APIRequestContext; ownBffRequest: APIRequestContext }, {
  demoTokens: DemoTokens;
  authServiceTokens: AuthServiceTokens;
  ownedRecords: OwnedRecords;
  bffSession: StorageState;
}>({
  demoTokens: [async ({ browser, playwright }, provide) => {
    await provide({
      psychologist: await apiToken(browser, playwright, demoAccounts.psychologist),
      patient: await apiToken(browser, playwright, demoAccounts.patient),
      administrator: await apiToken(browser, playwright, demoAccounts.administrator),
    });
  }, { scope: "worker" }],
  authServiceTokens: [async ({ playwright }, provide) => {
    const request = await playwright.request.newContext(contextOptions());
    try {
      await provide({
        psychologist: await authServiceLogin(request, demoAccounts.psychologist),
        administrator: await authServiceLogin(request, demoAccounts.administrator),
      });
    } finally {
      await request.dispose();
    }
  }, { scope: "worker" }],
  ownedRecords: [async ({ browser, playwright }, provide) => {
    const request = await playwright.request.newContext(contextOptions());
    try {
      await provide(await createOwnedRecords(browser, playwright, request));
    } finally {
      await request.dispose();
    }
  }, { scope: "worker" }],
  bffSession: [async ({ browser }, provide) => {
    await provide(await signInThroughBff(browser, browserOptions(), demoAccounts.psychologist, demoPassword));
  }, { scope: "worker" }],
  bffRequest: async ({ playwright, bffSession }, provide) => {
    const request = await playwright.request.newContext({ ...contextOptions(), storageState: bffSession });
    try {
      await provide(request);
    } finally {
      await request.dispose();
    }
  },
  ownBffRequest: async ({ browser, playwright }, provide) => {
    const storageState = await signInThroughBff(browser, browserOptions(), demoAccounts.psychologist, demoPassword);
    const request = await playwright.request.newContext({ ...contextOptions(), storageState });
    try {
      await provide(request);
    } finally {
      await request.dispose();
    }
  },
});

// Gateway deployment tests run only against an already-started Compose/Kubernetes environment.
test.skip(!process.env.PLAYWRIGHT_SKIP_WEBSERVER, "Requires deployed gateway and synthetic bootstrap data");
// APIRequestContext uses Node DNS; local browser .localhost resolution is independent.
// A test-only physical origin supports CA-isolated HTTPS without changing the OS hosts file.
test.use({
  baseURL: requestBaseURL,
  extraHTTPHeaders: uiHost,
});

test.describe("direct bearer APIs enforce backend roles and clinical/notification ownership", () => {
  for (const path of [
    "/api/v1/auth/users/me",
    "/api/v1/patients",
    "/api/v1/appointments",
    "/api/v1/clinical/clinical-cases",
    "/api/v1/notifications",
  ]) {
    test(`${path} rejects a request without a bearer token`, async ({ request }) => {
      // Act
      const response = await request.get(path, { headers: apiHost });

      // Assert
      expect(response.status()).toBe(401);
    });

    test(`${path} rejects an invalid bearer token`, async ({ request }) => {
      // Act
      const response = await request.get(path, { headers: signed("invalid.jwt.token") });

      // Assert
      expect(response.status()).toBe(401);
    });
  }

  for (const path of ["/api/v1/patients", "/api/v1/clinical/practice-profile", "/api/v1/notifications"]) {
    test(`${path} rejects the patient role`, async ({ request, demoTokens }) => {
      // Arrange
      const headers = signed(demoTokens.patient);

      // Act
      const response = await request.get(path, { headers });

      // Assert
      expect(response.status()).toBe(403);
    });
  }

  test("a psychologist can read their own profile", async ({ request, authServiceTokens }) => {
    // Arrange
    const headers = signed(authServiceTokens.psychologist);

    // Act
    const response = await request.get("/api/v1/auth/users/me", { headers });

    // Assert
    expect(response.status()).toBe(200);
  });

  test("a psychologist cannot look up a user by id", async ({ request, authServiceTokens }) => {
    // Arrange
    const userId = await currentUserId(request, authServiceTokens.psychologist);

    // Act
    const response = await request.get(`/api/v1/auth/users/${userId}`, { headers: signed(authServiceTokens.psychologist) });

    // Assert
    expect(response.status()).toBe(403);
  });

  test("an administrator can look up a user by id", async ({ request, authServiceTokens }) => {
    // Arrange
    const userId = await currentUserId(request, authServiceTokens.psychologist);

    // Act
    const response = await request.get(`/api/v1/auth/users/${userId}`, { headers: signed(authServiceTokens.administrator) });

    // Assert
    expect(response.status()).toBe(200);
  });

  test("another psychologist cannot read the owner's clinical case", async ({ request, demoTokens, ownedRecords }) => {
    // Arrange
    const casePath = `/api/v1/clinical/clinical-cases/${ownedRecords.caseId}`;

    // Act
    const response = await request.get(casePath, { headers: signed(demoTokens.psychologist) });

    // Assert
    // A foreign case answers like a missing one so case ids cannot be probed (ADR-021).
    expect(response.status()).toBe(404);
    expect((await response.json()).code).toBe("clinical_resource_not_found");
  });

  test("the owner reads the patient's clinical history", async ({ request, ownedRecords }) => {
    // Arrange
    const historyPath = `/api/v1/clinical/patients/${ownedRecords.patientId}/clinical-history`;

    // Act
    const response = await request.get(historyPath, { headers: signed(ownedRecords.owner) });

    // Assert
    expect(response.status()).toBe(200);
    expect((await response.json()).patientId).toBe(ownedRecords.patientId);
  });

  test("another psychologist sees an empty clinical history for the owner's patient", async ({
    request,
    demoTokens,
    ownedRecords,
  }) => {
    // Arrange
    const historyPath = `/api/v1/clinical/patients/${ownedRecords.patientId}/clinical-history`;

    // Act
    const response = await request.get(historyPath, { headers: signed(demoTokens.psychologist) });

    // Assert
    expect(response.status()).toBe(200);
    expect(await response.json()).toMatchObject({ cases: [], carePlans: [], sessionRecords: [] });
  });

  test("clinical history is not exposed through the patient API", async ({ request, ownedRecords }) => {
    // Arrange
    const patientHistoryPath = `/api/v1/patients/${ownedRecords.patientId}/clinical-history`;

    // Act
    const response = await request.get(patientHistoryPath, { headers: signed(ownedRecords.owner) });

    // Assert
    expect(response.status()).toBe(404);
  });

  test("another psychologist cannot read the owner's notifications", async ({ request, demoTokens, ownedRecords }) => {
    // Arrange
    const notifications = await ownerNotifications(request, ownedRecords.owner);

    // Act
    const statuses = await notificationStatuses(request, notifications, demoTokens.psychologist);

    // Assert
    expect(statuses).toEqual(notifications.map(() => 404));
  });

  test("an administrator can read the owner's notifications", async ({ request, demoTokens, ownedRecords }) => {
    // Arrange
    const notifications = await ownerNotifications(request, ownedRecords.owner);

    // Act
    const statuses = await notificationStatuses(request, notifications, demoTokens.administrator);

    // Assert
    expect(statuses).toEqual(notifications.map(() => 200));
  });

  test("the resource servers reject an auth-service token", async ({ request, authServiceTokens }) => {
    // Act
    const response = await request.get("/api/v1/patients", { headers: signed(authServiceTokens.psychologist) });

    // Assert
    expect(response.status()).toBe(401);
  });

  for (const path of ["/actuator/health", "/api/v1/unknown", "/dashboard", "/api/v1/patients-other"]) {
    test(`${path} is not routed on the API host`, async ({ request }) => {
      // Act
      const response = await request.get(path, { headers: apiHost });

      // Assert
      expect(response.status()).toBe(404);
    });
  }
});

test.describe("BFF session cookies respect transport configuration and mutations reject missing CSRF", () => {
  test("sign-in leaves exactly the session cookies, with a transport-matching Secure flag", async ({ bffSession }) => {
    // Arrange
    const secure = uiBaseURL.startsWith("https:");

    // Act
    // Cookies are not scoped by port, so Keycloak's own cookies on the same host are told apart by their path.
    const cookies = bffSession.cookies.filter((cookie) => cookie.domain === new URL(uiBaseURL).hostname && !cookie.path.startsWith("/realms/"));

    // Assert
    expect(cookies.map((cookie) => cookie.name).sort()).toEqual(["clinicflow_access_token", "clinicflow_csrf", "clinicflow_refresh_token"]);
    expect(cookies.map((cookie) => cookie.secure)).toEqual(cookies.map(() => secure));
  });

  test("sign-in keeps the tokens in HttpOnly cookies", async ({ bffSession }) => {
    // Act
    const httpOnly = Object.fromEntries(bffSession.cookies.filter((cookie) => cookie.name.startsWith("clinicflow_")).map((cookie) => [cookie.name, cookie.httpOnly]));

    // Assert
    expect(httpOnly).toEqual({ clinicflow_access_token: true, clinicflow_refresh_token: true, clinicflow_csrf: false });
  });

  test("the BFF no longer accepts a password", async ({ request }) => {
    // Act
    const response = await request.post("/api/auth/login", { data: { email: demoAccounts.psychologist, password: demoPassword } });

    // Assert
    expect(response.status()).toBe(405);
  });

  test("login stores a CSRF cookie for the browser client", async ({ bffRequest }) => {
    // Act
    const state = await bffRequest.storageState();

    // Assert
    expect(state.cookies.find((c) => c.name === "clinicflow_csrf")?.value).toBeTruthy();
  });

  test("a mutation without a CSRF token is rejected", async ({ bffRequest }) => {
    // Act
    const response = await bffRequest.post("/api/patients", { data: {} });

    // Assert
    expect(response.status()).toBe(403);
  });

  test("a read with the session cookie succeeds", async ({ bffRequest }) => {
    // Act
    const response = await bffRequest.get("/api/patients");

    // Assert
    expect(response.status()).toBe(200);
  });

  test("logout succeeds with the CSRF token", async ({ ownBffRequest }) => {
    // Arrange
    const csrf = await csrfToken(ownBffRequest);

    // Act
    const response = await ownBffRequest.post("/api/auth/logout", { headers: { "x-csrf-token": csrf } });

    // Assert
    expect(response.status()).toBe(200);
  });

  test("the session is rejected after logout", async ({ ownBffRequest }) => {
    // Arrange
    const csrf = await csrfToken(ownBffRequest);
    await arrangeStep(
      await ownBffRequest.post("/api/auth/logout", { headers: { "x-csrf-token": csrf } }),
      200,
      "BFF logout"
    );

    // Act
    const response = await ownBffRequest.get("/api/patients");

    // Assert
    expect(response.status()).toBe(401);
  });

  test("logout revokes the refresh token in Keycloak", async ({ playwright, ownBffRequest }) => {
    // Arrange
    const before = await ownBffRequest.storageState();
    await arrangeStep(
      await ownBffRequest.post("/api/auth/logout", { headers: { "x-csrf-token": await csrfToken(ownBffRequest) } }),
      200,
      "BFF logout"
    );
    // Only the refresh cookie survives, so the next request has to refresh.
    const replay = await playwright.request.newContext({
      ...contextOptions(),
      storageState: { ...before, cookies: before.cookies.filter((cookie) => cookie.name !== "clinicflow_access_token") },
    });

    // Act
    const response = await replay.get("/api/patients");

    // Assert
    expect(response.status()).toBe(401);
    await replay.dispose();
  });
});

function browserOptions() {
  return { baseURL: uiBaseURL, ignoreHTTPSErrors: process.env.PLAYWRIGHT_IGNORE_HTTPS_ERRORS === "true" };
}

// Keycloak is called on its own URL, so this context carries none of the gateway Host headers.
async function apiToken(browser: Browser, playwright: PlaywrightWorkerArgs["playwright"], email: string, password = demoPassword) {
  const request = await playwright.request.newContext();
  try {
    return await keycloakAccessToken(browser, request, uiBaseURL, email, password);
  } finally {
    await request.dispose();
  }
}

function contextOptions() {
  return {
    baseURL: requestBaseURL,
    extraHTTPHeaders: uiHost,
    ignoreHTTPSErrors: process.env.PLAYWRIGHT_IGNORE_HTTPS_ERRORS === "true",
  };
}

function signed(token: string) {
  return { ...apiHost, Authorization: `Bearer ${token}` };
}

async function arrangeStep(response: APIResponse, expectedStatus: number, step: string) {
  if (response.status() !== expectedStatus) {
    throw new Error(`Arrange step "${step}" returned ${response.status()}, expected ${expectedStatus}`);
  }
  return response;
}

async function authServiceLogin(request: APIRequestContext, email: string, password = demoPassword) {
  const response = await request.post("/api/v1/auth/login", { headers: apiHost, data: { email, password } });
  await arrangeStep(response, 200, `auth-service login ${email}`);
  return (await response.json()).accessToken as string;
}

async function currentUserId(request: APIRequestContext, token: string) {
  const response = await arrangeStep(
    await request.get("/api/v1/auth/users/me", { headers: signed(token) }),
    200,
    "read current user"
  );
  return (await response.json()).id as string;
}

async function createOwnedRecords(browser: Browser, playwright: PlaywrightWorkerArgs["playwright"], request: APIRequestContext): Promise<OwnedRecords> {
  const stamp = Date.now();
  const email = `gateway.owner.${stamp}@example.com`;
  const password = "TestingPass123!";
  const admin = await playwright.request.newContext();
  try {
    await provisionPsychologist(admin, { email, password, firstName: "Synthetic Gateway", lastName: "Owner" });
  } finally {
    await admin.dispose();
  }
  const owner = await apiToken(browser, playwright, email, password);
  const patient = await arrangeStep(
    await request.post("/api/v1/patients", {
      headers: signed(owner),
      data: { email: `gateway.patient.${stamp}@example.com`, fullName: "Synthetic API Patient", consentStatus: "GRANTED" },
    }),
    201,
    "create patient"
  );
  const patientId = (await patient.json()).id as string;
  const clinicalCase = await arrangeStep(
    await request.post("/api/v1/clinical/clinical-cases", {
      headers: signed(owner),
      data: { patientId, presentingConcern: "Synthetic gateway ownership check" },
    }),
    201,
    "create clinical case"
  );
  return { owner, patientId, caseId: (await clinicalCase.json()).id as string };
}

// Patient creation emits a notification; its owner must survive the async event boundary.
async function ownerNotifications(request: APIRequestContext, owner: string) {
  const deadline = Date.now() + 10_000;
  while (Date.now() < deadline) {
    const response = await request.get("/api/v1/notifications", { headers: signed(owner) });
    const notifications = response.ok() ? ((await response.json()) as { content: Array<{ id: string }> }).content : [];
    if (notifications.length > 0) return notifications;
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
  throw new Error('Arrange step "wait for owner notifications" timed out');
}

async function notificationStatuses(request: APIRequestContext, notifications: Array<{ id: string }>, token: string) {
  const statuses: number[] = [];
  for (const notification of notifications) {
    statuses.push((await request.get(`/api/v1/notifications/${notification.id}`, { headers: signed(token) })).status());
  }
  return statuses;
}

async function csrfToken(request: APIRequestContext) {
  const csrf = (await request.storageState()).cookies.find((c) => c.name === "clinicflow_csrf")?.value;
  if (!csrf) throw new Error('Arrange step "read CSRF cookie" found no clinicflow_csrf cookie');
  return csrf;
}
