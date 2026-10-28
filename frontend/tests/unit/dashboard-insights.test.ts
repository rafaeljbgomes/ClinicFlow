import { describe, expect, it } from "vitest";
import { deriveFollowUps, practiceMetrics } from "@/lib/dashboard-insights";
import type { ClinicalCase, Patient } from "@/lib/types";

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

describe("dashboard insights", () => {
  it("combines server-side totals with patient and case context", () => {
    // Arrange
    const patients = [patient()];
    const cases = [clinicalCase(), clinicalCase({ id: "intake", status: "INTAKE" })];

    // Act
    const metrics = practiceMetrics(patients, 1, cases, 1, now);

    // Assert
    expect(metrics).toEqual([
      { label: "Patients", value: 1, context: "+1 this month" },
      { label: "Upcoming", value: 1, context: "Next 7 days" },
      { label: "Cases", value: 1, context: "1 in intake" },
      { label: "Messages", value: 1, context: "Need attention" },
    ]);
  });

  it("reports quiet states when the server totals are zero", () => {
    // Act
    const metrics = practiceMetrics([], 0, [], 0, now);

    // Assert
    expect(metrics).toEqual([
      { label: "Patients", value: 0, context: "No new patients this month" },
      { label: "Upcoming", value: 0, context: "Next 7 days" },
      { label: "Cases", value: 0, context: "No cases in intake" },
      { label: "Messages", value: 0, context: "All clear" },
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
