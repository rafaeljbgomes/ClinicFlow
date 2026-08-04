import { afterEach, describe, expect, it, vi } from "vitest";

import { csrfCookieDefaults, secureCookieDefaults } from "@/lib/server/auth-cookies";

describe("auth cookie security", () => {
  afterEach(() => vi.unstubAllEnvs());

  it("uses secure cookies by default in production", () => {
    vi.stubEnv("NODE_ENV", "production");
    vi.stubEnv("CLINICFLOW_AUTH_COOKIE_SECURE", "");

    expect(secureCookieDefaults().secure).toBe(true);
    expect(csrfCookieDefaults().secure).toBe(true);
  });

  it("allows an explicit insecure override for loopback-only HTTP validation", () => {
    vi.stubEnv("NODE_ENV", "production");
    vi.stubEnv("CLINICFLOW_AUTH_COOKIE_SECURE", "false");

    expect(secureCookieDefaults().secure).toBe(false);
    expect(csrfCookieDefaults().secure).toBe(false);
  });
});
