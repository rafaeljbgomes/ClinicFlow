import { NextRequest } from "next/server";
import { proxyRequest } from "@/lib/server/bff";
import { forwardedQuery, rejectUnknownParameters } from "@/lib/server/list-query";

// The patient list is paged and searchable (ADR-029); only its documented parameters reach the service.
const LIST_PARAMETERS = ["q", "status", "consent", "createdFrom", "id", "page", "size"] as const;

export async function GET(request: NextRequest) {
  const rejected = rejectUnknownParameters(request, LIST_PARAMETERS);
  if (rejected) return rejected;
  return proxyRequest(request, "patient", `/patients${forwardedQuery(request, LIST_PARAMETERS)}`, { csrf: false });
}

export async function POST(request: NextRequest) {
  return proxyRequest(request, "patient", "/patients");
}
