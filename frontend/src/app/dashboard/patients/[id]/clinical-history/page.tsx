"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import useSWR from "swr";
import { ArrowLeftIcon, ClipboardListIcon } from "lucide-react";
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
import { Separator } from "@/components/ui/separator";
import { swrFetcher } from "@/lib/api-client";
import { formatDateTime } from "@/lib/format";
import type { CarePlan, Patient, PatientClinicalHistory, SessionRecord } from "@/lib/types";

export default function PatientClinicalHistoryPage() {
  const params = useParams<{ id: string }>();
  const patientId = params.id;
  const patient = useSWR<Patient>(`/api/patients/${patientId}`, swrFetcher);
  const history = useSWR<PatientClinicalHistory>(
    `/api/patients/${patientId}/clinical-history`,
    swrFetcher
  );

  const isLoading = patient.isLoading || history.isLoading;
  const error = patient.error ?? history.error;

  if (isLoading) {
    return <LoadingTable />;
  }

  const patientName = patient.data?.preferredName || patient.data?.fullName || "Patient";

  return (
    <>
      <SectionHeader
        title="Clinical history"
        description={`${patientName} - cases, care plans, and session records owned by the signed-in psychologist.`}
        actions={
          <Button
            variant="glass"
            render={<Link href="/dashboard/patients" />}
            nativeButton={false}
          >
            <ArrowLeftIcon data-icon="inline-start" />
            Patients
          </Button>
        }
      />

      {error ? <ErrorAlert message={error.message} /> : null}

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_24rem]">
        <div className="flex flex-col gap-6">
          <Card>
            <CardHeader>
              <CardTitle>Cases</CardTitle>
              <CardDescription>
                Lifecycle and presenting concerns for this patient.
              </CardDescription>
            </CardHeader>
            <CardContent className="flex flex-col gap-1">
              {(history.data?.cases ?? []).length === 0 ? (
                <EmptyHistoryState />
              ) : (
                history.data?.cases.map((clinicalCase, index, cases) => (
                  <div key={clinicalCase.id} className="flex flex-col">
                    <div className="grid gap-4 py-5 md:grid-cols-[minmax(0,1fr)_auto] md:items-start">
                      <div className="min-w-0">
                        <div className="flex flex-wrap items-center gap-2">
                          <StatusBadge status={clinicalCase.status} />
                          <span className="text-xs text-muted-foreground">
                            Opened {formatDateTime(clinicalCase.openedAt)}
                          </span>
                        </div>
                        <p className="mt-2 text-sm leading-6">
                          {clinicalCase.presentingConcern}
                        </p>
                      </div>
                      <Button
                        size="sm"
                        variant="glass"
                        render={<Link href="/dashboard/clinical" />}
                        nativeButton={false}
                      >
                        Open workspace
                      </Button>
                    </div>
                    {index < cases.length - 1 ? <Separator /> : null}
                  </div>
                ))
              )}
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Session records</CardTitle>
              <CardDescription>
                Clinical notes and attendance across all cases.
              </CardDescription>
            </CardHeader>
            <CardContent className="flex flex-col gap-1">
              {(history.data?.sessionRecords ?? []).length === 0 ? (
                <p className="rounded-[24px] bg-background/45 p-8 text-center text-sm text-muted-foreground">
                  No session records have been created yet.
                </p>
              ) : (
                [...(history.data?.sessionRecords ?? [])]
                  .sort(
                    (a, b) =>
                      new Date(b.sessionDate).getTime() -
                      new Date(a.sessionDate).getTime()
                  )
                  .map((record, index, records) => (
                    <SessionHistoryRow
                      key={record.id}
                      record={record}
                      showDivider={index < records.length - 1}
                    />
                  ))
              )}
            </CardContent>
          </Card>
        </div>

        <Card>
          <CardHeader>
            <CardTitle>Care plans</CardTitle>
            <CardDescription>
              Current treatment focus and goal progress.
            </CardDescription>
          </CardHeader>
          <CardContent className="flex flex-col gap-4">
            {(history.data?.carePlans ?? []).length === 0 ? (
              <p className="rounded-[24px] bg-background/45 p-8 text-center text-sm text-muted-foreground">
                No care plans for this patient yet.
              </p>
            ) : (
              history.data?.carePlans.map((plan) => (
                <CarePlanHistoryCard key={plan.id} carePlan={plan} />
              ))
            )}
          </CardContent>
        </Card>
      </div>
    </>
  );
}

function SessionHistoryRow({
  record,
  showDivider,
}: {
  record: SessionRecord;
  showDivider: boolean;
}) {
  return (
    <div className="flex flex-col">
      <div className="grid gap-4 py-5 md:grid-cols-[8rem_minmax(0,1fr)_auto] md:items-start">
        <div>
          <p className="text-sm font-semibold">
            {new Date(record.sessionDate).toLocaleTimeString([], {
              hour: "2-digit",
              minute: "2-digit",
            })}
          </p>
          <p className="text-xs text-muted-foreground">
            {new Date(record.sessionDate).toLocaleDateString([], {
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
          <p className="mt-2 line-clamp-2 text-sm leading-6 text-muted-foreground">
            {record.summary ?? "No summary has been added yet."}
          </p>
        </div>
        <span className="text-xs text-muted-foreground">
          {record.durationMinutes} min
        </span>
      </div>
      {showDivider ? <Separator /> : null}
    </div>
  );
}

function CarePlanHistoryCard({ carePlan }: { carePlan: CarePlan }) {
  return (
    <div className="rounded-[24px] bg-background/45 p-4">
      <p className="text-sm font-medium">{carePlan.plannedFrequency}</p>
      <p className="mt-2 line-clamp-3 text-sm leading-6 text-muted-foreground">
        {carePlan.therapeuticFocus}
      </p>
      <div className="mt-4 flex flex-col gap-3">
        {carePlan.goals.map((goal) => (
          <div key={goal.id}>
            <div className="flex items-center justify-between gap-3">
              <p className="truncate text-xs font-medium">{goal.description}</p>
              <span className="text-xs text-muted-foreground">
                {goal.progressPercentage}%
              </span>
            </div>
            <div className="mt-2 h-2 overflow-hidden rounded-full bg-muted">
              <div
                className="h-full rounded-full bg-clinical-blue"
                style={{ width: `${goal.progressPercentage}%` }}
              />
            </div>
          </div>
        ))}
      </div>
      <p className="mt-4 text-xs text-muted-foreground">
        Review {carePlan.reviewDate}
      </p>
    </div>
  );
}

function EmptyHistoryState() {
  return (
    <div className="flex min-h-64 flex-col items-center justify-center gap-4 rounded-[24px] bg-background/45 p-8 text-center">
      <span className="flex size-14 items-center justify-center rounded-full bg-secondary text-secondary-foreground">
        <ClipboardListIcon className="size-6" />
      </span>
      <div className="flex max-w-sm flex-col gap-2">
        <p className="text-base font-medium">No clinical case yet</p>
        <p className="text-sm leading-6 text-muted-foreground">
          Open the clinical workspace to create this patient&apos;s first case.
        </p>
      </div>
      <Button render={<Link href="/dashboard/clinical" />} nativeButton={false}>
        Create in clinical workspace
      </Button>
    </div>
  );
}
