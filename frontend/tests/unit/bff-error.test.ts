import { describe, expect, it } from "vitest";
import { sanitizeProblemFields } from "@/lib/server/bff";

describe("sanitizeProblemFields", () => {
  it("preserves only the agreed safe ProblemDetail extensions", () => {
    expect(
      sanitizeProblemFields({
        code: "validation_failed",
        correlationId: "request-123",
        fieldErrors: { email: ["Must be a valid email"] },
        stack: "sensitive trace",
      })
    ).toEqual({
      code: "validation_failed",
      correlationId: "request-123",
      fieldErrors: { email: ["Must be a valid email"] },
    });
  });

  it("drops malformed or unsafe extension values", () => {
    expect(
      sanitizeProblemFields({
        code: "../../unsafe",
        correlationId: "x".repeat(129),
        fieldErrors: { "bad field!": ["message"] },
      })
    ).toEqual({});
  });
});
