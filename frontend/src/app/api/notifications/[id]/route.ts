import { NextRequest } from "next/server";
import { proxyRequest } from "@/lib/server/bff";

type RouteContext = {
  params: Promise<{ id: string }>;
};

export async function GET(request: NextRequest, context: RouteContext) {
  const { id } = await context.params;
  return proxyRequest(request, "notification", `/notifications/${id}`, {
    csrf: false,
    roles: ["ADMIN", "PSYCHOLOGIST"],
    project: (payload, user) => {
      if (user?.role === "ADMIN" || !payload || typeof payload !== "object") return payload;
      const item = payload as Record<string, unknown>;
      return { id: item.id, eventType: item.eventType, type: item.type, status: item.status, subject: item.subject, createdAt: item.createdAt, sentAt: item.sentAt };
    },
  });
}
