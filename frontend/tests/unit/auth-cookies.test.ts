import { afterEach, describe, expect, it, vi } from "vitest";

import { csrfCookieDefaults, secureCookieDefaults } from "@/lib/server/auth-cookies";

describe.each([
  ["session cookie", secureCookieDefaults],
  ["CSRF cookie", csrfCookieDefaults],
] as const)("%s security", (_cookie, cookieDefaults) => {
  afterEach(() => vi.unstubAllEnvs());

  it("is secure by default in production", () => {
    // Arrange
    vi.stubEnv("NODE_ENV", "production");
    vi.stubEnv("CLINICFLOW_AUTH_COOKIE_SECURE", "");

    // Act
    const defaults = cookieDefaults();

    // Assert
    expect(defaults.secure).toBe(true);
  });

  it("allows an explicit insecure override for loopback-only HTTP validation", () => {
    // Arrange
    vi.stubEnv("NODE_ENV", "production");
    vi.stubEnv("CLINICFLOW_AUTH_COOKIE_SECURE", "false");

    // Act
    const defaults = cookieDefaults();

    // Assert
    expect(defaults.secure).toBe(false);
  });
});
