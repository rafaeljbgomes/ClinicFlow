import { expect, test, type Page } from "@playwright/test";

// The clinicflow login theme (docker/keycloak/themes) as a browser renders it: Keycloak's pages must
// read as the app's /login and /register pages, in its colours and fonts, served by Keycloak alone.
const primary = "rgb(23, 75, 58)";
const danger = "rgb(163, 50, 40)";

async function openSignIn(page: Page) {
  await page.goto("/api/auth/login");
  await expect(page.locator("#kc-form-login")).toBeVisible();
}

async function openRegistration(page: Page) {
  await page.goto("/register");
  await page.locator('a[href="/api/auth/register"]').click();
  await expect(page.locator("#kc-register-form")).toBeVisible();
}

test.describe("Keycloak login theme", () => {
  test("the sign-in page shows the app's brand panel and card", async ({ page }) => {
    // Arrange
    await page.setViewportSize({ width: 1280, height: 800 });

    // Act
    await openSignIn(page);

    // Assert
    await expect(page.getByRole("heading", { level: 1, name: "Sign in" })).toBeVisible();
    await expect(page.locator("#cf-page-subtitle")).toHaveText("Sign in to manage your practice with clarity.");
    await expect(page.getByText("Care, brought into focus.")).toBeVisible();
  });

  test("the sign-in button uses the app's primary colour", async ({ page }) => {
    // Arrange
    await openSignIn(page);

    // Act
    const background = await page.locator("#kc-login").evaluate((button) => getComputedStyle(button).backgroundColor);

    // Assert
    expect(background).toBe(primary);
  });

  test("the sign-in page is set in the app's font", async ({ page }) => {
    // Arrange
    await openSignIn(page);

    // Act
    const loaded = await page.evaluate(async () => {
      await document.fonts.ready;
      return document.fonts.check('16px "Instrument Sans"') && getComputedStyle(document.body).fontFamily.startsWith('"Instrument Sans"');
    });

    // Assert
    expect(loaded).toBe(true);
  });

  test("the sign-in page loads nothing from other origins", async ({ page }) => {
    // Arrange
    await openSignIn(page);
    await page.evaluate(() => document.fonts.ready);

    // Act
    const foreign = await page.evaluate(() =>
      performance
        .getEntriesByType("resource")
        .map((entry) => entry.name)
        .filter((url) => new URL(url).origin !== location.origin),
    );

    // Assert
    expect(foreign).toEqual([]);
  });

  test("a failed sign-in shows its error in the app's danger colour", async ({ page }) => {
    // Arrange: an address with no account, so brute-force protection counts against no one.
    await openSignIn(page);
    await page.locator("#username").fill(`unknown.${Date.now()}@example.test`);
    await page.locator("#password").fill("NotTheRightPassword!1");

    // Act
    await page.locator("#kc-login").click();

    // Assert
    const error = page.locator("#input-error-username");
    await expect(error).toBeVisible();
    await expect(error).toHaveCSS("color", danger);
  });

  test("the registration page uses the app's wording", async ({ page }) => {
    // Arrange
    await page.setViewportSize({ width: 1280, height: 800 });

    // Act
    await openRegistration(page);

    // Assert
    await expect(page.getByRole("heading", { level: 1, name: "Create a patient account" })).toBeVisible();
    await expect(page.locator("#kc-form-buttons input[type=submit]")).toHaveValue("Create account");
    await expect(page.getByRole("link", { name: "Back to sign in" })).toBeVisible();
  });

  test("on a phone the card stands alone and the page does not scroll sideways", async ({ page }) => {
    // Arrange
    await page.setViewportSize({ width: 375, height: 812 });

    // Act
    await openRegistration(page);

    // Assert
    await expect(page.locator("#kc-header")).toBeHidden();
    await expect(page.locator(".cf-card-eyebrow")).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  });
});
