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

// Appointments, messages and clinical cases are paged (ADR-029), so their metrics arrive as server-side
// totals rather than being counted from a full list in the browser.
export function practiceMetrics(
  patients: Patient[],
  upcomingThisWeek: number,
  activeCases: number,
  intakeCases: number,
  attentionMessages: number,
  now: number
): PracticeMetric[] {
  const startOfMonth = new Date(now);
  startOfMonth.setDate(1);
  startOfMonth.setHours(0, 0, 0, 0);
  const newPatients = patients.filter((patient) => new Date(patient.createdAt).getTime() >= startOfMonth.getTime()).length;

  return [
    { label: "Patients", value: patients.length, context: newPatients ? `+${newPatients} this month` : "No new patients this month" },
    { label: "Upcoming", value: upcomingThisWeek, context: "Next 7 days" },
    { label: "Cases", value: activeCases, context: intakeCases ? `${intakeCases} in intake` : "No cases in intake" },
    { label: "Messages", value: attentionMessages, context: attentionMessages ? "Need attention" : "All clear" },
  ];
}

// `intakeCases` is the server-filtered first page of INTAKE cases, most recently opened first.
export function deriveFollowUps(
  patients: Patient[],
  intakeCases: ClinicalCase[]
): FollowUp[] {
  const consent = patients
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
    description: patients.find((patient) => patient.id === clinicalCase.patientId)?.fullName ?? "Patient",
    href: "/dashboard/clinical",
    tone: "info" as const,
  }));

  return [...consent, ...intake].slice(0, FOLLOW_UP_LIMIT);
}
