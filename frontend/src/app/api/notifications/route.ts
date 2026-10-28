import { NextRequest } from "next/server";
import { proxyRequest } from "@/lib/server/bff";
import { forwardedQuery } from "@/lib/server/list-query";
import { projectNotifications } from "@/lib/server/notification-projection";

const LIST_PARAMETERS = ["page", "size", "status"] as const;

export async function GET(request: NextRequest) {
  return proxyRequest(request, "notification", `/notifications${forwardedQuery(request, LIST_PARAMETERS)}`, {
    csrf: false,
    roles: ["ADMIN", "PSYCHOLOGIST"],
    project: projectNotifications,
  });
}
