import { describe, expect, it } from "vitest";
import { NextRequest } from "next/server";
import { proxy } from "../../proxy";

describe("session proxy", () => {
  it("redirects a protected request to login when the cookie is absent", () => {
    // Arrange
    const request = new NextRequest("http://localhost/dashboard");

    // Act
    const response = proxy(request);

    // Assert
    expect(response.status).toBe(307);
    expect(response.headers.get("location")).toBe("http://localhost/login");
  });

  it("allows a stale cookie to reach protected routes for authoritative validation", () => {
    // Arrange
    const request = new NextRequest("http://localhost/dashboard", {
      headers: { cookie: "clinicflow_access_token=stale" },
    });

    // Act
    const response = proxy(request);

    // Assert
    expect(response.headers.get("x-middleware-next")).toBe("1");
  });

  it("never redirects public authentication pages based on cookie presence", () => {
    // Arrange
    const request = new NextRequest("http://localhost/login", {
      headers: { cookie: "clinicflow_access_token=stale" },
    });

    // Act
    const response = proxy(request);

    // Assert
    expect(response.headers.get("location")).toBeNull();
  });
});
