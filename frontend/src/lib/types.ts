export type Role = "ADMIN" | "PSYCHOLOGIST" | "PATIENT";

export type UserView = {
  id: string;
  email: string;
  role: Role;
  fullName: string;
  enabled: boolean;
};

export type PatientStatus = "ACTIVE" | "INACTIVE" | "ARCHIVED";
export type ConsentStatus = "GRANTED" | "PENDING" | "REVOKED";
export type ContactPreference = "EMAIL" | "PHONE" | "SMS";

export type Patient = {
  id: string;
  psychologistId: string;
  email: string;
  fullName: string;
  phone: string | null;
  preferredName: string | null;
  birthDate: string | null;
  emergencyContactName: string | null;
  emergencyContactPhone: string | null;
  emergencyContactRelationship: string | null;
  contactPreference: ContactPreference;
  consentStatus: ConsentStatus;
  consentUpdatedAt: string;
  status: PatientStatus;
  createdAt: string;
  updatedAt: string;
};

export type AppointmentStatus =
  | "SCHEDULED"
  | "RESCHEDULED"
  | "CANCELLED"
  | "COMPLETED";

export type AppointmentType = "IN_PERSON" | "ONLINE";

export type Appointment = {
  id: string;
  psychologistId: string;
  patientId: string;
  scheduledAt: string;
  status: AppointmentStatus;
  type: AppointmentType;
  cancellationReason: string | null;
};

export type NotificationStatus = "PENDING" | "SENT" | "FAILED";
export type NotificationType = "EMAIL" | "SYSTEM";

export type Notification = {
  id: string;
  eventId: string;
  eventType: string;
  psychologistId: string | null;
  type: NotificationType;
  status: NotificationStatus;
  recipient: string | null;
  subject: string;
  createdAt: string;
  sentAt: string | null;
};

export type ClinicalCaseStatus = "INTAKE" | "ACTIVE" | "PAUSED" | "DISCHARGED";
export type GoalStatus =
  | "NOT_STARTED"
  | "IN_PROGRESS"
  | "ACHIEVED"
  | "PAUSED"
  | "DISCONTINUED";
export type AttendanceStatus = "ATTENDED" | "NO_SHOW" | "CANCELLED_LATE";
export type NoteStatus = "PENDING_NOTE" | "DRAFT" | "SIGNED";
export type SessionModality = "IN_PERSON" | "ONLINE";

export type PracticeProfile = {
  psychologistId: string;
  professionalRegistration: string | null;
  timezone: string;
  defaultSessionDurationMinutes: number;
  primaryLocation: string | null;
  telehealthEnabled: boolean;
  createdAt: string;
  updatedAt: string;
};

export type ClinicalCase = {
  id: string;
  psychologistId: string;
  patientId: string;
  presentingConcern: string;
  status: ClinicalCaseStatus;
  openedAt: string;
  closedAt: string | null;
  createdAt: string;
  updatedAt: string;
};

export type CareGoal = {
  id: string;
  description: string;
  targetDate: string | null;
  status: GoalStatus;
  progressPercentage: number;
};

export type CarePlan = {
  id: string;
  clinicalCaseId: string;
  psychologistId: string;
  patientId: string;
  therapeuticFocus: string;
  plannedFrequency: string;
  reviewDate: string;
  goals: CareGoal[];
  createdAt: string;
  updatedAt: string;
};

export type SessionRecord = {
  id: string;
  clinicalCaseId: string | null;
  psychologistId: string;
  patientId: string;
  appointmentId: string | null;
  sessionDate: string;
  modality: SessionModality;
  durationMinutes: number;
  attendanceStatus: AttendanceStatus;
  noteStatus: NoteStatus;
  summary: string | null;
  focusAreas: string | null;
  interventions: string | null;
  homework: string | null;
  nextSteps: string | null;
  createdAt: string;
  updatedAt: string;
};

export type PatientClinicalHistory = {
  patientId: string;
  cases: ClinicalCase[];
  carePlans: CarePlan[];
  sessionRecords: SessionRecord[];
};

export type ServiceHealth = {
  service: "auth" | "patient" | "appointment" | "clinical" | "notification";
  label: string;
  status: "UP" | "DOWN" | "UNKNOWN";
  latencyMs?: number;
};

export type SystemHealth = {
  services: ServiceHealth[];
};
