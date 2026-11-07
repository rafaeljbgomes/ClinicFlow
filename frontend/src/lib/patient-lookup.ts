import { useMemo } from "react";
import useSWR from "swr";
import { swrFetcher } from "@/lib/api-client";
import { listPath } from "@/lib/list-path";
import type { Page, Patient } from "@/lib/types";

// The patient list is paged (ADR-029), so screens that show names next to appointments or cases look up just
// the patients on screen. The service accepts at most one page of ids per request.
export const PATIENT_LOOKUP_LIMIT = 100;

// A one-row page whose totalElements counts the psychologist's patients.
export const PATIENT_COUNT_PATH = listPath("/api/patients", { size: 1 });

// One request path per chunk of distinct ids, in a stable order so the SWR key only changes with the ids.
export function patientLookupPaths(ids: readonly (string | null | undefined)[]): string[] {
  const distinct = [...new Set(ids.filter((id): id is string => !!id))].sort();
  const paths: string[] = [];
  for (let start = 0; start < distinct.length; start += PATIENT_LOOKUP_LIMIT) {
    const chunk = distinct.slice(start, start + PATIENT_LOOKUP_LIMIT);
    paths.push(listPath("/api/patients", { id: chunk, size: chunk.length }));
  }
  return paths;
}

async function fetchPatients(paths: readonly string[]): Promise<Patient[]> {
  const pages = await Promise.all(paths.map((path) => swrFetcher<Page<Patient>>(path)));
  return pages.flatMap((page) => page.content);
}

// Patients by id for the given ids; ids that are not the caller's patients are simply absent from the map.
export function usePatientLookup(ids: readonly (string | null | undefined)[]) {
  const paths = patientLookupPaths(ids);
  const key = paths.length ? ["patient-lookup", ...paths] : null;
  const lookup = useSWR<Patient[]>(key, ([, ...urls]: string[]) => fetchPatients(urls));
  const byId = useMemo(
    () => new Map((lookup.data ?? []).map((patient) => [patient.id, patient])),
    [lookup.data]
  );
  return { byId, error: lookup.error as Error | undefined, isLoading: lookup.isLoading, mutate: lookup.mutate };
}
