import {
  expect,
  test as base,
  type APIResponse,
  type BrowserContext,
  type Locator,
  type Page,
} from "@playwright/test";
import { fillKeycloakLogin, provisionPsychologist, signInThroughBff, type StorageState } from "./keycloak";

type DemoRole = "psychologist" | "patient" | "administrator";
type Workspace = { fullName: string; storageState: StorageState };
type Patient = { id: string; fullName: string; email: string; version: number };
type SessionRecord = {
  id: string;
  sessionDate: string;
  modality: string;
  durationMinutes: number;
  attendanceStatus: string;
  version: number;
};

const demoPassword = "ClinicFlowDemo!2026";
const demoAccounts: Record<DemoRole, { email: string; home: string }> = {
  psychologist: { email: "sofia.almeida@demo.clinicflow.local", home: "/dashboard" },
  patient: { email: "ana.martins@demo.clinicflow.local", home: "/dashboard/access-limited" },
  administrator: { email: "admin@demo.clinicflow.local", home: "/dashboard/system" },
};
// The realm's fixed demo ids (ADR-027).
const demoUserIds = {
  psychologist: "00000000-0000-4000-8000-000000000011",
  patient: "00000000-0000-4000-8000-000000000021",
};
const workspaceName = "ClinicFlow Demo Psychologist";
const therapeuticFocus = "Stabilize routines and reduce acute stress triggers.";
const sessionSummary = "Reviewed weekly routine and agreed on sleep hygiene homework.";

// Sign-ins go through Keycloak's login page, so demo sessions and the synthetic psychologist workspace
// are arranged once per worker and shared read-only; every test arranges its own records through the
// BFF and performs a single action. Tests that sign out use a session of their own, because sign-out
// ends the Keycloak session.
const test = base.extend<{ workspacePage: Page; pageAs: (role: DemoRole) => Promise<Page> }, {
  demoSessions: Record<DemoRole, StorageState>;
  workspace: Workspace;
}>({
  demoSessions: [async ({ browser }, provide, workerInfo) => {
    const sessions = {} as Record<DemoRole, StorageState>;
    for (const role of Object.keys(demoAccounts) as DemoRole[]) {
      sessions[role] = await signInThroughBff(browser, requestOptions(workerInfo.project.use), demoAccounts[role].email, demoPassword);
    }
    await provide(sessions);
  }, { scope: "worker" }],
  workspace: [async ({ browser, playwright }, provide, workerInfo) => {
    const request = await playwright.request.newContext();
    try {
      const email = `psychologist.${Date.now()}.${workerInfo.workerIndex}@example.com`;
      const password = "TestingPass123!";
      // Psychologist accounts come from an administrator; self-registration yields PATIENT only (ADR-027).
      await provisionPsychologist(request, { email, password, firstName: "ClinicFlow Demo", lastName: "Psychologist" });
      const storageState = await signInThroughBff(browser, requestOptions(workerInfo.project.use), email, password);
      await provide({ fullName: workspaceName, storageState });
    } finally {
      await request.dispose();
    }
  }, { scope: "worker" }],
  workspacePage: async ({ browser, workspace }, provide) => {
    const context = await browser.newContext({ storageState: workspace.storageState });
    try {
      await provide(await context.newPage());
    } finally {
      await context.close();
    }
  },
  pageAs: async ({ browser, demoSessions }, provide) => {
    const contexts: BrowserContext[] = [];
    await provide(async (role) => {
      const context = await browser.newContext({ storageState: demoSessions[role] });
      contexts.push(context);
      return context.newPage();
    });
    await Promise.all(contexts.map((context) => context.close()));
  },
});

test.describe("session routing", () => {
  test("sends anonymous dashboard visitors to login", async ({ page }) => {
    // Act
    await page.goto("/dashboard");

    // Assert
    await expect(page).toHaveURL(/\/login$/);
  });

  test("keeps the login page available with a stale access cookie", async ({ context, page, baseURL }) => {
    // Arrange
    await addStaleAccessCookie(context, baseURL);

    // Act
    await page.goto("/login");

    // Assert
    await expect(page).toHaveURL(/\/login$/);
    await expect(page.locator('a[href="/api/auth/login"]')).toBeVisible();
  });

  test("sends stale sessions back to login", async ({ context, page, baseURL }) => {
    // Arrange
    await addStaleAccessCookie(context, baseURL);

    // Act
    await page.goto("/dashboard");

    // Assert
    await expect(page).toHaveURL(/\/login$/);
  });
});

test.describe("sign-in through Keycloak", () => {
  test("signs in on Keycloak's page and returns to the role's home", async ({ page }) => {
    // Arrange
    await page.goto("/login");
    await page.locator('a[href="/api/auth/login"]').click();

    // Act
    await fillKeycloakLogin(page, demoAccounts.psychologist.email, demoPassword);

    // Assert
    await expect(page).toHaveURL(/\/dashboard$/, { timeout: 30_000 });
    await expect(page.getByRole("heading", { name: "Today" })).toBeVisible({ timeout: 30_000 });
  });

  test("leaves no code or token in the address bar after the callback", async ({ page }) => {
    // Arrange
    await page.goto("/api/auth/login");

    // Act
    await fillKeycloakLogin(page, demoAccounts.psychologist.email, demoPassword);

    // Assert
    await expect(page).toHaveURL(/\/dashboard$/, { timeout: 30_000 });
    expect(new URL(page.url()).search).toBe("");
  });

  test("keeps the tokens out of reach of page scripts", async ({ page }) => {
    // Arrange
    await page.goto("/api/auth/login");
    await fillKeycloakLogin(page, demoAccounts.psychologist.email, demoPassword);
    await expect(page).toHaveURL(/\/dashboard$/, { timeout: 30_000 });

    // Act
    const visible = await page.evaluate(() => document.cookie.split("; ").map((cookie) => cookie.split("=")[0]));

    // Assert
    expect(visible).toEqual(["clinicflow_csrf"]);
  });

  test("renews a lapsed access cookie from the refresh token", async ({ page }) => {
    // Arrange
    await page.goto("/api/auth/login");
    await fillKeycloakLogin(page, demoAccounts.psychologist.email, demoPassword);
    await expect(page).toHaveURL(/\/dashboard$/, { timeout: 30_000 });
    await page.context().clearCookies({ name: "clinicflow_access_token" });

    // Act
    await page.goto("/dashboard");

    // Assert
    await expect(page.getByRole("heading", { name: "Today" })).toBeVisible({ timeout: 30_000 });
    expect((await page.context().cookies()).some((cookie) => cookie.name === "clinicflow_access_token")).toBe(true);
  });

  test("signing out ends the Keycloak session and returns to login", async ({ page }) => {
    // Arrange
    await page.goto("/api/auth/login");
    await fillKeycloakLogin(page, demoAccounts.psychologist.email, demoPassword);
    await expect(page.getByRole("heading", { name: "Today" })).toBeVisible({ timeout: 30_000 });
    await page.getByRole("button", { name: "Sofia Almeida" }).click();

    // Act
    await page.getByRole("menuitem", { name: "Sign out" }).click();

    // Assert
    await expect(page).toHaveURL(/\/login$/, { timeout: 30_000 });
    await page.goto("/api/auth/login");
    await expect(page.locator("#username")).toBeVisible();
  });

  test("self-registration creates a patient account", async ({ page }) => {
    // Arrange
    await page.goto("/register");
    await page.locator('a[href="/api/auth/register"]').click();
    await page.locator("#email").fill(`registration.${Date.now()}@example.test`);
    await page.locator("#firstName").fill("Self");
    await page.locator("#lastName").fill("Registered");
    await page.locator("#password").fill("TestingPass123!");
    await page.locator("#password-confirm").fill("TestingPass123!");

    // Act
    await page.locator("#kc-form-buttons input[type=submit], #kc-form-buttons button[type=submit]").click();

    // Assert
    await expect(page).toHaveURL(/\/dashboard\/access-limited$/, { timeout: 30_000 });
  });
});

test.describe("role isolation", () => {
  for (const role of Object.keys(demoAccounts) as DemoRole[]) {
    test(`routes the ${role} to their home`, async ({ pageAs }) => {
      // Arrange
      const page = await pageAs(role);

      // Act
      await page.goto("/dashboard/entry");

      // Assert
      await expect(page).toHaveURL(new RegExp(`${demoAccounts[role].home.replaceAll("/", "\\/")}$`));
    });
  }

  test("lets administrators look up an account on the system page", async ({ pageAs }) => {
    // Arrange
    const page = await pageAs("administrator");
    await page.goto("/dashboard/system");
    await page.getByPlaceholder("User UUID").fill(demoUserIds.psychologist);

    // Act
    await page.getByRole("button", { name: "Inspect" }).click();

    // Assert
    // The account comes from Keycloak's Admin API through the BFF's read-only service account (ADR-031).
    await expect(page.getByText(demoAccounts.psychologist.email, { exact: true })).toBeVisible();
  });

  test("reports the role and state Keycloak holds for a looked-up account", async ({ pageAs }) => {
    // Arrange
    const page = await pageAs("administrator");

    // Act
    const response = await page.request.get(`/api/users/${demoUserIds.patient}`);

    // Assert
    expect(await response.json()).toEqual({
      id: demoUserIds.patient,
      email: demoAccounts.patient.email,
      fullName: "Ana Martins",
      role: "PATIENT",
      enabled: true,
    });
  });

  test("reports an unknown account as not found", async ({ pageAs }) => {
    // Arrange
    const page = await pageAs("administrator");

    // Act
    const response = await page.request.get("/api/users/9b38f432-6b8f-4d75-9e6a-e775cc4b0175");

    // Assert
    expect(response.status()).toBe(404);
  });

  test("forbids psychologists from looking up accounts", async ({ pageAs }) => {
    // Arrange
    const page = await pageAs("psychologist");

    // Act
    const response = await page.request.get(`/api/users/${demoUserIds.patient}`);

    // Assert
    expect(response.status()).toBe(403);
  });

  test("forbids patients from reading system health", async ({ pageAs }) => {
    // Arrange
    const page = await pageAs("patient");

    // Act
    const response = await page.request.get("/api/system/health");

    // Assert
    expect(response.status()).toBe(403);
  });

  test("lets administrators read system health", async ({ pageAs }) => {
    // Arrange
    const page = await pageAs("administrator");

    // Act
    const response = await page.request.get("/api/system/health");

    // Assert
    expect(response.status()).toBe(200);
  });
});

test.describe("psychologist workspace shell", () => {
  test("marks Today as the current navigation entry", async ({ workspacePage: page }) => {
    // Act
    await page.goto("/dashboard");

    // Assert
    await expect(page.getByRole("link", { name: "Today" })).toHaveAttribute("aria-current", "page");
  });

  test("offers a single Schedule action", async ({ workspacePage: page }) => {
    // Act
    await page.goto("/dashboard");

    // Assert
    await expect(page.getByRole("button", { name: "Schedule", exact: true })).toHaveCount(1);
  });

  test("hides the System area from psychologists", async ({ workspacePage: page }) => {
    // Act
    await page.goto("/dashboard");

    // Assert
    await expect(page.getByRole("heading", { name: "Today" })).toBeVisible({ timeout: 30_000 });
    await expect(page.getByRole("link", { name: "System" })).toHaveCount(0);
  });

  test("account menu offers settings and sign out", async ({ workspacePage: page }) => {
    // Arrange
    await page.goto("/dashboard");

    // Act
    await page.getByRole("button", { name: workspaceName }).click();

    // Assert
    await expect(page.getByRole("menuitem", { name: "Settings" })).toBeVisible();
    await expect(page.getByRole("menuitem", { name: "Sign out" })).toBeVisible();
  });

  test("settings hide security internals", async ({ workspacePage: page }) => {
    // Act
    await page.goto("/dashboard/settings");

    // Assert
    await expect(page.getByRole("heading").first()).toBeVisible();
    await expect(page.getByText("CSRF protection")).toHaveCount(0);
    await expect(page.getByText("Browser access uses")).toHaveCount(0);
  });
});

test.describe("patients", () => {
  test("creating a patient lists them", async ({ workspacePage: page }) => {
    // Arrange
    const stamp = uniqueStamp();
    const patientName = `Demo Patient ${stamp}`;
    await page.goto("/dashboard/patients");
    await openDialog(page, "New patient", "Create patient");
    await page.getByLabel("Full name").fill(patientName);
    await page.getByLabel("Preferred name").fill("Demo");
    await page.getByLabel("Email").fill(`patient.${stamp}@example.com`);
    await page.getByLabel("Birth date").fill("1991-04-12");
    await page.getByLabel("Phone", { exact: true }).fill("+351912345678");
    await page.getByLabel("Emergency contact").fill("Emergency Contact");
    await page.getByLabel("Emergency phone").fill("+351923456789");
    await page.getByLabel("Relationship").fill("Partner");

    // Act
    await page.getByRole("button", { name: "Create patient" }).click();

    // Assert
    await expect(page.getByText(patientName).filter({ visible: true }).first()).toBeVisible();
  });

  test("search finds a patient by email", async ({ workspacePage: page }) => {
    // Arrange
    const patient = await createPatient(page);
    await page.goto("/dashboard/patients");

    // Act
    await page.getByPlaceholder("Search patients").fill(patient.email);

    // Assert
    await expect(page.getByText(patient.fullName).filter({ visible: true }).first()).toBeVisible();
  });

  test("keyboard selection applies the Active status filter", async ({ workspacePage: page }) => {
    // Arrange
    await createPatient(page);
    await page.goto("/dashboard/patients");
    const statusFilter = patientStatusFilter(page);
    await statusFilter.focus();
    await page.keyboard.press("ArrowDown");
    await highlightOption(page, page.getByRole("option", { name: "Active", exact: true }));

    // Act
    await page.keyboard.press("Enter");

    // Assert
    await expect(statusFilter).toContainText("Active");
  });

  test("Space opens the status filter options", async ({ workspacePage: page }) => {
    // Arrange
    await createPatient(page);
    await page.goto("/dashboard/patients");
    await patientStatusFilter(page).focus();

    // Act
    await page.keyboard.press("Space");

    // Assert
    await expect(page.getByRole("option", { name: "All statuses", exact: true })).toBeVisible();
  });

  test("Home highlights All statuses in the status filter", async ({ workspacePage: page }) => {
    // Arrange
    await createPatient(page);
    await page.goto("/dashboard/patients");
    await patientStatusFilter(page).focus();
    await page.keyboard.press("Space");
    await expect(page.getByRole("option", { name: "All statuses", exact: true })).toBeVisible();

    // Act
    await page.keyboard.press("Home");

    // Assert
    await expect(page.getByRole("option", { name: "All statuses", exact: true })).toHaveAttribute("data-highlighted", "");
  });

  test("choosing All statuses resets the status filter", async ({ workspacePage: page }) => {
    // Arrange
    await createPatient(page);
    await page.goto("/dashboard/patients");
    const statusFilter = patientStatusFilter(page);
    await statusFilter.focus();
    await page.keyboard.press("ArrowDown");
    await highlightOption(page, page.getByRole("option", { name: "Active", exact: true }));
    await page.keyboard.press("Enter");
    await expect(statusFilter).toContainText("Active");
    await statusFilter.focus();
    await page.keyboard.press("Space");
    // Wait for the listbox and the Home highlight; pressing keys before the popup opens loses them.
    const allStatuses = page.getByRole("option", { name: "All statuses", exact: true });
    await expect(allStatuses).toBeVisible();
    await page.keyboard.press("Home");
    await expect(allStatuses).toHaveAttribute("data-highlighted", "");

    // Act
    await page.keyboard.press("Enter");

    // Assert
    await expect(statusFilter).toContainText("All statuses");
  });

  test("patient actions open from the keyboard", async ({ workspacePage: page }) => {
    // Arrange
    await createPatient(page);
    await page.goto("/dashboard/patients");
    await page.getByRole("button", { name: "Patient actions" }).last().focus();

    // Act
    await page.keyboard.press("Enter");

    // Assert
    await expect(page.getByRole("menuitem", { name: "Archive" })).toBeVisible();
  });
});

test.describe("concurrent edits", () => {
  test("a stale patient edit keeps what was typed and explains why nothing was saved", async ({
    workspacePage: page,
  }) => {
    // Arrange
    const editor = await openStalePatientEditor(page);
    await editor.getByLabel("Full name").fill("Renamed in this tab");

    // Act
    await editor.getByRole("button", { name: "Save changes" }).click();

    // Assert
    await expect(editor.getByText("Changed elsewhere")).toBeVisible();
    await expect(editor.getByLabel("Full name")).toHaveValue("Renamed in this tab");
  });

  test("reloading a stale patient edit shows the latest saved details", async ({ workspacePage: page }) => {
    // Arrange
    const editor = await openStalePatientEditor(page);
    await editor.getByLabel("Full name").fill("Renamed in this tab");
    await editor.getByRole("button", { name: "Save changes" }).click();
    await expect(editor.getByText("Changed elsewhere")).toBeVisible();

    // Act
    await editor.getByRole("button", { name: "Reload latest" }).click();

    // Assert
    await expect(editor.getByLabel("Full name")).toHaveValue("Renamed in another tab");
  });
});

test.describe("calendar", () => {
  test("scheduling a session lists it as scheduled", async ({ workspacePage: page }) => {
    // Arrange
    const patient = await createPatient(page);
    await page.goto("/dashboard/appointments");
    await openDialog(page, "Schedule session", "Schedule session");
    const dialog = page.getByRole("dialog", { name: "Schedule session" });
    await choosePatient(page, dialog, patient.fullName);
    await page.getByLabel("Date and time").fill(futureDatetimeLocal());

    // Act
    await dialog.getByRole("button", { name: "Schedule session" }).click();

    // Assert
    await expect(page.getByText(patient.fullName).filter({ visible: true }).first()).toBeVisible();
    await expect(page.getByText("SCHEDULED").filter({ visible: true }).first()).toBeVisible();
  });
});

test.describe("clinical cases", () => {
  test("creating a case opens it in intake", async ({ workspacePage: page }) => {
    // Arrange
    const patient = await createPatient(page);
    const presentingConcern = `Initial stress and sleep disruption ${uniqueStamp()}`;
    await page.goto("/dashboard/clinical");
    await openDialog(page, "New clinical case", "Create clinical case");
    const dialog = page.getByRole("dialog", { name: "Create clinical case" });
    await choosePatient(page, dialog, patient.fullName);
    await page.getByLabel("Presenting concern").fill(presentingConcern);

    // Act
    await page.getByRole("button", { name: "Create case" }).click();

    // Assert
    await expect(page.getByText(presentingConcern).filter({ visible: true }).first()).toBeVisible();
    await expect(page.getByText("Intake").filter({ visible: true }).first()).toBeVisible();
  });

  test("case browser search finds a case by patient name", async ({ workspacePage: page }) => {
    // Arrange
    const patient = await createPatient(page);
    const clinicalCase = await createClinicalCase(page, patient);
    await page.goto("/dashboard/clinical");
    await page.getByRole("button", { name: "Browse cases" }).click();

    // Act
    await page.getByPlaceholder("Search this page by patient or concern").fill(patient.fullName);

    // Assert
    await expect(page.getByText(clinicalCase.presentingConcern).filter({ visible: true }).first()).toBeVisible();
  });

  test("keyboard selection applies the Intake case filter", async ({ workspacePage: page }) => {
    // Arrange
    const patient = await createPatient(page);
    await createClinicalCase(page, patient);
    await page.goto("/dashboard/clinical");
    await page.getByRole("button", { name: "Browse cases" }).click();
    const clinicalFilter = page.getByRole("combobox", { name: "Filter clinical cases by status" });
    await clinicalFilter.focus();
    await page.keyboard.press("ArrowDown");
    await expect(page.getByRole("option", { name: "All case statuses", exact: true })).toBeVisible();
    await highlightOption(page, page.getByRole("option", { name: "Intake", exact: true }));

    // Act
    await page.keyboard.press("Enter");

    // Assert
    await expect(clinicalFilter).toContainText("Intake");
  });

  test("the clinical workspace offers no bulk selection", async ({ workspacePage: page }) => {
    // Arrange
    const patient = await createPatient(page);
    const clinicalCase = await createClinicalCase(page, patient);

    // Act
    await page.goto("/dashboard/clinical");

    // Assert
    await expect(page.getByText(clinicalCase.presentingConcern).filter({ visible: true }).first()).toBeVisible();
    await expect(page.getByRole("checkbox")).toHaveCount(0);
  });

  test("saving a care plan shows its therapeutic focus", async ({ workspacePage: page }) => {
    // Arrange
    const patient = await createPatient(page);
    await createClinicalCase(page, patient);
    await page.goto("/dashboard/clinical");
    await openDialog(page, "Create", "Care plan", true);
    await page.getByLabel("Therapeutic focus").fill(therapeuticFocus);
    await page.getByLabel("Planned frequency").fill("Weekly");
    await page.getByLabel("Review date").fill("2026-08-15");
    await page.getByLabel("Goal description").fill("Improve sleep consistency");
    await page.getByLabel("Progress").fill("20");
    await page.getByRole("button", { name: "Add goal" }).click();
    await expect(page.getByText("Goal 2")).toBeVisible();
    await page.getByLabel("Goal description").nth(1).fill("Create a consistent wind-down routine");
    await page.getByLabel("Progress").nth(1).fill("0");

    // Act
    await page.getByRole("button", { name: "Save care plan" }).click();

    // Assert
    await expect(page.getByText(therapeuticFocus).filter({ visible: true }).first()).toBeVisible();
  });

  test("care plan tab shows goals without a status as not started", async ({ workspacePage: page }) => {
    // Arrange
    const patient = await createPatient(page);
    const clinicalCase = await createClinicalCase(page, patient);
    await putCarePlan(page, clinicalCase.id);
    await page.goto("/dashboard/clinical");

    // Act
    await page.getByRole("tab", { name: "Care plan" }).click();

    // Assert
    await expect(page.getByText("Not started").filter({ visible: true }).first()).toBeVisible();
  });

  test("objectives dialog lists every care-plan goal", async ({ workspacePage: page }) => {
    // Arrange
    const patient = await createPatient(page);
    const clinicalCase = await createClinicalCase(page, patient);
    await putCarePlan(page, clinicalCase.id);
    await page.goto("/dashboard/clinical");
    await page.getByRole("tab", { name: "Care plan" }).click();

    // Act
    await page.getByRole("button", { name: "View all 2 objectives" }).click();

    // Assert
    const objectives = page.getByRole("dialog", { name: "Care-plan objectives" });
    await expect(objectives).toContainText("Improve sleep consistency");
    await expect(objectives).toContainText("Create a consistent wind-down routine");
  });
});

test.describe("session records", () => {
  test("creating a session record shows its summary", async ({ workspacePage: page }) => {
    // Arrange
    const patient = await createPatient(page);
    await createClinicalCase(page, patient);
    await page.goto("/dashboard/clinical");
    await page.getByRole("tab", { name: "Session records" }).click();
    await openDialog(page, "New record", "New session record");
    await page.getByLabel("Session date").fill(futureDatetimeLocal());
    await page.getByLabel("Summary").fill(sessionSummary);

    // Act
    await page.getByRole("button", { name: "Create session record" }).click();

    // Assert
    await expect(page.getByRole("dialog", { name: "New session record" })).toBeHidden({ timeout: 30_000 });
    await expect(page.locator("main")).toContainText(sessionSummary, { timeout: 30_000 });
  });

  test("draft record actions offer editing and signing", async ({ workspacePage: page }) => {
    // Arrange
    await openSessionRecords(page, await draftRecord(page));

    // Act
    await page.getByRole("button", { name: "Session record actions" }).click();

    // Assert
    await expect(page.getByRole("menuitem", { name: "Edit" })).toBeVisible();
    await expect(page.getByRole("menuitem", { name: "Sign note" })).toBeVisible();
  });

  test("sign dialog warns that signed notes are locked", async ({ workspacePage: page }) => {
    // Arrange
    await openSessionRecords(page, await draftRecord(page));
    await page.getByRole("button", { name: "Session record actions" }).click();

    // Act
    await page.getByRole("menuitem", { name: "Sign note" }).click();

    // Assert
    const signDialog = page.getByRole("dialog", { name: "Sign session note?" });
    await expect(signDialog).toContainText("Signed notes can't be edited");
    await expect(signDialog).toContainText(sessionSummary);
  });

  test("confirming the signature shows the signer", async ({ workspacePage: page }) => {
    // Arrange
    await openSessionRecords(page, await draftRecord(page));
    await page.getByRole("button", { name: "Session record actions" }).click();
    await page.getByRole("menuitem", { name: "Sign note" }).click();
    const signDialog = page.getByRole("dialog", { name: "Sign session note?" });

    // Act
    await signDialog.getByRole("button", { name: "Sign note" }).click();

    // Assert
    await expect(signDialog).toBeHidden({ timeout: 30_000 });
    await expect(page.locator("main")).toContainText(`Signed by ${workspaceName}`, { timeout: 30_000 });
  });

  test("signed record actions are read-only", async ({ workspacePage: page }) => {
    // Arrange
    await openSessionRecords(page, await signedRecord(page));

    // Act
    await page.getByRole("button", { name: "Session record actions" }).click();

    // Assert
    await expect(page.getByRole("menuitem", { name: "View note" })).toBeVisible();
    await expect(page.getByRole("menuitem", { name: "Edit" })).toHaveCount(0);
    await expect(page.getByRole("menuitem", { name: "Sign note" })).toHaveCount(0);
  });

  test("a signed note opens read-only", async ({ workspacePage: page }) => {
    // Arrange
    await openSessionRecords(page, await signedRecord(page));
    await page.getByRole("button", { name: "Session record actions" }).click();

    // Act
    await page.getByRole("menuitem", { name: "View note" }).click();

    // Assert
    const noteDialog = page.getByRole("dialog", { name: "Session note" });
    await expect(noteDialog).toContainText(sessionSummary);
    await expect(noteDialog).toContainText("read-only");
    await expect(noteDialog.getByRole("textbox")).toHaveCount(0);
    await expect(noteDialog.getByRole("combobox")).toHaveCount(0);
  });

  test("Escape closes the signed note", async ({ workspacePage: page }) => {
    // Arrange
    await openSessionRecords(page, await signedRecord(page));
    await page.getByRole("button", { name: "Session record actions" }).click();
    await page.getByRole("menuitem", { name: "View note" }).click();
    const noteDialog = page.getByRole("dialog", { name: "Session note" });
    await expect(noteDialog).toBeVisible();

    // Act
    await page.keyboard.press("Escape");

    // Assert
    await expect(noteDialog).toBeHidden();
  });

  test("clinical history shows the plan, the note and its signature", async ({ workspacePage: page }) => {
    // Arrange
    const { patient } = await signedRecord(page, { withCarePlan: true });

    // Act
    await page.goto(`/dashboard/patients/${patient.id}/clinical-history`);

    // Assert
    await expect(page.getByRole("heading", { name: "Clinical history" })).toBeVisible();
    await expect(page.locator("main")).toContainText(therapeuticFocus);
    await expect(page.locator("main")).toContainText(sessionSummary);
    await expect(page.locator("main")).toContainText("Signed by the treating psychologist");
  });

  test("the BFF rejects edits to a signed note", async ({ workspacePage: page }) => {
    // Arrange
    const { record } = await signedRecord(page);
    const csrf = await csrfToken(page);

    // Act
    const response = await page.request.patch(`/api/session-records/${record.id}`, {
      headers: { "x-csrf-token": csrf, "if-match": `"${record.version}"` },
      data: {
        sessionDate: record.sessionDate,
        modality: record.modality,
        durationMinutes: record.durationMinutes,
        attendanceStatus: record.attendanceStatus,
        noteStatus: "DRAFT",
        summary: "Rewritten after signing",
      },
    });

    // Assert
    expect(response.status()).toBe(409);
    expect((await response.json()).code).toBe("session_record_signed");
  });
});

test.describe("messages", () => {
  test("list patient and appointment notifications", async ({ workspacePage: page }) => {
    // Arrange
    const patient = await createPatient(page);
    await scheduleAppointment(page, patient);

    // Act
    await page.goto("/dashboard/notifications");

    // Assert
    await expect(page.locator("main")).toContainText("Patient profile created");
    await expect(page.locator("main")).toContainText("Appointment scheduled");
  });
});

function requestOptions(use: { baseURL?: string; ignoreHTTPSErrors?: boolean }) {
  return { baseURL: use.baseURL, ignoreHTTPSErrors: use.ignoreHTTPSErrors };
}

async function arrangeStep(response: APIResponse, expectedStatus: number, step: string) {
  if (response.status() !== expectedStatus) {
    throw new Error(`Arrange step "${step}" returned ${response.status()}, expected ${expectedStatus}`);
  }
  return response;
}

async function addStaleAccessCookie(context: BrowserContext, baseURL?: string) {
  if (!baseURL) throw new Error('Arrange step "read base URL" found no configured baseURL');
  await context.addCookies([
    {
      name: "clinicflow_access_token",
      value: "stale-token",
      url: new URL(baseURL).origin,
      httpOnly: true,
      sameSite: "Lax",
    },
  ]);
}

async function csrfToken(page: Page) {
  const csrf = (await page.context().cookies()).find((cookie) => cookie.name === "clinicflow_csrf")?.value;
  if (!csrf) throw new Error('Arrange step "read CSRF cookie" found no clinicflow_csrf cookie');
  return csrf;
}

async function bffPost(
  page: Page,
  path: string,
  data: unknown,
  expectedStatus: number,
  step: string,
  method = "POST",
  headers: Record<string, string> = {}
) {
  const response = await page.request.fetch(path, {
    method,
    headers: { "x-csrf-token": await csrfToken(page), ...headers },
    data,
  });
  return (await arrangeStep(response, expectedStatus, step)).json();
}

function uniqueStamp() {
  return `${Date.now()}${Math.floor(Math.random() * 1_000)}`;
}

async function createPatient(page: Page): Promise<Patient> {
  const stamp = uniqueStamp();
  const fullName = `Demo Patient ${stamp}`;
  const email = `patient.${stamp}@example.com`;
  const created = await bffPost(page, "/api/patients", {
    email,
    fullName,
    phone: "+351912345678",
    contactPreference: "EMAIL",
    consentStatus: "GRANTED",
  }, 201, "create patient");
  return { id: created.id, fullName, email, version: created.version };
}

async function scheduleAppointment(page: Page, patient: Patient) {
  return bffPost(page, "/api/appointments", {
    patientId: patient.id,
    scheduledAt: new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString(),
    type: "ONLINE",
  }, 201, "schedule appointment");
}

async function createClinicalCase(page: Page, patient: Patient) {
  const presentingConcern = `Initial stress and sleep disruption ${uniqueStamp()}`;
  const created = await bffPost(page, "/api/clinical-cases", {
    patientId: patient.id,
    presentingConcern,
  }, 201, "create clinical case");
  return { id: created.id as string, presentingConcern };
}

async function putCarePlan(page: Page, caseId: string) {
  return bffPost(page, `/api/clinical-cases/${caseId}/care-plan`, {
    therapeuticFocus,
    plannedFrequency: "Weekly",
    reviewDate: "2026-08-15",
    goals: [
      { description: "Improve sleep consistency", progressPercentage: 20 },
      { description: "Create a consistent wind-down routine", progressPercentage: 0 },
    ],
  }, 200, "put care plan", "PUT", { "if-none-match": "*" });
}

async function draftRecord(page: Page, options: { withCarePlan?: boolean } = {}) {
  const patient = await createPatient(page);
  const clinicalCase = await createClinicalCase(page, patient);
  if (options.withCarePlan) await putCarePlan(page, clinicalCase.id);
  const record: SessionRecord = await bffPost(page, `/api/clinical-cases/${clinicalCase.id}/session-records`, {
    sessionDate: new Date().toISOString(),
    modality: "ONLINE",
    durationMinutes: 50,
    attendanceStatus: "ATTENDED",
    summary: sessionSummary,
  }, 201, "create session record");
  return { patient, clinicalCase, record };
}

async function signedRecord(page: Page, options: { withCarePlan?: boolean } = {}) {
  const arranged = await draftRecord(page, options);
  const signed: SessionRecord = await bffPost(page, `/api/session-records/${arranged.record.id}/sign`, undefined,
    200, "sign session record", "POST", { "if-match": `"${arranged.record.version}"` });
  return { ...arranged, record: signed };
}

// Opens the edit sheet, then saves a change to the same patient as if from another tab.
async function openStalePatientEditor(page: Page) {
  const patient = await createPatient(page);
  await page.goto("/dashboard/patients");
  await page.getByPlaceholder("Search patients").fill(patient.email);
  await page.getByRole("button", { name: "Patient actions" }).filter({ visible: true }).first().click();
  await page.getByRole("menuitem", { name: "Edit" }).click();
  const editor = page.getByRole("dialog", { name: "Edit patient" });
  await expect(editor.getByLabel("Full name")).toHaveValue(patient.fullName);
  await bffPost(page, `/api/patients/${patient.id}`, {
    fullName: "Renamed in another tab",
    phone: "+351912345678",
    contactPreference: "EMAIL",
    consentStatus: "REVOKED",
  }, 200, "edit the patient in another tab", "PUT", { "if-match": `"${patient.version}"` });
  return editor;
}

// The newest case is selected by default, so a freshly arranged case opens in the workspace.
async function openSessionRecords(page: Page, arranged: { clinicalCase: { presentingConcern: string } }) {
  await page.goto("/dashboard/clinical");
  await expect(page.getByText(arranged.clinicalCase.presentingConcern).filter({ visible: true }).first()).toBeVisible();
  await page.getByRole("tab", { name: "Session records" }).click();
}

function patientStatusFilter(page: Page) {
  return page.getByRole("combobox", { name: "Filter patients by status" });
}

async function highlightOption(page: Page, option: Locator) {
  await expect(option).toBeVisible();
  await expect.poll(async () => {
    if (await option.getAttribute("data-highlighted") !== null) return true;
    await page.keyboard.press("ArrowDown");
    return false;
  }, { timeout: 5_000 }).toBe(true);
}

async function choosePatient(page: Page, dialog: Locator, patientName: string) {
  await dialog.getByRole("combobox").first().click();
  await page.getByRole("option", { name: patientName, exact: true }).click();
}

async function openDialog(page: Page, triggerName: string, dialogName: string, exact = false) {
  const dialog = page.getByRole("dialog", { name: dialogName });
  await expect
    .poll(
      async () => {
        if (await dialog.isVisible()) return true;
        await page.getByRole("button", { name: triggerName, exact }).click();
        return dialog.isVisible();
      },
      { timeout: 15_000 }
    )
    .toBe(true);
}

function futureDatetimeLocal() {
  const value = new Date(Date.now() + 24 * 60 * 60 * 1000);
  value.setMinutes(value.getMinutes() - value.getTimezoneOffset());
  return value.toISOString().slice(0, 16);
}
