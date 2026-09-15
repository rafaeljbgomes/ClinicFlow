import type { Appointment, ClinicalCase, Notification, Patient } from "@/lib/types";

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

export function practiceMetrics(
  patients: Patient[],
  appointments: Appointment[],
  clinicalCases: ClinicalCase[],
  notifications: Notification[],
  now: number
): PracticeMetric[] {
  const startOfMonth = new Date(now);
  startOfMonth.setDate(1);
  startOfMonth.setHours(0, 0, 0, 0);
  const sevenDays = now + 7 * 24 * 60 * 60 * 1000;
  const newPatients = patients.filter((patient) => new Date(patient.createdAt).getTime() >= startOfMonth.getTime()).length;
  const upcoming = appointments.filter((appointment) => {
    const scheduledAt = new Date(appointment.scheduledAt).getTime();
    return appointment.status !== "CANCELLED" && scheduledAt >= now && scheduledAt <= sevenDays;
  }).length;
  const activeCases = clinicalCases.filter((clinicalCase) => clinicalCase.status === "ACTIVE").length;
  const intakeCases = clinicalCases.filter((clinicalCase) => clinicalCase.status === "INTAKE").length;
  const attentionMessages = notifications.filter((notification) => notification.status === "PENDING" || notification.status === "FAILED").length;

  return [
    { label: "Patients", value: patients.length, context: newPatients ? `+${newPatients} this month` : "No new patients this month" },
    { label: "Upcoming", value: upcoming, context: "Next 7 days" },
    { label: "Cases", value: activeCases, context: intakeCases ? `${intakeCases} in intake` : "No cases in intake" },
    { label: "Messages", value: attentionMessages, context: attentionMessages ? "Need attention" : "All clear" },
  ];
}

export function deriveFollowUps(
  patients: Patient[],
  clinicalCases: ClinicalCase[]
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
  const intake = clinicalCases
    .filter((clinicalCase) => clinicalCase.status === "INTAKE")
    .map((clinicalCase) => ({
      id: `case-${clinicalCase.id}`,
      title: "Complete case intake",
      description: patients.find((patient) => patient.id === clinicalCase.patientId)?.fullName ?? "Patient",
      href: "/dashboard/clinical",
      tone: "info" as const,
    }));

  return [...consent, ...intake].slice(0, 3);
}
