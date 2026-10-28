import type { NextRequest } from "next/server";

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
