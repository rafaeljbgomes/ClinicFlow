"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import useSWR from "swr";
import {
  ChevronDownIcon,
  ClipboardListIcon,
  FileTextIcon,
  FolderOpenIcon,
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
import { ListPager } from "@/components/list-pager";
import { LoadingTable } from "@/components/loading-table";
import { SectionHeader } from "@/components/section-header";
import { STALE_VERSION_MESSAGE, StaleVersionAlert } from "@/components/stale-version-alert";
import {
  isSignable,
  SessionNoteDetails,
  SignatureLine,
  SignSessionNoteConfirmation,
} from "@/components/session-note-signature";
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
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import {
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
} from "@/components/ui/tabs";
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
import { ApiClientError, apiFetch, isStaleVersion, swrFetcher } from "@/lib/api-client";
import { formatDateTime, formatEnum } from "@/lib/format";
import { listPath } from "@/lib/list-path";
import type {
  CarePlan,
  ClinicalCase,
  ClinicalCaseStatus,
  Page,
  Patient,
  PracticeProfile,
  SessionRecord,
  UserView,
} from "@/lib/types";
import { cn } from "@/lib/utils";

const caseStatuses: ClinicalCaseStatus[] = [
  "INTAKE",
  "ACTIVE",
  "PAUSED",
  "DISCHARGED",
];

const CASE_PAGE_SIZE = 20;
const SESSION_PAGE_SIZE = 20;

export default function ClinicalPage() {
  // The switcher offers the most recently opened cases; older ones are reached through the case browser.
  const cases = useSWR<Page<ClinicalCase>>(listPath("/api/clinical-cases", { size: CASE_PAGE_SIZE }), swrFetcher);
  const patients = useSWR<Patient[]>("/api/patients", swrFetcher);
  const profile = useSWR<PracticeProfile>("/api/practice-profile", swrFetcher);
  const currentUser = useSWR<UserView>("/api/auth/me", swrFetcher);
  const [createCaseOpen, setCreateCaseOpen] = useState(false);
  const [carePlanOpen, setCarePlanOpen] = useState(false);
  // The plan the open form was built from; null when the case has no plan yet.
  const [carePlanBase, setCarePlanBase] = useState<CarePlan | null>(null);
  const [carePlanConflict, setCarePlanConflict] = useState(false);
  const [newSessionOpen, setNewSessionOpen] = useState(false);
  const [editingSession, setEditingSession] = useState<SessionRecord | null>(null);
  const [sessionConflict, setSessionConflict] = useState(false);
  const [signingSession, setSigningSession] = useState<SessionRecord | null>(null);
  const [viewingSession, setViewingSession] = useState<SessionRecord | null>(null);
  // The case the psychologist picked, as it was when picked; null means the most recently opened case.
  const [pickedCase, setPickedCase] = useState<ClinicalCase | null>(null);
  const [caseBrowserOpen, setCaseBrowserOpen] = useState(false);
  const [goalsOpen, setGoalsOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState<ClinicalCaseStatus | "ALL">("ALL");
  const [browsePage, setBrowsePage] = useState(0);
  const [sessionPage, setSessionPage] = useState({ caseId: "", number: 0 });

  // The browser filters by status on the server and pages through every case, not just the recent ones.
  const browsedCases = useSWR<Page<ClinicalCase>>(
    caseBrowserOpen
      ? listPath("/api/clinical-cases", {
        status: statusFilter === "ALL" ? undefined : statusFilter,
        page: browsePage,
        size: CASE_PAGE_SIZE,
      })
      : null,
    swrFetcher
  );

  const patientById = useMemo(
    () => new Map((patients.data ?? []).map((patient) => [patient.id, patient])),
    [patients.data]
  );

  const recentCases = useMemo(() => cases.data?.content ?? [], [cases.data]);

  // Patient names live in the patient service, so the text search narrows the page on screen.
  const visibleCases = useMemo(() => {
    const normalized = query.trim().toLowerCase();
    return (browsedCases.data?.content ?? []).filter((clinicalCase) => {
      const patient = patientById.get(clinicalCase.patientId);
      return !normalized || [patient?.fullName, patient?.preferredName, clinicalCase.presentingConcern]
        .filter(Boolean)
        .join(" ")
        .toLowerCase()
        .includes(normalized);
    });
  }, [browsedCases.data, patientById, query]);

  // A picked case that is on neither loaded page (an older case after a status change, say) is read on its own.
  const listedCase = pickedCase
    ? [...recentCases, ...(browsedCases.data?.content ?? [])].find((clinicalCase) => clinicalCase.id === pickedCase.id)
    : undefined;
  const pickedCaseDetail = useSWR<ClinicalCase>(
    pickedCase && !listedCase ? `/api/clinical-cases/${pickedCase.id}` : null,
    swrFetcher
  );
  const selectedCase = pickedCase
    ? listedCase ?? pickedCaseDetail.data ?? pickedCase
    : recentCases[0];

  const sessionPageNumber = sessionPage.caseId === selectedCase?.id ? sessionPage.number : 0;
  const carePlan = useSWR<CarePlan>(
    selectedCase ? `/api/clinical-cases/${selectedCase.id}/care-plan` : null,
    swrFetcher,
    { shouldRetryOnError: false }
  );
  const sessions = useSWR<Page<SessionRecord>>(
    selectedCase
      ? listPath(`/api/clinical-cases/${selectedCase.id}/session-records`, {
        page: sessionPageNumber,
        size: SESSION_PAGE_SIZE,
      })
      : null,
    swrFetcher
  );

  const isLoading = cases.isLoading || patients.isLoading || profile.isLoading;
  const error = cases.error ?? patients.error ?? profile.error;

  async function refreshCases() {
    await Promise.all([cases.mutate(), browsedCases.mutate(), pickedCaseDetail.mutate()]);
  }

  async function createCase(values: ClinicalCaseFormValues) {
    const created = await apiFetch<ClinicalCase>("/api/clinical-cases", {
      method: "POST",
      body: values,
    });
    toast.success("Clinical case created");
    setPickedCase(created);
    setCreateCaseOpen(false);
    await refreshCases();
  }

  async function changeStatus(status: ClinicalCaseStatus) {
    if (!selectedCase) return;
    let updated: ClinicalCase;
    try {
      updated = await apiFetch<ClinicalCase>(`/api/clinical-cases/${selectedCase.id}/status`, {
        method: "PATCH",
        body: { status },
        expectedVersion: selectedCase.version,
      });
    } catch (failure) {
      if (!isStaleVersion(failure)) throw failure;
      toast.error(`${STALE_VERSION_MESSAGE} Check the latest case status and try again.`);
      await refreshCases();
      return;
    }
    toast.success("Case status updated");
    // Keeps the workspace on this case even if the new status moves it off the loaded pages.
    setPickedCase(updated);
    await refreshCases();
  }

  function openCarePlan() {
    setCarePlanBase(carePlan.data ?? null);
    setCarePlanConflict(false);
    setCarePlanOpen(true);
  }

  // The first save creates the plan; later saves replace the version the form was built from.
  async function saveCarePlan(values: CarePlanFormValues) {
    if (!selectedCase) return;
    try {
      await apiFetch<CarePlan>(`/api/clinical-cases/${selectedCase.id}/care-plan`, {
        method: "PUT",
        body: values,
        expectedVersion: carePlanBase ? carePlanBase.version : null,
      });
    } catch (failure) {
      if (!isStaleVersion(failure)) throw failure;
      setCarePlanConflict(true);
      return;
    }
    toast.success("Care plan saved");
    setCarePlanOpen(false);
    await carePlan.mutate();
  }

  async function reloadCarePlan() {
    setCarePlanBase((await carePlan.mutate()) ?? null);
    setCarePlanConflict(false);
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

  // `editingSession` is the snapshot the form opened with, so its version is the one the edit is based on.
  async function updateSession(values: SessionRecordFormValues) {
    if (!editingSession || !selectedCase) return;
    try {
      await apiFetch<SessionRecord>(`/api/session-records/${editingSession.id}`, {
        method: "PATCH",
        body: {
          clinicalCaseId: editingSession.clinicalCaseId ?? selectedCase.id,
          ...toUpdateSessionPayload(values),
        },
        expectedVersion: editingSession.version,
      });
    } catch (failure) {
      if (!isStaleVersion(failure)) throw failure;
      setSessionConflict(true);
      return;
    }
    toast.success("Session record updated");
    closeSessionEditor();
    await sessions.mutate();
  }

  async function reloadEditedSession() {
    const latest = (await sessions.mutate())?.content.find((record) => record.id === editingSession?.id);
    setEditingSession(latest ?? null);
    setSessionConflict(false);
  }

  function closeSessionEditor() {
    setEditingSession(null);
    setSessionConflict(false);
  }

  // Signs the revision shown in the confirmation; a newer revision is never signed in its place.
  async function signSession() {
    if (!signingSession) return;
    try {
      await apiFetch<SessionRecord>(`/api/session-records/${signingSession.id}/sign`, {
        method: "POST",
        expectedVersion: signingSession.version,
      });
      toast.success("Session note signed");
    } catch (signFailure) {
      toast.error(
        isStaleVersion(signFailure)
          ? "The note changed after you opened it, so it was not signed. Review the latest version before signing."
          : signFailure instanceof ApiClientError
            ? signFailure.message
            : "The session note could not be signed"
      );
    }
    setSigningSession(null);
    await sessions.mutate();
  }

  function signerName(record: SessionRecord) {
    return record.signedBy && record.signedBy === currentUser.data?.id
      ? currentUser.data.fullName
      : undefined;
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

      {selectedCase ? (
        <div className="flex flex-col gap-6">
          <CaseWorkspaceToolbar
            cases={recentCases}
            totalCases={cases.data?.page.totalElements ?? recentCases.length}
            patientById={patientById}
            selectedCase={selectedCase}
            onBrowseCases={() => setCaseBrowserOpen(true)}
            onSelectCase={setPickedCase}
          />
          <CaseDetail
            carePlan={carePlan.data}
            carePlanError={carePlan.error}
            clinicalCase={selectedCase}
            defaultDuration={profile.data?.defaultSessionDurationMinutes}
            isCarePlanLoading={carePlan.isLoading}
            isSessionsLoading={sessions.isLoading}
            isSessionsValidating={sessions.isValidating}
            patient={patientById.get(selectedCase.patientId)}
            sessions={sessions.data}
            sessionsError={sessions.error}
            onSessionPageChange={(number) => setSessionPage({ caseId: selectedCase.id, number })}
            onChangeStatus={changeStatus}
            onCreateSession={() => setNewSessionOpen(true)}
            onEditCarePlan={openCarePlan}
            onEditSession={setEditingSession}
            onSignSession={setSigningSession}
            onViewSession={setViewingSession}
            signerName={signerName}
            onViewAllGoals={() => setGoalsOpen(true)}
          />
        </div>
      ) : (
        <EmptyClinicalState />
      )}

      <Sheet open={caseBrowserOpen} onOpenChange={setCaseBrowserOpen}>
        <SheetContent side="right" className="sm:max-w-lg">
          <SheetHeader>
            <SheetTitle>Browse clinical cases</SheetTitle>
            <SheetDescription>
              Search open and historical cases, then choose the record to work with.
            </SheetDescription>
          </SheetHeader>
          <div className="flex min-h-0 flex-1 flex-col gap-4 overflow-y-auto px-8 pb-8">
            <label className="relative">
              <SearchIcon className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
              <Input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search this page by patient or concern" className="pl-9" />
              <span className="sr-only">Search clinical cases on this page</span>
            </label>
            <Select
              value={statusFilter}
              onValueChange={(value) => {
                setStatusFilter(value as ClinicalCaseStatus | "ALL");
                setBrowsePage(0);
              }}
            >
              <SelectTrigger aria-label="Filter clinical cases by status" className="w-full">
                <SelectValue>{statusFilter === "ALL" ? "All case statuses" : formatEnum(statusFilter)}</SelectValue>
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL">All case statuses</SelectItem>
                {caseStatuses.map((status) => <SelectItem key={status} value={status}>{formatEnum(status)}</SelectItem>)}
              </SelectContent>
            </Select>
            <div className="flex items-center justify-between border-b border-border/70 pb-3">
              <p className="type-label text-muted-foreground">Cases</p>
              <p className="text-xs text-muted-foreground">
                {visibleCases.length} shown
                {browsedCases.data ? ` of ${browsedCases.data.page.totalElements}` : ""}
              </p>
            </div>
            {browsedCases.error ? <ErrorAlert message={browsedCases.error.message} /> : null}
            {browsedCases.isLoading ? (
              <p className="py-8 text-center text-sm text-muted-foreground">Loading clinical cases…</p>
            ) : visibleCases.length === 0 ? (
              <p className="py-8 text-center text-sm text-muted-foreground">
                {query.trim() ? "No cases on this page match your search." : "No clinical cases match this view."}
              </p>
            ) : (
              <div className="flex flex-col">
                {visibleCases.map((clinicalCase, index) => (
                  <div key={clinicalCase.id}>
                    <button
                      type="button"
                      onClick={() => {
                        setPickedCase(clinicalCase);
                        setCaseBrowserOpen(false);
                      }}
                      aria-pressed={selectedCase.id === clinicalCase.id}
                      className={cn(
                        "w-full rounded-xl border-l-2 border-transparent px-3.5 py-4 text-left transition duration-150 hover:bg-secondary/45 focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-ring/25",
                        selectedCase.id === clinicalCase.id && "border-l-primary bg-secondary/80 shadow-[inset_0_0_0_1px_rgb(42_118_91/12%)]"
                      )}
                    >
                      <div className="flex items-start justify-between gap-3">
                        <div className="min-w-0">
                          <p className="text-sm font-semibold">{patientName(patientById.get(clinicalCase.patientId), clinicalCase.patientId)}</p>
                          <p className="mt-1 text-xs leading-5 text-muted-foreground">{clinicalCase.presentingConcern}</p>
                        </div>
                        <StatusBadge status={clinicalCase.status} />
                      </div>
                    </button>
                    {index < visibleCases.length - 1 ? <Separator /> : null}
                  </div>
                ))}
              </div>
            )}
            <ListPager
              page={browsedCases.data?.page}
              noun="cases"
              onPageChange={setBrowsePage}
              disabled={browsedCases.isValidating}
            />
          </div>
        </SheetContent>
      </Sheet>

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
          {carePlanConflict ? <StaleVersionAlert onReload={reloadCarePlan} /> : null}
          <CarePlanForm
            key={carePlanBase?.version ?? "new"}
            carePlan={carePlanBase ?? undefined}
            onSubmit={saveCarePlan}
          />
        </DialogContent>
      </Dialog>

      <Dialog open={goalsOpen} onOpenChange={setGoalsOpen}>
        <DialogContent className="max-w-xl">
          <DialogHeader>
            <DialogTitle>Care-plan objectives</DialogTitle>
            <DialogDescription>
              Review every objective and its current progress for this care plan.
            </DialogDescription>
          </DialogHeader>
          {carePlan.data ? <CarePlanGoalsDialog carePlan={carePlan.data} /> : null}
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
        onOpenChange={(open) => !open && closeSessionEditor()}
      >
        <DialogContent className="max-w-3xl">
          <DialogHeader>
            <DialogTitle>Edit session record</DialogTitle>
            <DialogDescription>
              Complete notes and attendance for this session. Sign the note from the record menu once the
              draft is final.
            </DialogDescription>
          </DialogHeader>
          {sessionConflict ? <StaleVersionAlert onReload={reloadEditedSession} /> : null}
          {editingSession ? (
            <SessionRecordForm
              key={editingSession.version}
              includeNoteStatus
              record={editingSession}
              submitLabel="Save session record"
              onSubmit={updateSession}
            />
          ) : null}
        </DialogContent>
      </Dialog>

      <Dialog
        open={!!signingSession}
        onOpenChange={(open) => !open && setSigningSession(null)}
      >
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle>Sign session note?</DialogTitle>
            <DialogDescription>
              Signed notes can&apos;t be edited or returned to draft. Check the note before signing.
            </DialogDescription>
          </DialogHeader>
          {signingSession ? (
            <SignSessionNoteConfirmation record={signingSession} onConfirm={signSession} />
          ) : null}
        </DialogContent>
      </Dialog>

      <Dialog
        open={!!viewingSession}
        onOpenChange={(open) => !open && setViewingSession(null)}
      >
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle>Session note</DialogTitle>
            <DialogDescription>
              This note is signed and read-only.
            </DialogDescription>
          </DialogHeader>
          {viewingSession ? (
            <SessionNoteDetails record={viewingSession} signerName={signerName(viewingSession)} />
          ) : null}
        </DialogContent>
      </Dialog>
    </>
  );
}

function CaseWorkspaceToolbar({
  cases,
  totalCases,
  patientById,
  selectedCase,
  onBrowseCases,
  onSelectCase,
}: {
  cases: ClinicalCase[];
  totalCases: number;
  patientById: Map<string, Patient>;
  selectedCase: ClinicalCase;
  onBrowseCases: () => void;
  onSelectCase: (clinicalCase: ClinicalCase) => void;
}) {
  return (
    <div className="flex flex-col gap-3 lg:flex-row lg:items-center">
      <DropdownMenu>
        <DropdownMenuTrigger
          render={
            <Button
              variant="outline"
              className="h-14 w-full justify-between rounded-2xl px-4 lg:w-[25rem]"
              aria-label="Selected clinical case"
            />
          }
        >
          <span className="flex min-w-0 items-center gap-3">
            <SearchIcon className="size-4 shrink-0 text-muted-foreground" />
            <span className="truncate font-semibold">
              {patientName(patientById.get(selectedCase.patientId), selectedCase.patientId)}
            </span>
            <StatusBadge status={selectedCase.status} />
          </span>
          <ChevronDownIcon className="size-4 shrink-0 text-muted-foreground" />
        </DropdownMenuTrigger>
        <DropdownMenuContent align="start" className="w-[min(25rem,calc(100vw-2rem))]">
          <DropdownMenuLabel>
            {totalCases > cases.length ? "Recently opened cases" : "Select clinical case"}
          </DropdownMenuLabel>
          <DropdownMenuGroup>
            {cases.map((clinicalCase) => (
              <DropdownMenuItem
                key={clinicalCase.id}
                onClick={() => onSelectCase(clinicalCase)}
                className="flex items-center justify-between gap-3"
              >
                <span className="truncate">
                  {patientName(patientById.get(clinicalCase.patientId), clinicalCase.patientId)}
                </span>
                <StatusBadge status={clinicalCase.status} />
              </DropdownMenuItem>
            ))}
          </DropdownMenuGroup>
          {totalCases > cases.length ? (
            <DropdownMenuGroup>
              <DropdownMenuItem onClick={onBrowseCases}>Browse all {totalCases} cases</DropdownMenuItem>
            </DropdownMenuGroup>
          ) : null}
        </DropdownMenuContent>
      </DropdownMenu>

      <div className="flex flex-wrap items-center gap-3">
        <Button variant="outline" className="h-12 rounded-xl" onClick={onBrowseCases}>
          <FolderOpenIcon data-icon="inline-start" />
          Browse cases
        </Button>
        <Separator orientation="vertical" className="hidden h-6 lg:block" />
        <p className="text-sm text-muted-foreground">
          {totalCases} {totalCases === 1 ? "case" : "cases"}
        </p>
      </div>
    </div>
  );
}

function CaseDetail({
  carePlan,
  carePlanError,
  clinicalCase,
  defaultDuration,
  isCarePlanLoading,
  isSessionsLoading,
  isSessionsValidating,
  patient,
  sessions,
  sessionsError,
  onSessionPageChange,
  onChangeStatus,
  onCreateSession,
  onEditCarePlan,
  onEditSession,
  onSignSession,
  onViewSession,
  signerName,
  onViewAllGoals,
}: {
  carePlan?: CarePlan;
  carePlanError?: Error;
  clinicalCase: ClinicalCase;
  defaultDuration?: number;
  isCarePlanLoading: boolean;
  isSessionsLoading: boolean;
  isSessionsValidating: boolean;
  patient?: Patient;
  sessions?: Page<SessionRecord>;
  sessionsError?: Error;
  onSessionPageChange: (number: number) => void;
  onChangeStatus: (status: ClinicalCaseStatus) => Promise<void>;
  onCreateSession: () => void;
  onEditCarePlan: () => void;
  onEditSession: (record: SessionRecord) => void;
  onSignSession: (record: SessionRecord) => void;
  onViewSession: (record: SessionRecord) => void;
  signerName: (record: SessionRecord) => string | undefined;
  onViewAllGoals: () => void;
}) {
  const [activeTab, setActiveTab] = useState("sessions");
  // The clinical service already returns the latest session first.
  const orderedSessions = sessions?.content ?? [];

  return (
    <div className="flex flex-col gap-6">
      <Card className="gap-4 py-5">
        <CardContent className="grid gap-6 pt-0 lg:grid-cols-[minmax(0,1.4fr)_minmax(18rem,0.6fr)] lg:gap-0">
          <div className="flex min-w-0 flex-col gap-4 lg:pr-8">
            <div className="flex flex-col gap-5 sm:flex-row sm:items-start sm:justify-between">
              <div className="flex min-w-0 items-start gap-4">
                <span className="flex size-14 shrink-0 items-center justify-center rounded-full bg-secondary text-base font-semibold text-secondary-foreground">
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
            </div>
            <p className="max-w-3xl text-sm leading-6 text-muted-foreground">{clinicalCase.presentingConcern}</p>
            <div className="grid overflow-hidden rounded-xl border border-border/70 bg-surface-subtle divide-y divide-border/70 sm:grid-cols-3 sm:divide-x sm:divide-y-0">
              <CaseMetadata label="Opened" value={formatDateTime(clinicalCase.openedAt)} />
              <CaseMetadata label="Default duration" value={`${defaultDuration ?? 50} minutes`} />
              <div className="min-w-0 px-3 py-3 sm:px-4">
                <p className="type-label text-muted-foreground">Case status</p>
                <Select value={clinicalCase.status} onValueChange={(value) => onChangeStatus(value as ClinicalCaseStatus)}>
                  <SelectTrigger aria-label="Change clinical case status" className="mt-1.5 w-full border-0 bg-transparent px-0 shadow-none">
                    <SelectValue>{formatEnum(clinicalCase.status)}</SelectValue>
                  </SelectTrigger>
                  <SelectContent>
                    <SelectGroup>
                      {caseStatuses.map((status) => <SelectItem key={status} value={status}>{formatEnum(status)}</SelectItem>)}
                    </SelectGroup>
                  </SelectContent>
                </Select>
              </div>
            </div>
          </div>

          <div className="flex min-w-0 flex-col gap-4 border-t border-border/70 pt-6 lg:border-l lg:border-t-0 lg:pl-8 lg:pt-0">
            <div className="flex items-start justify-between gap-4">
              <div>
                <CardTitle>Care plan</CardTitle>
              </div>
              {carePlan ? (
                <Button variant="ghost" size="sm" onClick={() => setActiveTab("care-plan")}>View plan</Button>
              ) : (
                <Button variant="outline" size="sm" onClick={onEditCarePlan}>Create</Button>
              )}
            </div>
            {carePlanError && !isNotFound(carePlanError) ? (
              <ErrorAlert message={carePlanError.message} />
            ) : isCarePlanLoading ? (
              <p className="text-sm text-muted-foreground">Loading care plan…</p>
            ) : carePlan ? (
              <>
                <p className="text-sm leading-6">{carePlan.therapeuticFocus}</p>
                <div className="grid grid-cols-2 divide-x divide-border/70 border-t border-border/70 pt-4">
                  <CaseMetadata label="Next review" value={formatDate(carePlan.reviewDate)} />
                  <div className="min-w-0 px-3 py-3 sm:px-4">
                    <p className="type-label text-muted-foreground">Objective progress</p>
                    <div className="mt-2 flex flex-wrap items-center gap-2">
                      {carePlan.goals[0] ? <StatusBadge status={carePlan.goals[0].status} /> : <span className="text-sm font-semibold">No objectives</span>}
                      <span className="text-xs text-muted-foreground">
                        {carePlan.goals.filter((goal) => goal.progressPercentage >= 100).length}/{carePlan.goals.length}
                      </span>
                    </div>
                  </div>
                </div>
              </>
            ) : (
              <p className="text-sm leading-6 text-muted-foreground">No care plan has been created for this case.</p>
            )}
          </div>
        </CardContent>
      </Card>

      <Tabs value={activeTab} onValueChange={setActiveTab} className="gap-5">
        <TabsList variant="line" className="h-auto w-full justify-start gap-6 border-b border-border/70 px-1 pb-2">
          <TabsTrigger value="overview" className="flex-none px-2 py-2">Overview</TabsTrigger>
          <TabsTrigger value="sessions" className="flex-none px-2 py-2">Session records</TabsTrigger>
          <TabsTrigger value="care-plan" className="flex-none px-2 py-2">Care plan</TabsTrigger>
        </TabsList>

        <TabsContent value="overview">
          <Card tone="quiet" className="border border-border/60 bg-card">
            <CardHeader>
              <CardTitle>Case overview</CardTitle>
              <CardDescription>Current context for this clinical record.</CardDescription>
            </CardHeader>
            <CardContent className="grid gap-6 md:grid-cols-2">
              <div>
                <p className="type-label text-muted-foreground">Presenting concern</p>
                <p className="mt-2 text-sm leading-6">{clinicalCase.presentingConcern}</p>
              </div>
              <div className="grid overflow-hidden rounded-xl border border-border/70 bg-surface-subtle sm:grid-cols-2 sm:divide-x sm:divide-border/70">
                <CaseMetadata label="Session records" value={`${sessions?.page.totalElements ?? 0}`} />
                <CaseMetadata label="Care plan" value={carePlan ? "In place" : "Not created"} />
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="sessions">
          <Card className="case-work-panel">
            <CardHeader>
              <div className="session-record-header">
                <div>
                  <CardTitle>Session records</CardTitle>
                  <CardDescription>Appointment shells and manual records for the case.</CardDescription>
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
                <div className="flex min-h-36 items-center justify-center py-8 text-center text-sm text-muted-foreground">No session records for this case yet.</div>
              ) : (
                <>
                  <div className="session-record-columns type-label border-y border-border/70 py-3 text-muted-foreground">
                    <span>Date &amp; time</span>
                    <span>Note status</span>
                    <span>Attendance / modality</span>
                    <span>Summary</span>
                    <span className="sr-only">Actions</span>
                  </div>
                  {orderedSessions.map((record, index) => (
                    <SessionRecordRow
                      key={record.id}
                      record={record}
                      showDivider={index < orderedSessions.length - 1}
                      signerName={signerName(record)}
                      onEdit={() => onEditSession(record)}
                      onSign={() => onSignSession(record)}
                      onView={() => onViewSession(record)}
                    />
                  ))}
                  <ListPager
                    page={sessions?.page}
                    noun="session records"
                    onPageChange={onSessionPageChange}
                    disabled={isSessionsValidating}
                  />
                </>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="care-plan">
          <Card tone="quiet" className="border border-border/60 bg-card">
            <CardHeader>
              <div className="flex items-start justify-between gap-4">
                <div>
                  <CardTitle>Care plan</CardTitle>
                  <CardDescription>Focus, review date, and goal progress.</CardDescription>
                </div>
                <Button variant="outline" onClick={onEditCarePlan}>{carePlan ? "Edit" : "Create"}</Button>
              </div>
            </CardHeader>
            <CardContent>
              {carePlanError && !isNotFound(carePlanError) ? (
                <ErrorAlert message={carePlanError.message} />
              ) : isCarePlanLoading ? (
                <LoadingTable />
              ) : carePlan ? (
                <CarePlanSummary carePlan={carePlan} onViewAllGoals={onViewAllGoals} />
              ) : (
                <div className="flex min-h-36 items-center justify-center py-8 text-center text-sm text-muted-foreground">No care plan has been created for this case.</div>
              )}
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  );
}

function SessionRecordRow({
  record,
  showDivider,
  signerName,
  onEdit,
  onSign,
  onView,
}: {
  record: SessionRecord;
  showDivider: boolean;
  signerName?: string;
  onEdit: () => void;
  onSign: () => void;
  onView: () => void;
}) {
  return (
    <div className="flex flex-col">
      <div className="session-record-row grid gap-4 py-5">
        <div className="session-record-date">
          <p className="text-sm font-semibold">
            {new Date(record.sessionDate).toLocaleDateString("en-GB", {
              day: "numeric",
              month: "short",
              year: "numeric",
            })}
          </p>
          <p className="text-xs text-muted-foreground">
            {new Date(record.sessionDate).toLocaleTimeString("en-GB", {
              hour: "2-digit",
              minute: "2-digit",
            })}
          </p>
        </div>
        <div className="session-record-note flex flex-wrap items-start">
          <StatusBadge status={record.noteStatus} />
        </div>
        <div className="session-record-attendance flex flex-wrap items-start gap-2">
          <StatusBadge status={record.attendanceStatus} />
          <StatusBadge status={record.modality} />
        </div>
        <div className="session-record-summary min-w-0">
          <p className="text-sm leading-6 text-muted-foreground">{record.summary ?? "No summary has been added yet."}</p>
          <SignatureLine record={record} signerName={signerName} />
        </div>
        <DropdownMenu>
          <DropdownMenuTrigger render={<Button variant="ghost" size="icon-sm" className="session-record-actions" />}>
            <MoreHorizontalIcon />
            <span className="sr-only">Session record actions</span>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            <DropdownMenuGroup>
              <DropdownMenuLabel>Session record</DropdownMenuLabel>
              {record.noteStatus === "SIGNED" ? (
                <DropdownMenuItem onClick={onView}>View note</DropdownMenuItem>
              ) : (
                <>
                  <DropdownMenuItem onClick={onEdit}>Edit</DropdownMenuItem>
                  {isSignable(record) ? (
                    <DropdownMenuItem onClick={onSign}>Sign note</DropdownMenuItem>
                  ) : null}
                </>
              )}
            </DropdownMenuGroup>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
      {showDivider ? <Separator /> : null}
    </div>
  );
}

function CarePlanSummary({ carePlan, onViewAllGoals }: { carePlan: CarePlan; onViewAllGoals: () => void }) {
  return (
    <div className="flex flex-col gap-5">
      <div>
        <p className="type-label text-muted-foreground">Therapeutic focus</p>
        <p className="mt-2 text-sm leading-6">{carePlan.therapeuticFocus}</p>
      </div>
      <div className="grid overflow-hidden rounded-xl border border-border/70 bg-surface-subtle divide-y divide-border/70 sm:grid-cols-2 sm:divide-x sm:divide-y-0">
        <CaseMetadata label="Frequency" value={carePlan.plannedFrequency} />
        <CaseMetadata label="Review" value={formatDate(carePlan.reviewDate)} />
      </div>
      <div className="care-plan-preview flex flex-col">
        {carePlan.goals.map((goal, index) => (
          <div key={goal.id} className="care-plan-goal py-4 first:pt-0 last:pb-0">
            <div className="flex items-start justify-between gap-3">
              <div className="min-w-0">
                <p className="line-clamp-2 text-sm font-semibold">{goal.description}</p>
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
      {carePlan.goals.length > 1 ? (
        <Button variant="ghost" size="sm" className="w-fit px-0" onClick={onViewAllGoals}>
          View all {carePlan.goals.length} objectives
        </Button>
      ) : null}
    </div>
  );
}

function CarePlanGoalsDialog({ carePlan }: { carePlan: CarePlan }) {
  return (
    <div className="flex max-h-[55dvh] flex-col overflow-y-auto">
      {carePlan.goals.map((goal, index) => (
        <div key={goal.id} className="py-5 first:pt-0 last:pb-0">
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
          <p className="mt-1 text-xs text-muted-foreground">{goal.progressPercentage}% complete</p>
          {index < carePlan.goals.length - 1 ? <Separator className="mt-5" /> : null}
        </div>
      ))}
    </div>
  );
}

function CaseMetadata({ label, value }: { label: string; value: string }) {
  return (
    <div className="min-w-0 px-3 py-3 sm:px-4">
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
