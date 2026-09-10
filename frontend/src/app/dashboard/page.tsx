"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import useSWR from "swr";
import {
  ArrowRightIcon,
  CalendarClockIcon,
  CheckCircle2Icon,
  ClipboardListIcon,
  MessageCircleIcon,
  PlusIcon,
  UserRoundPlusIcon,
} from "lucide-react";
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
import { formatDateTime, formatEnum } from "@/lib/format";
import type { Appointment, ClinicalCase, Notification, Patient } from "@/lib/types";

export default function DashboardPage() {
  const [renderedAt] = useState(() => Date.now());
  const [todayText] = useState(() => todayLabel());
  const patients = useSWR<Patient[]>("/api/patients", swrFetcher);
  const appointments = useSWR<Appointment[]>("/api/appointments", swrFetcher);
  const clinicalCases = useSWR<ClinicalCase[]>("/api/clinical-cases", swrFetcher);
  const notifications = useSWR<Notification[]>("/api/notifications", swrFetcher);

  const isLoading =
    patients.isLoading ||
    appointments.isLoading ||
    clinicalCases.isLoading ||
    notifications.isLoading;
  const error =
    patients.error ??
    appointments.error ??
    clinicalCases.error ??
    notifications.error;

  const patientById = useMemo(
    () => new Map((patients.data ?? []).map((patient) => [patient.id, patient])),
    [patients.data]
  );

  const orderedAppointments = useMemo(
    () =>
      [...(appointments.data ?? [])].sort(
        (a, b) => new Date(a.scheduledAt).getTime() - new Date(b.scheduledAt).getTime()
      ),
    [appointments.data]
  );

  const upcomingAppointments = useMemo(() => {
    return orderedAppointments.filter(
      (appointment) =>
        appointment.status !== "CANCELLED" &&
        new Date(appointment.scheduledAt).getTime() >= renderedAt
    );
  }, [orderedAppointments, renderedAt]);

  const actionItems = useMemo(() => {
    const pendingConsent = (patients.data ?? [])
      .filter((patient) => patient.consentStatus === "PENDING")
      .slice(0, 2)
      .map((patient) => ({
        id: `consent-${patient.id}`,
        title: "Consent pending",
        description: patient.fullName,
      }));

    const inactivePatients = (patients.data ?? [])
      .filter((patient) => patient.status === "INACTIVE")
      .slice(0, 2)
      .map((patient) => ({
        id: `inactive-${patient.id}`,
        title: "Review follow-up",
        description: patient.fullName,
      }));

    const intakeCases = (clinicalCases.data ?? [])
      .filter((clinicalCase) => clinicalCase.status === "INTAKE")
      .slice(0, 2)
      .map((clinicalCase) => ({
        id: `case-${clinicalCase.id}`,
        title: "Case intake",
        description: patientById.get(clinicalCase.patientId)?.fullName ?? "Patient",
      }));

    return [...pendingConsent, ...inactivePatients, ...intakeCases].slice(0, 3);
  }, [clinicalCases.data, patientById, patients.data]);

  if (isLoading) {
    return <LoadingTable />;
  }

  return (
    <>
      <SectionHeader
        title="Today"
        description={`${todayText} — appointments, patients, and follow-ups in one considered view.`}
        actions={
          <>
            <Button variant="glass" render={<Link href="/dashboard/patients" />} nativeButton={false}>
              <UserRoundPlusIcon data-icon="inline-start" />
              New patient
            </Button>
            <Button render={<Link href="/dashboard/appointments" />} nativeButton={false}>
              <PlusIcon data-icon="inline-start" />
              Schedule
            </Button>
            <Button variant="glass" render={<Link href="/dashboard/clinical" />} nativeButton={false}>
              <ClipboardListIcon data-icon="inline-start" />
              Clinical
            </Button>
          </>
        }
      />

      {error ? <ErrorAlert message={error.message} /> : null}

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_22rem]">
        <Card>
          <CardHeader>
            <CardTitle>Session timeline</CardTitle>
            <CardDescription>A clear view of the day and week ahead.</CardDescription>
          </CardHeader>
          <CardContent className="flex flex-col gap-1">
            {upcomingAppointments.length === 0 ? (
              <EmptyPracticeState />
            ) : (
              upcomingAppointments.slice(0, 8).map((appointment, index) => {
                const patient = patientById.get(appointment.patientId);
                return (
                  <AppointmentRow
                    key={appointment.id}
                    appointment={appointment}
                    patient={patient}
                    showDivider={index < upcomingAppointments.length - 1}
                  />
                );
              })
            )}
          </CardContent>
        </Card>

        <div className="flex flex-col gap-6">
          <PracticePulse
            appointments={upcomingAppointments.length}
            cases={clinicalCases.data?.length ?? 0}
            messages={notifications.data?.length ?? 0}
            patients={patients.data?.length ?? 0}
          />

          <Card size="sm">
            <CardHeader>
              <CardTitle>Follow-ups</CardTitle>
              <CardDescription>Small practice tasks worth keeping visible.</CardDescription>
            </CardHeader>
            <CardContent className="flex flex-col gap-3">
              {actionItems.length === 0 ? (
                <p className="text-sm text-muted-foreground">No follow-ups need attention.</p>
              ) : (
                actionItems.map((item) => (
                  <div key={item.id} className="flex items-start gap-3 rounded-xl border border-border bg-muted/35 p-4">
                    <CheckCircle2Icon className="mt-0.5 size-4 text-clinical-blue" />
                    <div className="min-w-0">
                      <p className="text-sm font-medium">{item.title}</p>
                      <p className="truncate text-xs text-muted-foreground">{item.description}</p>
                    </div>
                  </div>
                ))
              )}
            </CardContent>
          </Card>

          <Card size="sm">
            <CardHeader>
              <CardTitle>Recent messages</CardTitle>
              <CardDescription>Practice notifications in plain language.</CardDescription>
            </CardHeader>
            <CardContent className="flex flex-col gap-3">
              {(notifications.data ?? []).slice(-3).reverse().map((message) => (
                <div key={message.id} className="flex items-start gap-3 rounded-xl border border-border bg-muted/35 p-4">
                  <span className="flex size-8 shrink-0 items-center justify-center rounded-full bg-secondary text-secondary-foreground">
                    <MessageCircleIcon className="size-4" />
                  </span>
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium">{message.subject}</p>
                    <p className="text-xs text-muted-foreground">{formatDateTime(message.createdAt)}</p>
                  </div>
                  <StatusBadge status={message.status} />
                </div>
              ))}
              {(notifications.data ?? []).length === 0 ? (
                <p className="text-sm text-muted-foreground">No messages yet.</p>
              ) : null}
            </CardContent>
          </Card>
        </div>
      </div>
    </>
  );
}

function AppointmentRow({
  appointment,
  patient,
  showDivider,
}: {
  appointment: Appointment;
  patient?: Patient;
  showDivider: boolean;
}) {
  return (
    <div className="flex flex-col">
      <div className="grid gap-4 py-5 sm:grid-cols-[5rem_3rem_minmax(0,1fr)_auto] sm:items-center">
        <div>
          <p className="text-sm font-semibold">
            {new Date(appointment.scheduledAt).toLocaleTimeString("en-GB", {
              hour: "2-digit",
              minute: "2-digit",
            })}
          </p>
          <p className="text-xs text-muted-foreground">
            {new Date(appointment.scheduledAt).toLocaleDateString("en-GB", {
              month: "short",
              day: "numeric",
            })}
          </p>
        </div>

        <span className="flex size-11 items-center justify-center rounded-full bg-secondary text-sm font-semibold text-secondary-foreground">
          {patient?.fullName?.[0]?.toUpperCase() ?? "P"}
        </span>

        <div className="min-w-0">
          <p className="truncate text-base font-medium">{patient?.fullName ?? "Patient"}</p>
          <p className="text-sm text-muted-foreground">{formatEnum(appointment.type)} session</p>
        </div>

        <div className="flex items-center gap-2">
          <StatusBadge status={appointment.status} />
          <Button variant="ghost" size="icon-sm" render={<Link href="/dashboard/appointments" />} nativeButton={false}>
            <ArrowRightIcon />
            <span className="sr-only">Open appointment</span>
          </Button>
        </div>
      </div>
      {showDivider ? <Separator /> : null}
    </div>
  );
}

function PracticePulse({
  appointments,
  cases,
  messages,
  patients,
}: {
  appointments: number;
  cases: number;
  messages: number;
  patients: number;
}) {
  const items = [
    { label: "Patients", value: patients },
    { label: "Upcoming", value: appointments },
    { label: "Cases", value: cases },
    { label: "Messages", value: messages },
  ];

  return (
    <Card size="sm">
      <CardHeader>
        <CardTitle>Practice pulse</CardTitle>
        <CardDescription>Only the essentials for the clinical day.</CardDescription>
      </CardHeader>
      <CardContent className="grid grid-cols-2 gap-3">
        {items.map((item) => (
          <div key={item.label} className="rounded-xl border border-border bg-muted/35 p-4">
            <span className="block text-xs font-medium text-muted-foreground">{item.label}</span>
            <span className="mt-2 block text-3xl font-semibold tracking-tight text-foreground">{item.value}</span>
          </div>
        ))}
      </CardContent>
    </Card>
  );
}

function EmptyPracticeState() {
  return (
    <div className="flex min-h-72 flex-col items-center justify-center gap-4 rounded-xl border border-border bg-muted/35 p-8 text-center">
      <span className="flex size-14 items-center justify-center rounded-full bg-secondary text-secondary-foreground">
        <CalendarClockIcon className="size-6" />
      </span>
      <div className="flex max-w-sm flex-col gap-2">
        <p className="text-base font-medium">No upcoming sessions</p>
        <p className="text-sm leading-6 text-muted-foreground">
          The workspace will fill in as appointments are scheduled.
        </p>
      </div>
    </div>
  );
}

function todayLabel() {
  return new Date().toLocaleDateString("en-GB", {
    weekday: "long",
    month: "long",
    day: "numeric",
  }).replace(/^./, (letter) => letter.toUpperCase());
}
