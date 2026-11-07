import type { ClinicalCase, Patient } from "@/lib/types";

export type PracticeMetric = {
  label: string;
  value: number;
  context: string;
};

export type FollowUp = {
  id: string;
  title: string;
  description: string;
  href: string;
  tone: "warning" | "danger" | "info";
};

// Statuses of appointments that still need to happen; the dashboard counts and lists only these.
export const OPEN_APPOINTMENT_STATUSES = ["SCHEDULED", "RESCHEDULED"] as const;
// Messages in these statuses need the psychologist's attention.
export const ATTENTION_NOTIFICATION_STATUSES = ["PENDING", "FAILED"] as const;
export const UPCOMING_WINDOW_MS = 7 * 24 * 60 * 60 * 1000;
// The follow-up card shows at most this many items, so it never needs more intake cases than this.
export const FOLLOW_UP_LIMIT = 3;
// Patients whose consent needs a follow-up.
export const CONSENT_FOLLOW_UP_STATUSES = ["PENDING", "REVOKED"] as const;

// Appointments, messages and clinical cases are paged (ADR-029), so their metrics arrive as server-side
// totals rather than being counted from a full list in the browser.
// The first instant of the local month containing `now`, as the patient list's createdFrom filter.
export function startOfMonth(now: number): string {
  const start = new Date(now);
  start.setDate(1);
  start.setHours(0, 0, 0, 0);
  return start.toISOString();
}

// Patients are paged too, so their count and this month's new patients are server-side totals.
export function practiceMetrics(
  patients: { total: number; newThisMonth: number },
  upcomingThisWeek: number,
  activeCases: number,
  intakeCases: number,
  attentionMessages: number
): PracticeMetric[] {
  const newPatients = patients.newThisMonth;

  return [
    { label: "Patients", value: patients.total, context: newPatients ? `+${newPatients} this month` : "No new patients this month" },
    { label: "Upcoming", value: upcomingThisWeek, context: "Next 7 days" },
    { label: "Cases", value: activeCases, context: intakeCases ? `${intakeCases} in intake` : "No cases in intake" },
    { label: "Messages", value: attentionMessages, context: attentionMessages ? "Need attention" : "All clear" },
  ];
}

// `consentPatients` is the server-filtered first page of patients whose consent needs a follow-up, and
// `intakeCases` the first page of INTAKE cases, most recently opened first. `patientById` names the cases.
export function deriveFollowUps(
  consentPatients: Patient[],
  intakeCases: ClinicalCase[],
  patientById: ReadonlyMap<string, Patient>
): FollowUp[] {
  const consent = consentPatients
    .filter((patient) => patient.consentStatus === "PENDING" || patient.consentStatus === "REVOKED")
    .map((patient) => ({
      id: `consent-${patient.id}`,
      title: patient.consentStatus === "REVOKED" ? "Review consent" : "Consent pending",
      description: patient.fullName,
      href: "/dashboard/patients",
      tone: patient.consentStatus === "REVOKED" ? "danger" as const : "warning" as const,
    }));
  const intake = intakeCases.map((clinicalCase) => ({
    id: `case-${clinicalCase.id}`,
    title: "Complete case intake",
    description: patientById.get(clinicalCase.patientId)?.fullName ?? "Patient",
    href: "/dashboard/clinical",
    tone: "info" as const,
  }));

  return [...consent, ...intake].slice(0, FOLLOW_UP_LIMIT);
}
