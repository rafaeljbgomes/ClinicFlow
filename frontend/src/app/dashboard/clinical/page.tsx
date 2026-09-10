"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import useSWR from "swr";
import {
  ClipboardListIcon,
  FileTextIcon,
  MoreHorizontalIcon,
  PlusIcon,
  SearchIcon,
} from "lucide-react";
import { toast } from "sonner";
import {
  CarePlanForm,
  ClinicalCaseForm,
  SessionRecordForm,
  type CarePlanFormValues,
  type ClinicalCaseFormValues,
  type SessionRecordFormValues,
} from "@/components/clinical-forms";
import { ErrorAlert } from "@/components/error-alert";
import { LoadingTable } from "@/components/loading-table";
import { SectionHeader } from "@/components/section-header";
import { StatusBadge } from "@/components/status-badge";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Separator } from "@/components/ui/separator";
import { Progress } from "@/components/ui/progress";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { ApiClientError, apiFetch, swrFetcher } from "@/lib/api-client";
import { formatDateTime, formatEnum } from "@/lib/format";
import type {
  CarePlan,
  ClinicalCase,
  ClinicalCaseStatus,
  Patient,
  PracticeProfile,
  SessionRecord,
} from "@/lib/types";
import { cn } from "@/lib/utils";

const caseStatuses: ClinicalCaseStatus[] = [
  "INTAKE",
  "ACTIVE",
  "PAUSED",
  "DISCHARGED",
];

export default function ClinicalPage() {
  const cases = useSWR<ClinicalCase[]>("/api/clinical-cases", swrFetcher);
  const patients = useSWR<Patient[]>("/api/patients", swrFetcher);
  const profile = useSWR<PracticeProfile>("/api/practice-profile", swrFetcher);
  const [createCaseOpen, setCreateCaseOpen] = useState(false);
  const [carePlanOpen, setCarePlanOpen] = useState(false);
  const [newSessionOpen, setNewSessionOpen] = useState(false);
  const [editingSession, setEditingSession] = useState<SessionRecord | null>(null);
  const [selectedCaseId, setSelectedCaseId] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState<ClinicalCaseStatus | "ALL">("ALL");

  const patientById = useMemo(
    () => new Map((patients.data ?? []).map((patient) => [patient.id, patient])),
    [patients.data]
  );

  const sortedCases = useMemo(
    () =>
      [...(cases.data ?? [])].sort(
        (a, b) => new Date(b.openedAt).getTime() - new Date(a.openedAt).getTime()
      ),
    [cases.data]
  );

  const visibleCases = useMemo(() => {
    const normalized = query.trim().toLowerCase();
    return sortedCases.filter((clinicalCase) => {
      const patient = patientById.get(clinicalCase.patientId);
      const matchesQuery = !normalized || [patient?.fullName, patient?.preferredName, clinicalCase.presentingConcern]
        .filter(Boolean)
        .join(" ")
        .toLowerCase()
        .includes(normalized);
      return matchesQuery && (statusFilter === "ALL" || clinicalCase.status === statusFilter);
    });
  }, [patientById, query, sortedCases, statusFilter]);

  const selectedCase =
    visibleCases.find((clinicalCase) => clinicalCase.id === selectedCaseId) ??
    visibleCases[0];

  const carePlan = useSWR<CarePlan>(
    selectedCase ? `/api/clinical-cases/${selectedCase.id}/care-plan` : null,
    swrFetcher,
    { shouldRetryOnError: false }
  );
  const sessions = useSWR<SessionRecord[]>(
    selectedCase ? `/api/clinical-cases/${selectedCase.id}/session-records` : null,
    swrFetcher
  );

  const isLoading = cases.isLoading || patients.isLoading || profile.isLoading;
  const error = cases.error ?? patients.error ?? profile.error;

  async function createCase(values: ClinicalCaseFormValues) {
    const created = await apiFetch<ClinicalCase>("/api/clinical-cases", {
      method: "POST",
      body: values,
    });
    toast.success("Clinical case created");
    setSelectedCaseId(created.id);
    setCreateCaseOpen(false);
    await cases.mutate();
  }

  async function changeStatus(status: ClinicalCaseStatus) {
    if (!selectedCase) return;
    await apiFetch<ClinicalCase>(`/api/clinical-cases/${selectedCase.id}/status`, {
      method: "PATCH",
      body: { status },
    });
    toast.success("Case status updated");
    await cases.mutate();
  }

  async function saveCarePlan(values: CarePlanFormValues) {
    if (!selectedCase) return;
    await apiFetch<CarePlan>(`/api/clinical-cases/${selectedCase.id}/care-plan`, {
      method: "PUT",
      body: values,
    });
    toast.success("Care plan saved");
    setCarePlanOpen(false);
    await carePlan.mutate();
  }

  async function createSession(values: SessionRecordFormValues) {
    if (!selectedCase) return;
    await apiFetch<SessionRecord>(
      `/api/clinical-cases/${selectedCase.id}/session-records`,
      {
        method: "POST",
        body: toCreateSessionPayload(values),
      }
    );
    toast.success("Session record created");
    setNewSessionOpen(false);
    await sessions.mutate();
  }

  async function updateSession(values: SessionRecordFormValues) {
    if (!editingSession || !selectedCase) return;
    await apiFetch<SessionRecord>(`/api/session-records/${editingSession.id}`, {
      method: "PATCH",
      body: {
        clinicalCaseId: editingSession.clinicalCaseId ?? selectedCase.id,
        ...toUpdateSessionPayload(values),
      },
    });
    toast.success("Session record updated");
    setEditingSession(null);
    await sessions.mutate();
  }

  if (isLoading) {
    return <LoadingTable />;
  }

  return (
    <>
      <SectionHeader
        title="Clinical"
        description="Coordinate cases, care plans, and session records with clarity."
        actions={
          <Button
            disabled={(patients.data ?? []).length === 0}
            onClick={() => setCreateCaseOpen(true)}
          >
            <PlusIcon data-icon="inline-start" />
            New clinical case
          </Button>
        }
      />

      {error ? <ErrorAlert message={error.message} /> : null}

      <div className="grid items-start gap-6 xl:grid-cols-[24rem_minmax(0,1fr)]">
        <Card tone="quiet" className="self-start border border-border/60 bg-card">
          <CardHeader>
            <CardTitle>Case registry</CardTitle>
            <CardDescription>
              Open and historical clinical cases for your patients.
            </CardDescription>
          </CardHeader>
          <CardContent className="flex flex-col gap-4">
            <div className="flex flex-col gap-3">
              <label className="relative">
                <SearchIcon className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
                <Input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search cases or patients" className="pl-9" />
                <span className="sr-only">Search clinical cases</span>
              </label>
              <Select value={statusFilter} onValueChange={(value) => setStatusFilter(value as ClinicalCaseStatus | "ALL")}>
                <SelectTrigger aria-label="Filter clinical cases by status" className="w-full"><SelectValue>{statusFilter === "ALL" ? "All case statuses" : formatEnum(statusFilter)}</SelectValue></SelectTrigger>
                <SelectContent>
                  <SelectItem value="ALL">All case statuses</SelectItem>
                  {caseStatuses.map((status) => <SelectItem key={status} value={status}>{formatEnum(status)}</SelectItem>)}
                </SelectContent>
              </Select>
            </div>
            {sortedCases.length === 0 ? (
              <EmptyClinicalState />
            ) : visibleCases.length === 0 ? (
              <p className="py-6 text-center text-sm text-muted-foreground">No clinical cases match this view.</p>
            ) : (
              visibleCases.map((clinicalCase) => (
                <button
                  key={clinicalCase.id}
                  type="button"
                  onClick={() => setSelectedCaseId(clinicalCase.id)}
                  className={cn(
                    "w-full border-l-2 border-transparent px-3 py-3 text-left transition duration-150 hover:bg-secondary/45 focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-ring/25",
                    selectedCase?.id === clinicalCase.id && "border-l-primary bg-secondary/80"
                  )}
                >
                  <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0">
                      <p className="text-sm font-semibold">
                        {patientName(patientById.get(clinicalCase.patientId), clinicalCase.patientId)}
                      </p>
                      <p className="mt-1 text-xs leading-5 text-muted-foreground">
                        {clinicalCase.presentingConcern}
                      </p>
                    </div>
                    <StatusBadge status={clinicalCase.status} />
                  </div>
                </button>
              ))
            )}
          </CardContent>
        </Card>

        {selectedCase ? (
          <CaseDetail
            carePlan={carePlan.data}
            carePlanError={carePlan.error}
            clinicalCase={selectedCase}
            defaultDuration={profile.data?.defaultSessionDurationMinutes}
            isCarePlanLoading={carePlan.isLoading}
            isSessionsLoading={sessions.isLoading}
            patient={patientById.get(selectedCase.patientId)}
            sessions={sessions.data ?? []}
            sessionsError={sessions.error}
            onChangeStatus={changeStatus}
            onCreateSession={() => setNewSessionOpen(true)}
            onEditCarePlan={() => setCarePlanOpen(true)}
            onEditSession={setEditingSession}
          />
        ) : (
          <Card>
            <CardContent className="flex min-h-72 items-center justify-center text-sm text-muted-foreground">
              Create a clinical case to start the clinical record.
            </CardContent>
          </Card>
        )}
      </div>

      <Dialog open={createCaseOpen} onOpenChange={setCreateCaseOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Create clinical case</DialogTitle>
            <DialogDescription>
              Start a clinical record for an existing patient.
            </DialogDescription>
          </DialogHeader>
          <ClinicalCaseForm patients={patients.data ?? []} onSubmit={createCase} />
        </DialogContent>
      </Dialog>

      <Dialog open={carePlanOpen} onOpenChange={setCarePlanOpen}>
        <DialogContent className="max-w-4xl">
          <DialogHeader>
            <DialogTitle>Care plan</DialogTitle>
            <DialogDescription>
              Maintain focus, review cadence, and measurable goals for this case.
            </DialogDescription>
          </DialogHeader>
          <CarePlanForm carePlan={carePlan.data} onSubmit={saveCarePlan} />
        </DialogContent>
      </Dialog>

      <Dialog open={newSessionOpen} onOpenChange={setNewSessionOpen}>
        <DialogContent className="max-w-3xl">
          <DialogHeader>
            <DialogTitle>New session record</DialogTitle>
            <DialogDescription>
              Add a manual record to the selected clinical case.
            </DialogDescription>
          </DialogHeader>
          <SessionRecordForm
            defaultDuration={profile.data?.defaultSessionDurationMinutes}
            submitLabel="Create session record"
            onSubmit={createSession}
          />
        </DialogContent>
      </Dialog>

      <Dialog
        open={!!editingSession}
        onOpenChange={(open) => !open && setEditingSession(null)}
      >
        <DialogContent className="max-w-3xl">
          <DialogHeader>
            <DialogTitle>Edit session record</DialogTitle>
            <DialogDescription>
              Complete notes, attendance, and signature status for this session.
            </DialogDescription>
          </DialogHeader>
          {editingSession ? (
            <SessionRecordForm
              includeNoteStatus
              record={editingSession}
              submitLabel="Save session record"
              onSubmit={updateSession}
            />
          ) : null}
        </DialogContent>
      </Dialog>
    </>
  );
}

function CaseDetail({
  carePlan,
  carePlanError,
  clinicalCase,
  defaultDuration,
  isCarePlanLoading,
  isSessionsLoading,
  patient,
  sessions,
  sessionsError,
  onChangeStatus,
  onCreateSession,
  onEditCarePlan,
  onEditSession,
}: {
  carePlan?: CarePlan;
  carePlanError?: Error;
  clinicalCase: ClinicalCase;
  defaultDuration?: number;
  isCarePlanLoading: boolean;
  isSessionsLoading: boolean;
  patient?: Patient;
  sessions: SessionRecord[];
  sessionsError?: Error;
  onChangeStatus: (status: ClinicalCaseStatus) => Promise<void>;
  onCreateSession: () => void;
  onEditCarePlan: () => void;
  onEditSession: (record: SessionRecord) => void;
}) {
  const orderedSessions = useMemo(
    () =>
      [...sessions].sort(
        (a, b) =>
          new Date(b.sessionDate).getTime() - new Date(a.sessionDate).getTime()
      ),
    [sessions]
  );

  return (
    <div className="flex flex-col gap-6">
      <Card>
        <CardHeader>
          <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
            <div className="flex min-w-0 items-start gap-4">
              <span className="flex size-12 shrink-0 items-center justify-center rounded-full bg-secondary text-base font-semibold text-secondary-foreground">
                {patientInitials(patient)}
              </span>
              <div className="min-w-0">
                <CardTitle>{patient?.fullName ?? clinicalCase.patientId}</CardTitle>
                {patient?.preferredName && patient.preferredName !== patient.fullName ? (
                  <p className="mt-1 text-sm text-muted-foreground">Prefers {patient.preferredName}</p>
                ) : null}
                <p className="mt-1 text-xs leading-5 text-muted-foreground">{patientContext(patient)}</p>
              </div>
            </div>
            <div className="flex flex-wrap items-center gap-2">
              <Button
                variant="outline"
                render={
                  <Link
                    href={`/dashboard/patients/${clinicalCase.patientId}/clinical-history`}
                  />
                }
                nativeButton={false}
              >
                <FileTextIcon data-icon="inline-start" />
                Patient history
              </Button>
              <Select
                value={clinicalCase.status}
                onValueChange={(value) =>
                  onChangeStatus(value as ClinicalCaseStatus)
                }
              >
                <SelectTrigger className="w-40">
                  <SelectValue>{formatEnum(clinicalCase.status)}</SelectValue>
                </SelectTrigger>
                <SelectContent>
                  <SelectGroup>
                    {caseStatuses.map((status) => (
                      <SelectItem key={status} value={status}>
                        {formatEnum(status)}
                      </SelectItem>
                    ))}
                  </SelectGroup>
                </SelectContent>
              </Select>
            </div>
          </div>
        </CardHeader>
        <CardContent className="flex flex-col gap-5">
          <p className="max-w-4xl text-sm leading-6 text-muted-foreground">{clinicalCase.presentingConcern}</p>
          <div className="grid divide-y divide-border/70 border-y border-border/70 sm:grid-cols-3 sm:divide-x sm:divide-y-0">
            <CaseMetadata label="Opened" value={formatDateTime(clinicalCase.openedAt)} />
            <CaseMetadata label="Default duration" value={`${defaultDuration ?? 50} minutes`} />
            <CaseMetadata label="Status" value={formatEnum(clinicalCase.status)} />
          </div>
        </CardContent>
      </Card>

      <div className="grid items-start gap-6 xl:grid-cols-[minmax(0,1.15fr)_minmax(19rem,0.95fr)]">
        <Card className="case-work-panel">
          <CardHeader>
            <div className="session-record-header">
              <div>
                <CardTitle>Session records</CardTitle>
                <CardDescription>
                  Appointment shells and manual records for the case.
                </CardDescription>
              </div>
              <Button onClick={onCreateSession}>
                <PlusIcon data-icon="inline-start" />
                New record
              </Button>
            </div>
          </CardHeader>
          <CardContent className="session-record-list flex flex-col gap-1">
            {sessionsError ? <ErrorAlert message={sessionsError.message} /> : null}
            {isSessionsLoading ? (
              <LoadingTable />
            ) : orderedSessions.length === 0 ? (
              <div className="flex min-h-36 items-center justify-center py-8 text-center text-sm text-muted-foreground">
                No session records for this case yet.
              </div>
            ) : (
              orderedSessions.map((record, index) => (
                <SessionRecordRow
                  key={record.id}
                  record={record}
                  showDivider={index < orderedSessions.length - 1}
                  onEdit={() => onEditSession(record)}
                />
              ))
            )}
          </CardContent>
        </Card>

        <Card tone="quiet" className="border border-border/60 bg-card">
          <CardHeader>
            <div className="flex items-start justify-between gap-4">
              <div>
                <CardTitle>Care plan</CardTitle>
                <CardDescription>
                  Focus, review date, and goal progress.
                </CardDescription>
              </div>
              <Button variant="outline" onClick={onEditCarePlan}>
                {carePlan ? "Edit" : "Create"}
              </Button>
            </div>
          </CardHeader>
          <CardContent>
            {carePlanError && !isNotFound(carePlanError) ? (
              <ErrorAlert message={carePlanError.message} />
            ) : isCarePlanLoading ? (
              <LoadingTable />
            ) : carePlan ? (
              <CarePlanSummary carePlan={carePlan} />
            ) : (
              <div className="flex min-h-36 items-center justify-center py-8 text-center text-sm text-muted-foreground">
                No care plan has been created for this case.
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}

function SessionRecordRow({
  record,
  showDivider,
  onEdit,
}: {
  record: SessionRecord;
  showDivider: boolean;
  onEdit: () => void;
}) {
  return (
    <div className="flex flex-col">
      <div className="session-record-row grid gap-4 py-5">
        <div>
          <p className="text-sm font-semibold">
            {new Date(record.sessionDate).toLocaleTimeString("en-GB", {
              hour: "2-digit",
              minute: "2-digit",
            })}
          </p>
          <p className="text-xs text-muted-foreground">
            {new Date(record.sessionDate).toLocaleDateString("en-GB", {
              month: "short",
              day: "numeric",
            })}
          </p>
        </div>
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <StatusBadge status={record.noteStatus} />
            <StatusBadge status={record.attendanceStatus} />
            <StatusBadge status={record.modality} />
          </div>
          <p className="mt-2 text-sm leading-6 text-muted-foreground">
            {record.summary ?? "No summary has been added yet."}
          </p>
        </div>
        <DropdownMenu>
          <DropdownMenuTrigger render={<Button variant="ghost" size="icon-sm" />}>
            <MoreHorizontalIcon />
            <span className="sr-only">Session record actions</span>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            <DropdownMenuGroup>
              <DropdownMenuLabel>Session record</DropdownMenuLabel>
              <DropdownMenuItem onClick={onEdit}>Edit</DropdownMenuItem>
            </DropdownMenuGroup>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
      {showDivider ? <Separator /> : null}
    </div>
  );
}

function CarePlanSummary({ carePlan }: { carePlan: CarePlan }) {
  return (
    <div className="flex flex-col gap-5">
      <div>
        <p className="type-label text-muted-foreground">Therapeutic focus</p>
        <p className="mt-2 text-sm leading-6">{carePlan.therapeuticFocus}</p>
      </div>
      <div className="grid divide-y divide-border/70 border-y border-border/70 sm:grid-cols-2 sm:divide-x sm:divide-y-0">
        <CaseMetadata label="Frequency" value={carePlan.plannedFrequency} />
        <CaseMetadata label="Review" value={formatDate(carePlan.reviewDate)} />
      </div>
      <div className="flex flex-col">
        {carePlan.goals.map((goal, index) => (
          <div key={goal.id} className="py-4 first:pt-0 last:pb-0">
            <div className="flex items-start justify-between gap-3">
              <div className="min-w-0">
                <p className="text-sm font-semibold">{goal.description}</p>
                <p className="mt-1 text-xs text-muted-foreground">
                  {goal.targetDate ? `Target ${formatDate(goal.targetDate)}` : "No target date"}
                </p>
              </div>
              <StatusBadge status={goal.status} />
            </div>
            <Progress className="mt-4" value={goal.progressPercentage} />
            <p className="mt-1 text-xs text-muted-foreground">
              {goal.progressPercentage}% complete
            </p>
            {index < carePlan.goals.length - 1 ? <Separator className="mt-4" /> : null}
          </div>
        ))}
      </div>
    </div>
  );
}

function CaseMetadata({ label, value }: { label: string; value: string }) {
  return (
    <div className="py-3 first:pt-0 last:pb-0 sm:px-4 sm:py-0 sm:first:pl-0 sm:last:pr-0">
      <p className="type-label text-muted-foreground">{label}</p>
      <p className="mt-2 text-sm font-semibold">{value}</p>
    </div>
  );
}

function EmptyClinicalState() {
  return (
    <div className="flex min-h-72 flex-col items-center justify-center gap-4 rounded-xl border border-border bg-muted/35 p-8 text-center">
      <span className="flex size-14 items-center justify-center rounded-full bg-secondary text-secondary-foreground">
        <ClipboardListIcon className="size-6" />
      </span>
      <div className="flex max-w-sm flex-col gap-2">
        <p className="text-base font-medium">No clinical cases yet</p>
        <p className="text-sm leading-6 text-muted-foreground">
          Create a case from an existing patient to begin the clinical record.
        </p>
      </div>
    </div>
  );
}

function patientName(patient: Patient | undefined, fallback: string) {
  if (!patient) {
    return fallback;
  }
  return patient.preferredName || patient.fullName;
}

function patientInitials(patient?: Patient) {
  return (patient?.fullName ?? "Patient")
    .split(" ")
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join("");
}

function patientContext(patient?: Patient) {
  if (!patient) return "Patient details are unavailable.";
  const details = [patient.birthDate ? `${formatDate(patient.birthDate)} · ${ageFromDate(patient.birthDate)} years` : null, `Patient since ${formatDate(patient.createdAt)}`]
    .filter(Boolean);
  return details.join(" · ");
}

function ageFromDate(value: string) {
  const birthDate = new Date(value);
  const now = new Date();
  let age = now.getFullYear() - birthDate.getFullYear();
  const hasHadBirthday = now.getMonth() > birthDate.getMonth() || (now.getMonth() === birthDate.getMonth() && now.getDate() >= birthDate.getDate());
  if (!hasHadBirthday) age -= 1;
  return age;
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("en-GB", { dateStyle: "medium" }).format(new Date(value));
}

function isNotFound(error: Error) {
  return error instanceof ApiClientError && error.status === 404;
}

function toCreateSessionPayload(values: SessionRecordFormValues) {
  return {
    sessionDate: values.sessionDate,
    modality: values.modality,
    durationMinutes: values.durationMinutes,
    attendanceStatus: values.attendanceStatus,
    summary: values.summary,
    focusAreas: values.focusAreas,
    interventions: values.interventions,
    homework: values.homework,
    nextSteps: values.nextSteps,
  };
}

function toUpdateSessionPayload(values: SessionRecordFormValues) {
  return {
    sessionDate: values.sessionDate,
    modality: values.modality,
    durationMinutes: values.durationMinutes,
    attendanceStatus: values.attendanceStatus,
    noteStatus: values.noteStatus,
    summary: values.summary,
    focusAreas: values.focusAreas,
    interventions: values.interventions,
    homework: values.homework,
    nextSteps: values.nextSteps,
  };
}
