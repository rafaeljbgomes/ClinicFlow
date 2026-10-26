import { expect, test as base, type APIRequestContext, type APIResponse } from "@playwright/test";

type StorageState = Awaited<ReturnType<APIRequestContext["storageState"]>>;
type DemoTokens = { psychologist: string; patient: string; administrator: string };
type OwnedRecords = { owner: string; patientId: string; caseId: string };

const requestBaseURL = process.env.PLAYWRIGHT_REQUEST_BASE_URL ?? process.env.PLAYWRIGHT_BASE_URL;
const uiHost = { Host: "clinicflow.localhost" };
const apiHost = { Host: "api.clinicflow.localhost" };
const demoPassword = "ClinicFlowDemo!2026";
const demoAccounts = {
  psychologist: "sofia.almeida@demo.clinicflow.local",
  patient: "ana.martins@demo.clinicflow.local",
  administrator: "admin@demo.clinicflow.local",
};

// Logins go through gateway auth rate limits, so demo tokens, owned records, and the BFF session are
// arranged once per worker and shared read-only; every test still performs its own single action.
const test = base.extend<{ bffRequest: APIRequestContext }, {
  demoTokens: DemoTokens;
  ownedRecords: OwnedRecords;
  bffSession: StorageState;
}>({
  demoTokens: [async ({ playwright }, provide) => {
    const request = await playwright.request.newContext(contextOptions());
    try {
      await provide({
        psychologist: await login(request, demoAccounts.psychologist),
        patient: await login(request, demoAccounts.patient),
        administrator: await login(request, demoAccounts.administrator),
      });
    } finally {
      await request.dispose();
    }
  }, { scope: "worker" }],
  ownedRecords: [async ({ playwright }, provide) => {
    const request = await playwright.request.newContext(contextOptions());
    try {
      await provide(await createOwnedRecords(request));
    } finally {
      await request.dispose();
    }
  }, { scope: "worker" }],
  bffSession: [async ({ playwright }, provide) => {
    const request = await playwright.request.newContext(contextOptions());
    try {
      await arrangeStep(await bffLogin(request), 200, "BFF login");
      await provide(await request.storageState());
    } finally {
      await request.dispose();
    }
  }, { scope: "worker" }],
  bffRequest: async ({ playwright, bffSession }, provide) => {
    const request = await playwright.request.newContext({ ...contextOptions(), storageState: bffSession });
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

  test("a psychologist can read their own profile", async ({ request, demoTokens }) => {
    // Arrange
    const headers = signed(demoTokens.psychologist);

    // Act
    const response = await request.get("/api/v1/auth/users/me", { headers });

    // Assert
    expect(response.status()).toBe(200);
  });

  test("a psychologist cannot look up a user by id", async ({ request, demoTokens }) => {
    // Arrange
    const userId = await currentUserId(request, demoTokens.psychologist);

    // Act
    const response = await request.get(`/api/v1/auth/users/${userId}`, { headers: signed(demoTokens.psychologist) });

    // Assert
    expect(response.status()).toBe(403);
  });

  test("an administrator can look up a user by id", async ({ request, demoTokens }) => {
    // Arrange
    const userId = await currentUserId(request, demoTokens.psychologist);

    // Act
    const response = await request.get(`/api/v1/auth/users/${userId}`, { headers: signed(demoTokens.administrator) });

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
  test("login sets exactly the session and CSRF cookies with a transport-matching Secure flag", async ({ request }) => {
    // Arrange
    const secure = (process.env.PLAYWRIGHT_BASE_URL || "").startsWith("https:");

    // Act
    const response = await bffLogin(request);

    // Assert
    const cookies = response.headersArray().filter((h) => h.name.toLowerCase() === "set-cookie").map((h) => h.value);
    expect(response.status()).toBe(200);
    expect(cookies.length).toBe(2);
    expect(cookies.map((cookie) => /; secure/i.test(cookie))).toEqual(cookies.map(() => secure));
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

  test("logout succeeds with the CSRF token", async ({ bffRequest }) => {
    // Arrange
    const csrf = await csrfToken(bffRequest);

    // Act
    const response = await bffRequest.post("/api/auth/logout", { headers: { "x-csrf-token": csrf } });

    // Assert
    expect(response.status()).toBe(200);
  });

  test("the session is rejected after logout", async ({ bffRequest }) => {
    // Arrange
    const csrf = await csrfToken(bffRequest);
    await arrangeStep(
      await bffRequest.post("/api/auth/logout", { headers: { "x-csrf-token": csrf } }),
      200,
      "BFF logout"
    );

    // Act
    const response = await bffRequest.get("/api/patients");

    // Assert
    expect(response.status()).toBe(401);
  });
});

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

async function login(request: APIRequestContext, email: string, password = demoPassword) {
  const response = await request.post("/api/v1/auth/login", { headers: apiHost, data: { email, password } });
  await arrangeStep(response, 200, `login ${email}`);
  return (await response.json()).accessToken as string;
}

async function bffLogin(request: APIRequestContext) {
  return request.post("/api/auth/login", { data: { email: demoAccounts.psychologist, password: demoPassword } });
}

async function currentUserId(request: APIRequestContext, token: string) {
  const response = await arrangeStep(
    await request.get("/api/v1/auth/users/me", { headers: signed(token) }),
    200,
    "read current user"
  );
  return (await response.json()).id as string;
}

async function createOwnedRecords(request: APIRequestContext): Promise<OwnedRecords> {
  const stamp = Date.now();
  const email = `gateway.owner.${stamp}@example.com`;
  const password = "TestingPass123!";
  await arrangeStep(
    await request.post("/api/v1/auth/register", {
      headers: apiHost,
      data: { email, password, role: "PSYCHOLOGIST", fullName: "Synthetic Gateway Owner" },
    }),
    201,
    "register owner"
  );
  const owner = await login(request, email, password);
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
