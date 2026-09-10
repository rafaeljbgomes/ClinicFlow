"use client";

import { useMemo, useState } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { Controller, useForm, useWatch } from "react-hook-form";
import useSWR from "swr";
import { z } from "zod";
import { MoreHorizontalIcon, PlusIcon } from "lucide-react";
import { toast } from "sonner";
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
  DialogTrigger,
} from "@/components/ui/dialog";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
  Field,
  FieldError,
  FieldGroup,
  FieldLabel,
} from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Separator } from "@/components/ui/separator";
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { apiFetch, swrFetcher } from "@/lib/api-client";
import { formatDateTime, formatEnum } from "@/lib/format";
import type { Appointment, AppointmentStatus, AppointmentType, Patient } from "@/lib/types";

const scheduleSchema = z.object({
  patientId: z.string().uuid("Select a patient"),
  scheduledAt: z.string().min(1, "Date and time are required"),
  type: z.enum(["ONLINE", "IN_PERSON"]),
});

const rescheduleSchema = z.object({
  newDate: z.string().min(1, "Date and time are required"),
});

const cancelSchema = z.object({
  reason: z.string().min(3, "Reason is required").max(500),
});

type ScheduleValues = z.infer<typeof scheduleSchema>;
type RescheduleValues = z.infer<typeof rescheduleSchema>;
type CancelValues = z.infer<typeof cancelSchema>;

export default function AppointmentsPage() {
  const appointments = useSWR<Appointment[]>("/api/appointments", swrFetcher);
  const patients = useSWR<Patient[]>("/api/patients", swrFetcher);
  const [createOpen, setCreateOpen] = useState(false);
  const [rescheduling, setRescheduling] = useState<Appointment | null>(null);
  const [cancelling, setCancelling] = useState<Appointment | null>(null);
  const [statusFilter, setStatusFilter] = useState<AppointmentStatus | "ALL">("ALL");

  const patientById = useMemo(
    () => new Map((patients.data ?? []).map((patient) => [patient.id, patient])),
    [patients.data]
  );
  const visibleAppointments = useMemo(
    () => (appointments.data ?? []).filter((item) => statusFilter === "ALL" || item.status === statusFilter),
    [appointments.data, statusFilter]
  );

  async function schedule(values: ScheduleValues) {
    await apiFetch<Appointment>("/api/appointments", {
      method: "POST",
      body: {
        patientId: values.patientId,
        scheduledAt: new Date(values.scheduledAt).toISOString(),
        type: values.type,
      },
    });
    toast.success("Appointment scheduled");
    setCreateOpen(false);
    await Promise.all([appointments.mutate(), patients.mutate()]);
  }

  async function complete(appointment: Appointment) {
    await apiFetch<Appointment>(`/api/appointments/${appointment.id}/complete`, {
      method: "PATCH",
    });
    toast.success("Appointment completed. Review the clinical record for session notes.");
    await appointments.mutate();
  }

  return (
    <>
      <SectionHeader
        title="Calendar"
        description="Schedule sessions and keep the clinical day easy to scan."
        actions={
          <Dialog open={createOpen} onOpenChange={setCreateOpen}>
            <DialogTrigger render={<Button disabled={(patients.data ?? []).length === 0} />}>
              <PlusIcon data-icon="inline-start" />
              Schedule session
            </DialogTrigger>
            <DialogContent>
              <DialogHeader>
                <DialogTitle>Schedule session</DialogTitle>
                <DialogDescription>
                  Choose the patient, time, and session format.
                </DialogDescription>
              </DialogHeader>
              <ScheduleForm patients={patients.data ?? []} onSubmit={schedule} />
            </DialogContent>
          </Dialog>
        }
      />

      {appointments.error || patients.error ? (
        <ErrorAlert
          message={(appointments.error ?? patients.error)?.message ?? "Unable to load data"}
        />
      ) : null}

      {appointments.isLoading || patients.isLoading ? (
        <LoadingTable />
      ) : (
        <Card>
          <CardHeader>
            <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
              <div><CardTitle>Session schedule</CardTitle><CardDescription>A continuous view of upcoming and past appointments.</CardDescription></div>
              <Select value={statusFilter} onValueChange={(value) => setStatusFilter(value as AppointmentStatus | "ALL")}>
                <SelectTrigger className="w-full sm:w-48"><SelectValue>{statusFilter === "ALL" ? "All sessions" : formatEnum(statusFilter)}</SelectValue></SelectTrigger>
                <SelectContent>
                  <SelectItem value="ALL">All sessions</SelectItem>
                  <SelectItem value="SCHEDULED">Scheduled</SelectItem>
                  <SelectItem value="RESCHEDULED">Rescheduled</SelectItem>
                  <SelectItem value="COMPLETED">Completed</SelectItem>
                  <SelectItem value="CANCELLED">Cancelled</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </CardHeader>
          <CardContent className="flex flex-col">
            {visibleAppointments.length === 0 ? (
              <div className="flex min-h-64 items-center justify-center rounded-xl border border-border bg-muted/35 p-8 text-center text-sm text-muted-foreground">
                No sessions match this view.
              </div>
            ) : (
              [...visibleAppointments]
                .sort(
                  (a, b) =>
                    new Date(a.scheduledAt).getTime() - new Date(b.scheduledAt).getTime()
                )
                .map((appointment, index, sorted) => (
                  <SessionRow
                    key={appointment.id}
                    appointment={appointment}
                    patient={patientById.get(appointment.patientId)}
                    showDivider={index < sorted.length - 1}
                    onCancel={() => setCancelling(appointment)}
                    onComplete={() => complete(appointment)}
                    onReschedule={() => setRescheduling(appointment)}
                  />
                ))
            )}
          </CardContent>
        </Card>
      )}

      <RescheduleDialog
        appointment={rescheduling}
        onOpenChange={(open) => !open && setRescheduling(null)}
        onDone={async () => {
          setRescheduling(null);
          await appointments.mutate();
        }}
      />
      <CancelDialog
        appointment={cancelling}
        onOpenChange={(open) => !open && setCancelling(null)}
        onDone={async () => {
          setCancelling(null);
          await appointments.mutate();
        }}
      />
    </>
  );
}

function SessionRow({
  appointment,
  patient,
  showDivider,
  onCancel,
  onComplete,
  onReschedule,
}: {
  appointment: Appointment;
  patient?: Patient;
  showDivider: boolean;
  onCancel: () => void;
  onComplete: () => void;
  onReschedule: () => void;
}) {
  return (
    <div className="flex flex-col">
      <div className="grid gap-4 py-5 md:grid-cols-[7rem_3rem_minmax(0,1fr)_auto] md:items-center">
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
          <p className="truncate text-base font-medium">{patient?.fullName ?? appointment.patientId}</p>
          <p className="text-sm text-muted-foreground">
            {formatEnum(appointment.type)} - {formatDateTime(appointment.scheduledAt)}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <StatusBadge status={appointment.status} />
          {appointment.status === "SCHEDULED" || appointment.status === "RESCHEDULED" ? <DropdownMenu>
            <DropdownMenuTrigger render={<Button variant="ghost" size="icon-sm" />}>
              <MoreHorizontalIcon />
              <span className="sr-only">Session actions</span>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuGroup>
                <DropdownMenuLabel>Session actions</DropdownMenuLabel>
                <DropdownMenuItem onClick={onReschedule}>Reschedule</DropdownMenuItem>
                <DropdownMenuItem onClick={onComplete}>Mark complete</DropdownMenuItem>
              </DropdownMenuGroup>
              <DropdownMenuSeparator />
              <DropdownMenuGroup>
                <DropdownMenuItem variant="destructive" onClick={onCancel}>
                  Cancel
                </DropdownMenuItem>
              </DropdownMenuGroup>
            </DropdownMenuContent>
          </DropdownMenu> : null}
        </div>
      </div>
      {showDivider ? <Separator /> : null}
    </div>
  );
}

function ScheduleForm({
  patients,
  onSubmit,
}: {
  patients: Patient[];
  onSubmit: (values: ScheduleValues) => Promise<void>;
}) {
  const form = useForm<ScheduleValues>({
    resolver: zodResolver(scheduleSchema),
    defaultValues: {
      patientId: patients[0]?.id ?? "",
      scheduledAt: "",
      type: "ONLINE",
    },
  });
  const selectedPatientId = useWatch({
    control: form.control,
    name: "patientId",
  });
  const selectedPatientName = patients.find(
    (patient) => patient.id === selectedPatientId
  )?.fullName;

  return (
    <form onSubmit={form.handleSubmit(onSubmit)}>
      <FieldGroup>
        <Controller
          control={form.control}
          name="patientId"
          render={({ field }) => (
            <Field data-invalid={!!form.formState.errors.patientId}>
              <FieldLabel>Patient</FieldLabel>
              <Select value={field.value} onValueChange={field.onChange}>
                <SelectTrigger className="w-full">
                  <SelectValue placeholder="Select patient">
                    {selectedPatientName}
                  </SelectValue>
                </SelectTrigger>
                <SelectContent>
                  <SelectGroup>
                    {patients.map((patient) => (
                      <SelectItem key={patient.id} value={patient.id}>
                        {patient.fullName}
                      </SelectItem>
                    ))}
                  </SelectGroup>
                </SelectContent>
              </Select>
              <FieldError>{form.formState.errors.patientId?.message}</FieldError>
            </Field>
          )}
        />

        <Field data-invalid={!!form.formState.errors.scheduledAt}>
          <FieldLabel htmlFor="scheduledAt">Date and time</FieldLabel>
          <Input
            id="scheduledAt"
            type="datetime-local"
            aria-invalid={!!form.formState.errors.scheduledAt}
            {...form.register("scheduledAt")}
          />
          <FieldError>{form.formState.errors.scheduledAt?.message}</FieldError>
        </Field>

        <Controller
          control={form.control}
          name="type"
          render={({ field }) => (
            <Field data-invalid={!!form.formState.errors.type}>
              <FieldLabel>Type</FieldLabel>
              <Select
                value={field.value}
                onValueChange={(value) => field.onChange(value as AppointmentType)}
              >
                <SelectTrigger className="w-full">
                  <SelectValue placeholder="Select type">
                    {formatEnum(field.value)}
                  </SelectValue>
                </SelectTrigger>
                <SelectContent>
                  <SelectGroup>
                    <SelectItem value="ONLINE">Online</SelectItem>
                    <SelectItem value="IN_PERSON">In person</SelectItem>
                  </SelectGroup>
                </SelectContent>
              </Select>
              <FieldError>{form.formState.errors.type?.message}</FieldError>
            </Field>
          )}
        />

            <Button type="submit" disabled={form.formState.isSubmitting}>
          {form.formState.isSubmitting ? "Scheduling" : "Schedule session"}
        </Button>
      </FieldGroup>
    </form>
  );
}

function RescheduleDialog({
  appointment,
  onOpenChange,
  onDone,
}: {
  appointment: Appointment | null;
  onOpenChange: (open: boolean) => void;
  onDone: () => Promise<void>;
}) {
  const form = useForm<RescheduleValues>({
    resolver: zodResolver(rescheduleSchema),
    values: { newDate: "" },
  });

  async function submit(values: RescheduleValues) {
    if (!appointment) return;
    await apiFetch<Appointment>(`/api/appointments/${appointment.id}/reschedule`, {
      method: "PATCH",
      body: { newDate: new Date(values.newDate).toISOString() },
    });
    toast.success("Appointment rescheduled");
    await onDone();
  }

  return (
    <Dialog open={!!appointment} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Reschedule session</DialogTitle>
          <DialogDescription>
            Choose a new date and time for this session.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={form.handleSubmit(submit)}>
          <FieldGroup>
            <Field data-invalid={!!form.formState.errors.newDate}>
              <FieldLabel htmlFor="newDate">New date and time</FieldLabel>
              <Input
                id="newDate"
                type="datetime-local"
                aria-invalid={!!form.formState.errors.newDate}
                {...form.register("newDate")}
              />
              <FieldError>{form.formState.errors.newDate?.message}</FieldError>
            </Field>
            <Button type="submit" disabled={form.formState.isSubmitting}>
              {form.formState.isSubmitting ? "Saving" : "Reschedule"}
            </Button>
          </FieldGroup>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function CancelDialog({
  appointment,
  onOpenChange,
  onDone,
}: {
  appointment: Appointment | null;
  onOpenChange: (open: boolean) => void;
  onDone: () => Promise<void>;
}) {
  const form = useForm<CancelValues>({
    resolver: zodResolver(cancelSchema),
    values: { reason: "" },
  });

  async function submit(values: CancelValues) {
    if (!appointment) return;
    await apiFetch<Appointment>(`/api/appointments/${appointment.id}/cancel`, {
      method: "PATCH",
      body: values,
    });
    toast.success("Appointment cancelled");
    await onDone();
  }

  return (
    <Dialog open={!!appointment} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Cancel session</DialogTitle>
          <DialogDescription>
            Store a short reason so the appointment history stays clear.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={form.handleSubmit(submit)}>
          <FieldGroup>
            <Field data-invalid={!!form.formState.errors.reason}>
              <FieldLabel htmlFor="reason">Reason</FieldLabel>
              <Input
                id="reason"
                aria-invalid={!!form.formState.errors.reason}
                {...form.register("reason")}
              />
              <FieldError>{form.formState.errors.reason?.message}</FieldError>
            </Field>
            <Button
              type="submit"
              variant="destructive"
              disabled={form.formState.isSubmitting}
            >
              {form.formState.isSubmitting ? "Cancelling" : "Cancel session"}
            </Button>
          </FieldGroup>
        </form>
      </DialogContent>
    </Dialog>
  );
}
