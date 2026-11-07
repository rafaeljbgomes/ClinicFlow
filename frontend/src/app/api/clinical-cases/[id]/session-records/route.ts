import { NextRequest } from "next/server";
import { proxyRequest } from "@/lib/server/bff";
import { forwardedQuery, rejectUnknownParameters } from "@/lib/server/list-query";

type RouteContext = {
  params: Promise<{ id: string }>;
};

const LIST_PARAMETERS = ["page", "size"] as const;

export async function GET(request: NextRequest, context: RouteContext) {
  const rejected = rejectUnknownParameters(request, LIST_PARAMETERS);
  if (rejected) return rejected;
  const { id } = await context.params;
  return proxyRequest(
    request,
    "clinical",
    `/clinical-cases/${id}/session-records${forwardedQuery(request, LIST_PARAMETERS)}`,
    { csrf: false }
  );
}

export async function POST(request: NextRequest, context: RouteContext) {
  const { id } = await context.params;
  return proxyRequest(request, "clinical", `/clinical-cases/${id}/session-records`);
}
