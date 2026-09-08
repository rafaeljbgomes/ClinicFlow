import path from "node:path";
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
        env: {
          ...process.env,
          CLINICFLOW_SECURITY_JWT_ISSUER: process.env.CLINICFLOW_SECURITY_JWT_ISSUER ?? "clinicflow-auth",
          CLINICFLOW_SECURITY_JWT_PUBLIC_KEY_PATH:
            process.env.CLINICFLOW_SECURITY_JWT_PUBLIC_KEY_PATH ??
            path.resolve(import.meta.dirname, "../secrets/jwt-public.pem"),
        },
        reuseExistingServer: true,
        timeout: 120_000,
      },
});
