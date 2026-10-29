import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  testDir: "./tests/e2e",
  timeout: 60_000,
  expect: {
    timeout: 10_000,
  },
  use: {
    baseURL: process.env.PLAYWRIGHT_BASE_URL ?? "http://localhost:3000",
    trace: "retain-on-failure",
    // Local CA trust is verified independently by gateway contract tests; this opt-in only affects the test browser.
    ignoreHTTPSErrors: process.env.PLAYWRIGHT_IGNORE_HTTPS_ERRORS === "true",
  },
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
  ],
  webServer: process.env.PLAYWRIGHT_SKIP_WEBSERVER
    ? undefined
    : {
        command: "npm run dev",
        url: process.env.PLAYWRIGHT_BASE_URL ?? "http://localhost:3000",
        // A host-run BFF against the Compose Keycloak (ADR-027); the client secret comes from
        // CLINICFLOW_BFF_CLIENT_SECRET in the environment.
        env: {
          ...process.env,
          CLINICFLOW_OIDC_ISSUER: process.env.CLINICFLOW_OIDC_ISSUER ?? "http://localhost:8180/realms/clinicflow",
          CLINICFLOW_PUBLIC_URL: process.env.CLINICFLOW_PUBLIC_URL ?? "http://localhost:3000",
        },
        reuseExistingServer: true,
        timeout: 120_000,
      },
});
