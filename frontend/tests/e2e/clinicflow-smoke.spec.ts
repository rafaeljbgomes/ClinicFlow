import { expect, test, type Page } from "@playwright/test";

test("session routing remains safe for missing and stale cookies", async ({ context, page }) => {
  await page.goto("/dashboard");
  await expect(page).toHaveURL(/\/login$/);
  const runtimeOrigin = new URL(page.url()).origin;

  await context.addCookies([
    {
      name: "clinicflow_access_token",
      value: "stale-token",
      url: runtimeOrigin,
      httpOnly: true,
      sameSite: "Lax",
    },
  ]);

  await page.goto("/login");
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByRole("button", { name: "Sign in" })).toBeVisible();

  await page.goto("/dashboard");
  await expect(page).toHaveURL(/\/login$/);
});

test("psychologist can run the main prototype workflow", async ({ page }) => {
  test.setTimeout(180_000);

  const stamp = Date.now();
  const email = `psychologist.${stamp}@example.com`;
  const password = "TestingPass123!";
  const patientName = `Demo Patient ${stamp}`;
  const patientEmail = `patient.${stamp}@example.com`;
  const presentingConcern = "Initial stress and sleep disruption";
  const therapeuticFocus = "Stabilize routines and reduce acute stress triggers.";
  const sessionSummary = "Reviewed weekly routine and agreed on sleep hygiene homework.";

  await page.goto("/register");
  await page.getByLabel("Full name").fill("ClinicFlow Demo Psychologist");
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password").fill(password);
  await page.getByRole("button", { name: "Create account" }).click();

  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.getByRole("heading", { name: "Today" })).toBeVisible({
    timeout: 30_000,
  });
  await expect(page.getByRole("link", { name: "System" })).toHaveCount(0);

  await page.getByRole("link", { name: "Patients" }).click();
  await openDialog(page, "New patient", "Create patient");
  await page.getByLabel("Full name").fill(patientName);
  await page.getByLabel("Preferred name").fill("Demo");
  await page.getByLabel("Email").fill(patientEmail);
  await page.getByLabel("Birth date").fill("1991-04-12");
  await page.getByLabel("Phone", { exact: true }).fill("+351912345678");
  await page.getByLabel("Emergency contact").fill("Emergency Contact");
  await page.getByLabel("Emergency phone").fill("+351923456789");
  await page.getByLabel("Relationship").fill("Partner");
  await page.getByRole("button", { name: "Create patient" }).click();
  await expect(page.getByText(patientName).filter({ visible: true }).first()).toBeVisible();
  await page.getByPlaceholder("Search patients").fill(patientEmail);
  await expect(page.getByText(patientName).filter({ visible: true }).first()).toBeVisible();
  await page.getByPlaceholder("Search patients").clear();

  await page.getByRole("link", { name: "Calendar" }).click();
  await openDialog(page, "Schedule session", "Schedule session");
  await page.getByLabel("Date and time").fill(futureDatetimeLocal());
  await page.getByRole("button", { name: "Schedule session" }).click();
  await expect(page.getByText(patientName).filter({ visible: true }).first()).toBeVisible();
  await expect(page.getByText("SCHEDULED").filter({ visible: true }).first()).toBeVisible();

  await page.getByRole("link", { name: "Clinical" }).click();
  await openDialog(page, "New clinical case", "Create clinical case");
  await page.getByLabel("Presenting concern").fill(presentingConcern);
  await page.getByRole("button", { name: "Create case" }).click();
  await expect(page.getByText(presentingConcern).filter({ visible: true }).first()).toBeVisible();
  await expect(page.getByText("INTAKE").filter({ visible: true }).first()).toBeVisible();

  await openDialog(page, "Create", "Care plan", true);
  await page.getByLabel("Therapeutic focus").fill(therapeuticFocus);
  await page.getByLabel("Planned frequency").fill("Weekly");
  await page.getByLabel("Review date").fill("2026-08-15");
  await page.getByLabel("Goal description").fill("Improve sleep consistency");
  await page.getByLabel("Progress").fill("20");
  await page.getByRole("button", { name: "Save care plan" }).click();
  await expect(page.getByText(therapeuticFocus).filter({ visible: true }).first()).toBeVisible();

  await openDialog(page, "New record", "New session record");
  await page.getByLabel("Session date").fill(futureDatetimeLocal());
  await page.getByLabel("Summary").fill(sessionSummary);
  await page.getByRole("button", { name: "Create session record" }).click();
  await expect(page.getByRole("dialog", { name: "New session record" })).toBeHidden({
    timeout: 30_000,
  });
  await expect(page.locator("main")).toContainText(sessionSummary, { timeout: 30_000 });

  const historyHref = await page
    .locator('a[href$="/clinical-history"]')
    .first()
    .getAttribute("href");
  expect(historyHref).toBeTruthy();
  await page.goto(historyHref!);
  await expect(page.getByRole("heading", { name: "Clinical history" })).toBeVisible();
  await expect(page.locator("main")).toContainText(therapeuticFocus);
  await expect(page.locator("main")).toContainText(sessionSummary);

  await page.getByRole("link", { name: "Messages" }).click();
  await expect(page.locator("main")).toContainText("Patient profile created");
  await expect(page.locator("main")).toContainText("Appointment scheduled");
});

async function openDialog(
  page: Page,
  triggerName: string,
  dialogName: string,
  exact = false
) {
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
