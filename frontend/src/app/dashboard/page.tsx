"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import useSWR from "swr";
import {
  ArrowRightIcon,
  CalendarClockIcon,
  CircleAlertIcon,
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
import { deriveFollowUps, practiceMetrics, type FollowUp, type PracticeMetric } from "@/lib/dashboard-insights";
import { formatDateTime, formatEnum } from "@/lib/format";
import type { Appointment, ClinicalCase, Notification, Patient } from "@/lib/types";

export default function DashboardPage() {
  const [renderedAt] = useState(() => Date.now());
  const [todayText] = useState(() => todayLabel());
  const patients = useSWR<Patient[]>("/api/patients", swrFetcher);
  const appointments = useSWR<Appointment[]>("/api/appointments", swrFetcher);
  const clinicalCases = useSWR<ClinicalCase[]>("/api/clinical-cases", swrFetcher);
  const notifications = useSWR<Notification[]>("/api/notifications", swrFetcher);

  const isLoading = patients.isLoading || appointments.isLoading || clinicalCases.isLoading || notifications.isLoading;
  const error = patients.error ?? appointments.error ?? clinicalCases.error ?? notifications.error;
  const patientById = useMemo(
    () => new Map((patients.data ?? []).map((patient) => [patient.id, patient])),
    [patients.data]
  );
  const upcomingAppointments = useMemo(
    () => [...(appointments.data ?? [])]
      .filter((appointment) => appointment.status !== "CANCELLED" && new Date(appointment.scheduledAt).getTime() >= renderedAt)
      .sort((a, b) => new Date(a.scheduledAt).getTime() - new Date(b.scheduledAt).getTime()),
    [appointments.data, renderedAt]
  );
  const followUps = useMemo(
    () => deriveFollowUps(patients.data ?? [], clinicalCases.data ?? []),
    [clinicalCases.data, patients.data]
  );
  const metrics = useMemo(
    () => practiceMetrics(patients.data ?? [], appointments.data ?? [], clinicalCases.data ?? [], notifications.data ?? [], renderedAt),
    [appointments.data, clinicalCases.data, notifications.data, patients.data, renderedAt]
  );

  if (isLoading) return <LoadingTable />;

  return (
    <>
      <SectionHeader
        title="Today"
        description={`${todayText} — appointments, patients, and clinical follow-ups in one considered view.`}
        actions={
          <>
            <Button variant="outline" render={<Link href="/dashboard/patients" />} nativeButton={false}>
              <UserRoundPlusIcon data-icon="inline-start" />
              New patient
            </Button>
            <Button render={<Link href="/dashboard/appointments" />} nativeButton={false}>
              <PlusIcon data-icon="inline-start" />
              Schedule
            </Button>
            <Button variant="ghost" render={<Link href="/dashboard/clinical" />} nativeButton={false}>
              <ClipboardListIcon data-icon="inline-start" />
              Clinical
            </Button>
          </>
        }
      />

      {error ? <ErrorAlert message={error.message} /> : null}

      <div className="grid items-start gap-6 xl:grid-cols-[minmax(0,1fr)_22rem]">
        <Card className="self-start">
          <CardHeader>
            <CardTitle>Upcoming sessions</CardTitle>
            <CardDescription>A focused view of the next appointments in your practice.</CardDescription>
          </CardHeader>
          <CardContent className="flex flex-col">
            {upcomingAppointments.length === 0 ? (
              <EmptyPracticeState />
            ) : (
              upcomingAppointments.slice(0, 8).map((appointment, index) => (
                <AppointmentRow
                  key={appointment.id}
                  appointment={appointment}
                  patient={patientById.get(appointment.patientId)}
                  showDivider={index < Math.min(upcomingAppointments.length, 8) - 1}
                />
              ))
            )}
          </CardContent>
        </Card>

        <Card size="sm" tone="quiet" className="self-start border border-border/60 bg-card">
          <CardHeader>
            <CardTitle>Follow-ups</CardTitle>
            <CardDescription>Items that benefit from a closer look.</CardDescription>
          </CardHeader>
          <CardContent className="flex flex-col">
            {followUps.length === 0 ? (
              <p className="py-2 text-sm text-muted-foreground">Nothing needs attention right now.</p>
            ) : (
              followUps.map((item, index) => (
                <FollowUpRow key={item.id} item={item} showDivider={index < followUps.length - 1} />
              ))
            )}
          </CardContent>
        </Card>
      </div>

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(20rem,0.7fr)]">
        <PracticePulse metrics={metrics} />
        <RecentMessages messages={notifications.data ?? []} />
      </div>
    </>
  );
}

function AppointmentRow({ appointment, patient, showDivider }: { appointment: Appointment; patient?: Patient; showDivider: boolean }) {
  return (
    <div className="flex flex-col">
      <div className="grid gap-4 py-4 sm:grid-cols-[5.25rem_2.75rem_minmax(0,1fr)_auto] sm:items-center">
        <div>
          <p className="text-sm font-semibold tabular-nums">
            {new Date(appointment.scheduledAt).toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit" })}
          </p>
          <p className="type-metadata text-muted-foreground">
            {new Date(appointment.scheduledAt).toLocaleDateString("en-GB", { month: "short", day: "numeric" })}
          </p>
        </div>
        <span className="flex size-10 items-center justify-center rounded-full bg-secondary text-sm font-semibold text-secondary-foreground">
          {patient?.fullName?.[0]?.toUpperCase() ?? "P"}
        </span>
        <div className="min-w-0">
          <p className="break-words text-base font-semibold tracking-tight">{patient?.preferredName || patient?.fullName || "Patient"}</p>
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

function FollowUpRow({ item, showDivider }: { item: FollowUp; showDivider: boolean }) {
  const iconClass = item.tone === "danger" ? "text-status-danger-fg" : item.tone === "warning" ? "text-status-warning-fg" : "text-status-info-fg";
  return (
    <div className="flex flex-col">
      <Link href={item.href} className="group flex items-start gap-3 rounded-xl py-3 outline-none transition-colors hover:bg-secondary/45 focus-visible:ring-3 focus-visible:ring-ring/25">
        <CircleAlertIcon className={`mt-0.5 size-4 shrink-0 ${iconClass}`} />
        <span className="min-w-0 flex-1">
          <span className="block text-sm font-semibold">{item.title}</span>
          <span className="block break-words text-xs text-muted-foreground">{item.description}</span>
        </span>
        <ArrowRightIcon className="mt-0.5 size-4 shrink-0 text-muted-foreground transition-transform group-hover:translate-x-0.5" />
      </Link>
      {showDivider ? <Separator /> : null}
    </div>
  );
}

function PracticePulse({ metrics }: { metrics: PracticeMetric[] }) {
  return (
    <Card size="sm" tone="inset">
      <CardHeader>
        <CardTitle>Practice pulse</CardTitle>
        <CardDescription>A little context for the clinical day.</CardDescription>
      </CardHeader>
      <CardContent className="grid divide-y divide-border/70 sm:grid-cols-4 sm:divide-x sm:divide-y-0">
        {metrics.map((item) => (
          <div key={item.label} className="py-4 first:pt-0 last:pb-0 sm:px-4 sm:py-0 sm:first:pl-0 sm:last:pr-0">
            <span className="type-label text-muted-foreground">{item.label}</span>
            <span className="mt-2 block text-3xl font-semibold tracking-[-0.04em]">{item.value}</span>
            <span className="mt-1 block text-xs leading-5 text-muted-foreground">{item.context}</span>
          </div>
        ))}
      </CardContent>
    </Card>
  );
}

function RecentMessages({ messages }: { messages: Notification[] }) {
  const recent = [...messages].slice(-3).reverse();
  return (
    <Card size="sm" tone="quiet" className="border border-border/60 bg-card">
      <CardHeader>
        <CardTitle>Recent messages</CardTitle>
        <CardDescription>Practice updates in plain language.</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col">
        {recent.length === 0 ? (
          <p className="py-2 text-sm text-muted-foreground">No messages yet.</p>
        ) : recent.map((message, index) => (
          <div key={message.id} className="flex flex-col">
            <div className="flex items-start gap-3 py-3">
              <span className="flex size-9 shrink-0 items-center justify-center rounded-full bg-secondary text-secondary-foreground">
                <MessageCircleIcon className="size-4" />
              </span>
              <div className="min-w-0 flex-1">
                <p className="break-words text-sm font-semibold">{message.subject}</p>
                <p className="mt-0.5 text-xs text-muted-foreground">{formatDateTime(message.createdAt)}</p>
              </div>
              <StatusBadge status={message.status} />
            </div>
            {index < recent.length - 1 ? <Separator /> : null}
          </div>
        ))}
      </CardContent>
    </Card>
  );
}

function EmptyPracticeState() {
  return (
    <div className="flex min-h-48 flex-col items-center justify-center gap-3 py-8 text-center">
      <span className="flex size-11 items-center justify-center rounded-full bg-secondary text-secondary-foreground">
        <CalendarClockIcon className="size-5" />
      </span>
      <div className="flex max-w-sm flex-col gap-1">
        <p className="text-base font-semibold">No upcoming sessions</p>
        <p className="text-sm leading-6 text-muted-foreground">Schedule a session when you are ready to plan the next clinical day.</p>
      </div>
    </div>
  );
}

function todayLabel() {
  return new Date().toLocaleDateString("en-GB", { weekday: "long", month: "long", day: "numeric" }).replace(/^./, (letter) => letter.toUpperCase());
}
