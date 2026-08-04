import { NextRequest } from "next/server";
import { proxyRequest } from "@/lib/server/bff";

export async function GET(request: NextRequest) {
  return proxyRequest(request, "notification", "/notifications", {
    csrf: false,
    roles: ["ADMIN", "PSYCHOLOGIST"],
    project: projectNotifications,
  });
}

function projectNotifications(payload: unknown, user: { role: string } | null) {
  if (user?.role === "ADMIN" || !Array.isArray(payload)) return payload;
  return payload.map(projectPracticeMessage);
}

function projectPracticeMessage(value: unknown) {
  if (!value || typeof value !== "object") return value;
  const item = value as Record<string, unknown>;
  return { id: item.id, eventType: item.eventType, type: item.type, status: item.status, subject: item.subject, createdAt: item.createdAt, sentAt: item.sentAt };
}
