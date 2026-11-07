import { describe, expect, it } from "vitest";
import { deriveFollowUps, practiceMetrics, startOfMonth, upcomingQueryStart } from "@/lib/dashboard-insights";
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
  it("starts the month at local midnight on its first day", () => {
    // Act
    const start = new Date(startOfMonth(now));

    // Assert
    expect([start.getFullYear(), start.getMonth(), start.getDate(), start.getHours(), start.getMinutes()])
      .toEqual([2026, 8, 1, 0, 0]);
  });

  it("names intake follow-ups from the patient lookup", () => {
    // Arrange
    const named = patient({ id: "patient-7", fullName: "Rita Gomes" });
    const cases = [clinicalCase({ id: "intake-7", patientId: "patient-7", status: "INTAKE" })];

    // Act
    const followUps = deriveFollowUps([], cases, new Map([[named.id, named]]));

    // Assert
    expect(followUps).toEqual([expect.objectContaining({ id: "case-intake-7", description: "Rita Gomes" })]);
  });

  it("falls back to a generic label when the lookup has no name", () => {
    // Arrange
    const cases = [clinicalCase({ id: "intake-8", patientId: "unknown", status: "INTAKE" })];

    // Act
    const followUps = deriveFollowUps([], cases, new Map());

    // Assert
    expect(followUps).toEqual([expect.objectContaining({ description: "Patient" })]);
  });

  it("combines server-side totals with patient context", () => {
    // Act
    const metrics = practiceMetrics({ total: 1, newThisMonth: 1 }, 1, 4, 2, 1);

    // Assert
    expect(metrics).toEqual([
      { label: "Patients", value: 1, context: "+1 this month" },
      { label: "Upcoming", value: 1, context: "Next 7 days" },
      { label: "Cases", value: 4, context: "2 in intake" },
      { label: "Messages", value: 1, context: "Need attention" },
    ]);
  });

  it("reports quiet states when the server totals are zero", () => {
    // Act
    const metrics = practiceMetrics({ total: 0, newThisMonth: 0 }, 0, 0, 0, 0);

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
    const followUps = deriveFollowUps(patients, cases, new Map());

    // Assert
    expect(followUps).toEqual([
      expect.objectContaining({ title: "Consent pending", href: "/dashboard/patients", tone: "warning" }),
      expect.objectContaining({ title: "Complete case intake", href: "/dashboard/clinical", tone: "info" }),
    ]);
  });

  it("keeps consent follow-ups ahead of intake cases within the card limit", () => {
    // Arrange
    const patients = [
      patient({ id: "pending", consentStatus: "PENDING" }),
      patient({ id: "revoked", consentStatus: "REVOKED" }),
    ];
    const intakeCases = [
      clinicalCase({ id: "intake-1", status: "INTAKE" }),
      clinicalCase({ id: "intake-2", status: "INTAKE" }),
    ];

    // Act
    const followUps = deriveFollowUps(patients, intakeCases, new Map());

    // Assert
    expect(followUps.map((item) => item.id)).toEqual(["consent-pending", "consent-revoked", "case-intake-1"]);
  });
});

describe("upcomingQueryStart", () => {
  it("gives the same start to visits within one five-minute step", () => {
    // Arrange
    const firstVisit = new Date("2026-09-10T09:01:12.345Z").getTime();
    const laterVisit = new Date("2026-09-10T09:04:59.999Z").getTime();

    // Act
    const starts = [upcomingQueryStart(firstVisit), upcomingQueryStart(laterVisit)];

    // Assert
    expect(starts).toEqual(Array(2).fill(new Date("2026-09-10T09:00:00.000Z").getTime()));
  });

  it("moves on when the next step begins", () => {
    // Arrange
    const nextStep = new Date("2026-09-10T09:05:00.000Z").getTime();

    // Act
    const start = upcomingQueryStart(nextStep);

    // Assert
    expect(start).toBe(nextStep);
  });
});
