import { NextRequest } from "next/server";
import { describe, expect, it } from "vitest";
import { forwardedQuery, rejectUnknownParameters, unknownParameters } from "@/lib/server/list-query";
import { projectNotifications } from "@/lib/server/notification-projection";

describe("forwardedQuery", () => {
  it("forwards allowlisted parameters, including repeated ones", () => {
    // Arrange
    const request = new NextRequest("http://localhost/api/notifications?page=2&status=PENDING&status=FAILED");

    // Act
    const query = forwardedQuery(request, ["page", "size", "status"]);

    // Assert
    expect(query).toBe("?page=2&status=PENDING&status=FAILED");
  });

  it("drops parameters the route does not allow", () => {
    // Arrange
    const request = new NextRequest("http://localhost/api/appointments?size=5&psychologistId=someone-else");

    // Act
    const query = forwardedQuery(request, ["size"]);

    // Assert
    expect(query).toBe("?size=5");
  });

  it("returns an empty string when nothing is forwarded", () => {
    // Arrange
    const request = new NextRequest("http://localhost/api/appointments?debug=1");

    // Act
    const query = forwardedQuery(request, ["page"]);

    // Assert
    expect(query).toBe("");
  });
});

describe("unknownParameters", () => {
  it("names each parameter the route does not allow once", () => {
    // Arrange
    const request = new NextRequest("http://localhost/api/notifications?page=1&query=a&query=b&debug=1");

    // Act
    const unknown = unknownParameters(request, ["page", "size", "status"]);

    // Assert
    expect(unknown).toEqual(["query", "debug"]);
  });

  it("finds nothing when every parameter is allowed", () => {
    // Arrange
    const request = new NextRequest("http://localhost/api/notifications?page=1&status=SENT&status=FAILED");

    // Act
    const unknown = unknownParameters(request, ["page", "size", "status"]);

    // Assert
    expect(unknown).toEqual([]);
  });
});

describe("rejectUnknownParameters", () => {
  it("lets a request with only documented parameters through", () => {
    // Arrange
    const request = new NextRequest("http://localhost/api/appointments?from=2026-11-01T00%3A00%3A00Z&size=5");

    // Act
    const rejected = rejectUnknownParameters(request, ["from", "to", "size"]);

    // Assert
    expect(rejected).toBeNull();
  });

  it("answers an undocumented parameter with a 400 that does not echo it", async () => {
    // Arrange
    const request = new NextRequest("http://localhost/api/notifications?probe7=%3Cscript%3E");

    // Act
    const rejected = rejectUnknownParameters(request, ["page", "size", "status"]);

    // Assert
    expect(rejected?.status).toBe(400);
    const body = await rejected?.text();
    expect(JSON.parse(body ?? "{}")).toMatchObject({ status: 400, code: "unsupported_parameter" });
    expect(body).not.toContain("probe7");
    expect(body).not.toContain("script");
  });

  it("gives the same answer whatever the undocumented parameter holds", async () => {
    // Arrange
    const allowed = ["page", "size", "status"];
    const first = new NextRequest("http://localhost/api/notifications?query=query%27+AND+%271%27%3D%271%27+--+");
    const second = new NextRequest("http://localhost/api/notifications?query=query%27+OR+%271%27%3D%271%27+--+");

    // Act
    const answers = await Promise.all([first, second].map((request) => rejectUnknownParameters(request, allowed)?.text()));

    // Assert
    expect(answers[0]).toBe(answers[1]);
  });
});

describe("projectNotifications", () => {
  const page = { size: 20, number: 0, totalElements: 1, totalPages: 1 };
  const delivery = {
    id: "notification-1",
    eventId: "event-1",
    eventType: "PatientCreated",
    psychologistId: "psychologist-1",
    type: "EMAIL",
    status: "SENT",
    recipient: "patient@example.com",
    subject: "Patient profile created",
    createdAt: "2026-09-10T08:00:00.000Z",
    sentAt: "2026-09-10T08:00:00.000Z",
  };

  it("strips delivery metadata from each item for psychologists and keeps the page", () => {
    // Act
    const projected = projectNotifications({ content: [delivery], page }, { role: "PSYCHOLOGIST" });

    // Assert
    expect(projected).toEqual({
      content: [{
        id: "notification-1",
        eventType: "PatientCreated",
        type: "EMAIL",
        status: "SENT",
        subject: "Patient profile created",
        createdAt: "2026-09-10T08:00:00.000Z",
        sentAt: "2026-09-10T08:00:00.000Z",
      }],
      page,
    });
  });

  it("keeps the full payload for administrators", () => {
    // Arrange
    const payload = { content: [delivery], page };

    // Act
    const projected = projectNotifications(payload, { role: "ADMIN" });

    // Assert
    expect(projected).toBe(payload);
  });

  it.each([[[delivery]], [{ unexpected: true }], [null]])(
    "withholds payloads that are not a page for psychologists: %j",
    (payload) => {
      // Act
      const projected = projectNotifications(payload, { role: "PSYCHOLOGIST" });

      // Assert
      expect(projected).toEqual({ content: [], page: { size: 0, number: 0, totalElements: 0, totalPages: 0 } });
    }
  );
});
