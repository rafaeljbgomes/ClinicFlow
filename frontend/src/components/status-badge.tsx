import { Badge } from "@/components/ui/badge";
import { formatEnum } from "@/lib/format";
import { cn } from "@/lib/utils";

export function StatusBadge({ status }: { status: string }) {
  const norm = status.toUpperCase();
  const tone = getTone(norm);

  return (
    <Badge
      variant="outline"
      className={cn(
        "h-6 gap-1.5 rounded-full border-transparent px-2.5 text-[0.68rem] font-medium tracking-[0.01em]",
        tone === "success" && "bg-[var(--status-success-bg)] text-[var(--status-success-fg)]",
        tone === "warning" && "bg-[var(--status-warning-bg)] text-[var(--status-warning-fg)]",
        tone === "danger" && "bg-[var(--status-danger-bg)] text-[var(--status-danger-fg)]",
        tone === "info" && "bg-[var(--status-info-bg)] text-[var(--status-info-fg)]",
        tone === "neutral" && "bg-muted text-muted-foreground"
      )}
    >
      <span className="size-1 rounded-full bg-current" />
      {formatEnum(status)}
    </Badge>
  );
}

function getTone(status: string) {
  if (
    [
      "UP",
      "ACTIVE",
      "SENT",
      "SCHEDULED",
      "COMPLETED",
      "GRANTED",
      "ACHIEVED",
      "SIGNED",
      "ATTENDED",
    ].includes(status)
  ) {
    return "success";
  }
  if (
    [
      "DOWN",
      "FAILED",
      "CANCELLED",
      "CANCELED",
      "REVOKED",
      "DISCHARGED",
      "DISCONTINUED",
      "NO_SHOW",
      "CANCELLED_LATE",
    ].includes(status)
  ) {
    return "danger";
  }
  if (
    [
      "RESCHEDULED",
      "PENDING",
      "PENDING_NOTE",
      "DRAFT",
      "WARNING",
      "UNKNOWN",
      "INTAKE",
      "PAUSED",
    ].includes(status)
  ) {
    return "warning";
  }
  if (["ONLINE", "IN_PERSON", "IN_PROGRESS", "NOT_STARTED"].includes(status)) {
    return "info";
  }

  return "neutral";
}
