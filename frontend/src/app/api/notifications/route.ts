import { NextRequest } from "next/server";
import { proxyRequest } from "@/lib/server/bff";
import { forwardedQuery, rejectUnknownParameters } from "@/lib/server/list-query";
import { projectNotifications } from "@/lib/server/notification-projection";

const LIST_PARAMETERS = ["page", "size", "status"] as const;

export async function GET(request: NextRequest) {
  const rejected = rejectUnknownParameters(request, LIST_PARAMETERS);
  if (rejected) return rejected;
  return proxyRequest(request, "notification", `/notifications${forwardedQuery(request, LIST_PARAMETERS)}`, {
    csrf: false,
    roles: ["ADMIN", "PSYCHOLOGIST"],
    project: projectNotifications,
  });
}
