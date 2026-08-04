"use client";

import { useMemo, useState } from "react";
import useSWR from "swr";
import { MoreHorizontalIcon, PlusIcon, SearchIcon } from "lucide-react";
import { toast } from "sonner";
import { ErrorAlert } from "@/components/error-alert";
import { LoadingTable } from "@/components/loading-table";
import { PatientForm, type PatientFormValues } from "@/components/patient-form";
import { SectionHeader } from "@/components/section-header";
import { StatusBadge } from "@/components/status-badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
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
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { apiFetch, swrFetcher } from "@/lib/api-client";
import Link from "next/link";
import type { Patient, PatientStatus } from "@/lib/types";
import { formatDateTime, formatEnum } from "@/lib/format";
import { toPatientPayload } from "@/components/patient-form";

export default function PatientsPage() {
  const { data, error, isLoading, mutate } = useSWR<Patient[]>(
    "/api/patients",
    swrFetcher
  );
  const [createOpen, setCreateOpen] = useState(false);
  const [editing, setEditing] = useState<Patient | null>(null);
  const [archiving, setArchiving] = useState<Patient | null>(null);
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState<PatientStatus | "ALL">("ALL");
  const visiblePatients = useMemo(() => {
    const normalized = query.trim().toLowerCase();
    return (data ?? []).filter((patient) =>
      (status === "ALL" || patient.status === status) &&
      (!normalized || `${patient.fullName} ${patient.preferredName ?? ""} ${patient.email}`.toLowerCase().includes(normalized))
    );
  }, [data, query, status]);

  async function createPatient(values: PatientFormValues) {
    await apiFetch<Patient>("/api/patients", {
      method: "POST",
      body: toPatientPayload(values),
    });
    toast.success("Patient created");
    setCreateOpen(false);
    await mutate();
  }

  async function updatePatient(values: PatientFormValues) {
    if (!editing) return;
    await apiFetch<Patient>(`/api/patients/${editing.id}`, {
      method: "PUT",
      body: toPatientPayload(values, false),
    });
    toast.success("Patient updated");
    setEditing(null);
    await mutate();
  }

  async function changeStatus(patient: Patient, status: PatientStatus) {
    await apiFetch<Patient>(`/api/patients/${patient.id}/status`, {
      method: "PATCH",
      body: { status },
    });
    toast.success("Patient status updated");
    await mutate();
  }

  return (
    <>
      <SectionHeader
        title="Patients"
        description="Manage patient profile, consent, contact preference, and emergency details."
        actions={
          <Dialog open={createOpen} onOpenChange={setCreateOpen}>
            <DialogTrigger render={<Button />}>
              <PlusIcon data-icon="inline-start" />
              New patient
            </DialogTrigger>
            <DialogContent>
              <DialogHeader>
                <DialogTitle>Create patient</DialogTitle>
                <DialogDescription>
                  Store only the minimum information required for this prototype.
                </DialogDescription>
              </DialogHeader>
              <PatientForm submitLabel="Create patient" onSubmit={createPatient} />
            </DialogContent>
          </Dialog>
        }
      />

      {error ? <ErrorAlert message={error.message} /> : null}
      {isLoading ? (
        <LoadingTable />
      ) : (
        <Card>
          <CardHeader>
            <CardTitle>Patient registry</CardTitle>
            <CardDescription>
              Each record is scoped to the signed-in practice user.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <div className="mb-5 grid gap-3 sm:grid-cols-[minmax(0,1fr)_12rem]">
              <label className="relative">
                <SearchIcon className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
                <Input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search patients" className="pl-9" />
                <span className="sr-only">Search patients</span>
              </label>
              <Select value={status} onValueChange={(value) => setStatus(value as PatientStatus | "ALL")}>
                <SelectTrigger className="w-full"><SelectValue>{status === "ALL" ? "All statuses" : formatEnum(status)}</SelectValue></SelectTrigger>
                <SelectContent>
                  <SelectItem value="ALL">All statuses</SelectItem>
                  <SelectItem value="ACTIVE">Active</SelectItem>
                  <SelectItem value="INACTIVE">Inactive</SelectItem>
                  <SelectItem value="ARCHIVED">Archived</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Name</TableHead>
                  <TableHead>Contact</TableHead>
                  <TableHead>Consent</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead className="w-12">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {visiblePatients.map((patient) => (
                  <TableRow key={patient.id}>
                    <TableCell className="font-medium">
                      <div className="flex items-center gap-3">
                        <span className="flex size-9 items-center justify-center rounded-full bg-secondary text-xs font-semibold text-secondary-foreground">
                          {patient.fullName[0]?.toUpperCase() ?? "P"}
                        </span>
                        <span className="min-w-0">
                          <span className="block truncate">{patient.fullName}</span>
                          {patient.preferredName ? (
                            <span className="block truncate text-xs text-muted-foreground">
                              Prefers {patient.preferredName}
                            </span>
                          ) : null}
                        </span>
                      </div>
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      <div className="flex flex-col gap-0.5">
                        <span>{patient.email}</span>
                        <span className="text-xs">
                          {formatEnum(patient.contactPreference)}
                          {patient.phone ? ` - ${patient.phone}` : ""}
                        </span>
                      </div>
                    </TableCell>
                    <TableCell>
                      <div className="flex flex-col gap-1">
                        <StatusBadge status={patient.consentStatus} />
                        <span className="text-xs text-muted-foreground">
                          {formatDateTime(patient.consentUpdatedAt)}
                        </span>
                      </div>
                    </TableCell>
                    <TableCell>
                      <StatusBadge status={patient.status} />
                    </TableCell>
                    <TableCell>
                      <DropdownMenu>
                        <DropdownMenuTrigger render={<Button variant="ghost" size="icon-sm" />}>
                          <MoreHorizontalIcon />
                          <span className="sr-only">Patient actions</span>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end">
                          <DropdownMenuGroup>
                            <DropdownMenuLabel>Patient actions</DropdownMenuLabel>
                            <DropdownMenuItem onClick={() => setEditing(patient)}>
                              Edit
                            </DropdownMenuItem>
                            <DropdownMenuItem
                              render={
                                <Link href={`/dashboard/patients/${patient.id}/clinical-history`} />
                              }
                            >
                              Clinical history
                            </DropdownMenuItem>
                          </DropdownMenuGroup>
                          <DropdownMenuSeparator />
                          <DropdownMenuGroup>
                            <DropdownMenuItem onClick={() => changeStatus(patient, "ACTIVE")}>
                              Mark active
                            </DropdownMenuItem>
                            <DropdownMenuItem onClick={() => changeStatus(patient, "INACTIVE")}>
                              Mark inactive
                            </DropdownMenuItem>
                            <DropdownMenuItem onClick={() => setArchiving(patient)}>
                              Archive
                            </DropdownMenuItem>
                          </DropdownMenuGroup>
                        </DropdownMenuContent>
                      </DropdownMenu>
                    </TableCell>
                  </TableRow>
                ))}
                {visiblePatients.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={5} className="text-muted-foreground">
                      No patients match these filters.
                    </TableCell>
                  </TableRow>
                ) : null}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      )}

      <Sheet open={!!editing} onOpenChange={(open) => !open && setEditing(null)}>
        <SheetContent>
          <SheetHeader>
            <SheetTitle>Edit patient</SheetTitle>
            <SheetDescription>
              Email is immutable in this first prototype slice.
            </SheetDescription>
          </SheetHeader>
          {editing ? (
            <div className="px-4">
              <PatientForm
                patient={editing}
                submitLabel="Save changes"
                onSubmit={updatePatient}
              />
            </div>
          ) : null}
        </SheetContent>
      </Sheet>

      <Dialog open={!!archiving} onOpenChange={(open) => !open && setArchiving(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Archive patient?</DialogTitle>
            <DialogDescription>
              {archiving?.fullName} will be removed from active workflows. The clinical record is preserved.
            </DialogDescription>
          </DialogHeader>
          <div className="flex justify-end gap-3">
            <Button variant="outline" onClick={() => setArchiving(null)}>Keep active</Button>
            <Button variant="destructive" onClick={async () => {
              if (!archiving) return;
              await changeStatus(archiving, "ARCHIVED");
              setArchiving(null);
            }}>Archive patient</Button>
          </div>
        </DialogContent>
      </Dialog>
    </>
  );
}
