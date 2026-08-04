import { expect, test } from "@playwright/test";

test("psychologist can run the main prototype workflow", async ({ page }) => {
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
  await expect(page.getByRole("heading", { name: "Today" })).toBeVisible();
  await expect(page.getByRole("link", { name: "System" })).toHaveCount(0);

  await page.getByRole("link", { name: "Patients" }).click();
  await page.getByRole("button", { name: "New patient" }).click();
  await page.getByLabel("Full name").fill(patientName);
  await page.getByLabel("Preferred name").fill("Demo");
  await page.getByLabel("Email").fill(patientEmail);
  await page.getByLabel("Birth date").fill("1991-04-12");
  await page.getByLabel("Phone").fill("+351912345678");
  await page.getByLabel("Emergency contact").fill("Emergency Contact");
  await page.getByLabel("Emergency phone").fill("+351923456789");
  await page.getByLabel("Relationship").fill("Partner");
  await page.getByRole("button", { name: "Create patient" }).click();
  await expect(page.getByText(patientName)).toBeVisible();
  await page.getByPlaceholder("Search patients").fill(patientEmail);
  await expect(page.getByText(patientName)).toBeVisible();
  await page.getByPlaceholder("Search patients").clear();

  await page.getByRole("link", { name: "Calendar" }).click();
  await page.getByRole("button", { name: "Schedule session" }).click();
  await page.getByLabel("Date and time").fill(futureDatetimeLocal());
  await page.getByRole("button", { name: "Schedule session" }).click();
  await expect(page.getByText(patientName)).toBeVisible();
  await expect(page.getByText("SCHEDULED")).toBeVisible();

  await page.getByRole("link", { name: "Clinical" }).click();
  await page.getByRole("button", { name: "New clinical case" }).click();
  await page.getByLabel("Presenting concern").fill(presentingConcern);
  await page.getByRole("button", { name: "Create case" }).click();
  await expect(page.getByText(presentingConcern)).toBeVisible();
  await expect(page.getByText("INTAKE")).toBeVisible();

  await page.getByRole("button", { name: "Create" }).click();
  await page.getByLabel("Therapeutic focus").fill(therapeuticFocus);
  await page.getByLabel("Planned frequency").fill("Weekly");
  await page.getByLabel("Review date").fill("2026-08-15");
  await page.getByLabel("Goal description").fill("Improve sleep consistency");
  await page.getByLabel("Progress").fill("20");
  await page.getByRole("button", { name: "Save care plan" }).click();
  await expect(page.getByText(therapeuticFocus)).toBeVisible();

  await page.getByRole("button", { name: "New record" }).click();
  await page.getByLabel("Session date").fill(futureDatetimeLocal());
  await page.getByLabel("Summary").fill(sessionSummary);
  await page.getByRole("button", { name: "Create session record" }).click();
  await expect(page.getByText(sessionSummary)).toBeVisible();

  await page.getByRole("link", { name: "Patient history" }).click();
  await expect(page.getByRole("heading", { name: "Clinical history" })).toBeVisible();
  await expect(page.getByText(therapeuticFocus)).toBeVisible();
  await expect(page.getByText(sessionSummary)).toBeVisible();

  await page.getByRole("link", { name: "Messages" }).click();
  await expect(page.getByText("Patient Created").last()).toBeVisible();
  await expect(page.getByText("Appointment Scheduled").last()).toBeVisible();
});

function futureDatetimeLocal() {
  const value = new Date(Date.now() + 24 * 60 * 60 * 1000);
  value.setMinutes(value.getMinutes() - value.getTimezoneOffset());
  return value.toISOString().slice(0, 16);
}
