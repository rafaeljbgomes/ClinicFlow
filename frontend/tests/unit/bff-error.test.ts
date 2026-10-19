import { describe, expect, it } from "vitest";
import { sanitizeProblemFields } from "@/lib/server/bff";

describe("sanitizeProblemFields", () => {
  it("preserves only the agreed safe ProblemDetail extensions", () => {
    // Arrange
    const problem = {
      code: "validation_failed",
      correlationId: "request-123",
      fieldErrors: { email: ["Must be a valid email"] },
      stack: "sensitive trace",
    };

    // Act
    const sanitized = sanitizeProblemFields(problem);

    // Assert
    expect(sanitized).toEqual({
      code: "validation_failed",
      correlationId: "request-123",
      fieldErrors: { email: ["Must be a valid email"] },
    });
  });

  it("drops malformed or unsafe extension values", () => {
    // Arrange
    const problem = {
      code: "../../unsafe",
      correlationId: "x".repeat(129),
      fieldErrors: { "bad field!": ["message"] },
    };

    // Act
    const sanitized = sanitizeProblemFields(problem);

    // Assert
    expect(sanitized).toEqual({});
  });
});
