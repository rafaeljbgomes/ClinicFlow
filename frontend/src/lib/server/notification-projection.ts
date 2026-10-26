import type { Page } from "@/lib/types";

const EMPTY_PAGE: Page<never> = { content: [], page: { size: 0, number: 0, totalElements: 0, totalPages: 0 } };

// Psychologists see practice-safe fields only. Anything that is not the expected page shape is
// withheld rather than passed through unprojected.
export function projectNotifications(payload: unknown, user: { role: string } | null) {
  if (user?.role === "ADMIN") return payload;
  if (!payload || typeof payload !== "object" || !Array.isArray((payload as { content?: unknown }).content)) {
    return EMPTY_PAGE;
  }
  const page = payload as { content: unknown[]; page?: unknown };
  return { content: page.content.map(projectPracticeMessage), page: page.page };
}

function projectPracticeMessage(value: unknown) {
  if (!value || typeof value !== "object") return value;
  const item = value as Record<string, unknown>;
  return { id: item.id, eventType: item.eventType, type: item.type, status: item.status, subject: item.subject, createdAt: item.createdAt, sentAt: item.sentAt };
}
