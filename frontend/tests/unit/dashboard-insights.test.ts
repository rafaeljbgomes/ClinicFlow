import { describe, expect, it } from "vitest";
import { deriveFollowUps, practiceMetrics } from "@/lib/dashboard-insights";
import type { Appointment, ClinicalCase, Notification, Patient } from "@/lib/types";

const now = new Date("2026-09-10T09:00:00.000Z").getTime();

function patient(overrides: Partial<Patient> = {}): Patient {
  return {
    id: "patient-1",
    psychologistId: "psychologist-1",
    email: "patient@example.com",
    fullName: "Ari Silva",
    phone: null,
    preferredName: null,
    birthDate: null,
    emergencyContactName: null,
    emergencyContactPhone: null,
    emergencyContactRelationship: null,
    contactPreference: "EMAIL",
    consentStatus: "GRANTED",
    consentUpdatedAt: "2026-09-01T09:00:00.000Z",
    status: "ACTIVE",
    createdAt: "2026-09-01T09:00:00.000Z",
    updatedAt: "2026-09-01T09:00:00.000Z",
    version: 0,
    ...overrides,
  };
}

function appointment(overrides: Partial<Appointment> = {}): Appointment {
  return {
    id: "appointment-1",
    psychologistId: "psychologist-1",
    patientId: "patient-1",
    scheduledAt: "2026-09-12T09:00:00.000Z",
    status: "SCHEDULED",
    type: "IN_PERSON",
    cancellationReason: null,
    version: 0,
    ...overrides,
  };
}

function clinicalCase(overrides: Partial<ClinicalCase> = {}): ClinicalCase {
  return {
    id: "case-1",
    psychologistId: "psychologist-1",
    patientId: "patient-1",
    presentingConcern: "Initial consultation",
    status: "ACTIVE",
    openedAt: "2026-09-01T09:00:00.000Z",
    closedAt: null,
    createdAt: "2026-09-01T09:00:00.000Z",
    updatedAt: "2026-09-01T09:00:00.000Z",
    version: 0,
    ...overrides,
  };
}

function notification(overrides: Partial<Notification> = {}): Notification {
  return {
    id: "notification-1",
    eventId: "event-1",
    eventType: "PatientCreated",
    psychologistId: "psychologist-1",
    type: "SYSTEM",
    status: "SENT",
    recipient: null,
    subject: "Patient profile created",
    createdAt: "2026-09-10T08:00:00.000Z",
    sentAt: "2026-09-10T08:00:00.000Z",
    ...overrides,
  };
}

describe("dashboard insights", () => {
  it("adds useful context without adding another API", () => {
    // Arrange
    const patients = [patient()];
    const appointments = [appointment(), appointment({ id: "cancelled", status: "CANCELLED" })];
    const cases = [clinicalCase(), clinicalCase({ id: "intake", status: "INTAKE" })];
    const notifications = [notification(), notification({ id: "failed", status: "FAILED" })];

    // Act
    const metrics = practiceMetrics(patients, appointments, cases, notifications, now);

    // Assert
    expect(metrics).toEqual([
      { label: "Patients", value: 1, context: "+1 this month" },
      { label: "Upcoming", value: 1, context: "Next 7 days" },
      { label: "Cases", value: 1, context: "1 in intake" },
      { label: "Messages", value: 1, context: "Need attention" },
    ]);
  });

  it("derives navigable follow-ups without pretending they are stored tasks", () => {
    // Arrange
    const patients = [patient({ consentStatus: "PENDING" })];
    const cases = [clinicalCase({ status: "INTAKE" })];

    // Act
    const followUps = deriveFollowUps(patients, cases);

    // Assert
    expect(followUps).toEqual([
      expect.objectContaining({ title: "Consent pending", href: "/dashboard/patients", tone: "warning" }),
      expect.objectContaining({ title: "Complete case intake", href: "/dashboard/clinical", tone: "info" }),
    ]);
  });
});
