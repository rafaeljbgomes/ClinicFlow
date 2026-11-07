import type { NextRequest } from "next/server";
import { jsonError } from "@/lib/server/bff";

// Rebuilds the query string from an allowlist, so a list route forwards only the paging and filter
// parameters its service documents (ADR-029) and nothing else the browser appended.
export function forwardedQuery(request: NextRequest, allowed: readonly string[]): string {
  const source = request.nextUrl.searchParams;
  const target = new URLSearchParams();
  for (const name of allowed) {
    for (const value of source.getAll(name)) {
      target.append(name, value);
    }
  }
  const query = target.toString();
  return query ? `?${query}` : "";
}

// The names of query parameters the list route does not document, each once.
export function unknownParameters(request: NextRequest, allowed: readonly string[]): string[] {
  const known = new Set(allowed);
  return [...new Set(request.nextUrl.searchParams.keys())].filter((name) => !known.has(name));
}

// A list route answers only the parameters it documents; anything else is a 400 rather than silently dropped.
// A typo then fails instead of returning an unfiltered list, and two requests that differ only in an
// unknown parameter can no longer get different answers because the list changed in between. The
// parameter names are not echoed back.
export function rejectUnknownParameters(request: NextRequest, allowed: readonly string[]) {
  return unknownParameters(request, allowed).length
    ? jsonError(400, "The request has a query parameter this list does not support", { code: "unsupported_parameter" })
    : null;
}
