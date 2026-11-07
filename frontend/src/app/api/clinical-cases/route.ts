import { NextRequest } from "next/server";
import { proxyRequest } from "@/lib/server/bff";
import { forwardedQuery, rejectUnknownParameters } from "@/lib/server/list-query";

const LIST_PARAMETERS = ["patientId", "status", "page", "size"] as const;

export async function GET(request: NextRequest) {
  const rejected = rejectUnknownParameters(request, LIST_PARAMETERS);
  if (rejected) return rejected;
  return proxyRequest(request, "clinical", `/clinical-cases${forwardedQuery(request, LIST_PARAMETERS)}`, {
    csrf: false,
  });
}

export async function POST(request: NextRequest) {
  return proxyRequest(request, "clinical", "/clinical-cases");
}
