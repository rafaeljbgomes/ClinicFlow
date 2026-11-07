"use client";

import { useDeferredValue, useState } from "react";
import useSWR from "swr";
import { SearchIcon } from "lucide-react";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { swrFetcher } from "@/lib/api-client";
import { listPath } from "@/lib/list-path";
import type { Page, Patient } from "@/lib/types";

// How many matches the picker offers at once; typing narrows the server-side search (ADR-029).
export const PATIENT_PICKER_SIZE = 20;

export function patientPickerPath(search: string): string {
  const q = search.trim();
  return listPath("/api/patients", { q: q || undefined, size: PATIENT_PICKER_SIZE });
}

// A patient select backed by the paged patient search: the first page of matches, by name.
export function PatientPicker({
  value,
  onChange,
  invalid = false,
}: {
  value: string;
  onChange: (patientId: string) => void;
  invalid?: boolean;
}) {
  const [search, setSearch] = useState("");
  const [selected, setSelected] = useState<Patient | null>(null);
  const deferredSearch = useDeferredValue(search);
  const matches = useSWR<Page<Patient>>(patientPickerPath(deferredSearch), swrFetcher, { keepPreviousData: true });
  const options = matches.data?.content ?? [];
  const chosen = options.find((patient) => patient.id === value) ?? (selected?.id === value ? selected : null);
  const hidden = (matches.data?.page.totalElements ?? 0) - options.length;

  return (
    <div className="flex flex-col gap-2">
      <label className="relative">
        <SearchIcon className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
        <Input
          type="search"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
          placeholder="Search patients"
          aria-label="Search patients"
          className="pl-9"
        />
      </label>
      <Select
        value={value}
        onValueChange={(next) => {
          const patientId = String(next ?? "");
          setSelected(options.find((patient) => patient.id === patientId) ?? null);
          onChange(patientId);
        }}
      >
        <SelectTrigger className="w-full" aria-invalid={invalid} aria-label="Patient">
          <SelectValue placeholder={matches.isLoading ? "Loading patients" : "Select patient"}>
            {chosen?.fullName}
          </SelectValue>
        </SelectTrigger>
        <SelectContent>
          <SelectGroup>
            {chosen && !options.some((patient) => patient.id === chosen.id) ? (
              <SelectItem value={chosen.id}>{chosen.fullName}</SelectItem>
            ) : null}
            {options.map((patient) => (
              <SelectItem key={patient.id} value={patient.id}>
                {patient.fullName}
              </SelectItem>
            ))}
          </SelectGroup>
        </SelectContent>
      </Select>
      <p className="text-xs text-muted-foreground" aria-live="polite">
        {matches.error
          ? "Patients could not be loaded."
          : options.length === 0 && !matches.isLoading
            ? deferredSearch.trim() ? "No patients match this search." : "No patients yet."
            : hidden > 0
              ? `Showing ${options.length} of ${options.length + hidden}. Type to narrow the list.`
              : null}
      </p>
    </div>
  );
}
