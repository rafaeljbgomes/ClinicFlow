import { NextRequest } from "next/server";
import { describe, expect, it } from "vitest";
import { forwardedQuery } from "@/lib/server/list-query";
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
