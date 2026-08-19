import { describe, expect, it } from "vitest";
import { NextRequest } from "next/server";
import { proxy } from "../../proxy";

describe("session proxy", () => {
  it("redirects a protected request only when the cookie is absent", () => {
    const response = proxy(new NextRequest("http://localhost/dashboard"));
    expect(response.status).toBe(307);
    expect(response.headers.get("location")).toBe("http://localhost/login");
  });

  it("allows a stale cookie to reach protected routes for authoritative validation", () => {
    const request = new NextRequest("http://localhost/dashboard", {
      headers: { cookie: "clinicflow_access_token=stale" },
    });
    expect(proxy(request).headers.get("x-middleware-next")).toBe("1");
  });

  it("never redirects public authentication pages based on cookie presence", () => {
    const request = new NextRequest("http://localhost/login", {
      headers: { cookie: "clinicflow_access_token=stale" },
    });
    expect(proxy(request).headers.get("location")).toBeNull();
  });
});
