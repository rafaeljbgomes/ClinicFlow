"use client";

import { useState } from "react";
import { LockIcon } from "lucide-react";
import { StatusBadge } from "@/components/status-badge";
import { Button } from "@/components/ui/button";
import { DialogClose, DialogFooter } from "@/components/ui/dialog";
import { formatDateTime, formatEnum } from "@/lib/format";
import type { SessionRecord } from "@/lib/types";

export function isSignable(record: SessionRecord) {
  return record.noteStatus === "DRAFT" && !!record.summary?.trim();
}

export function SignatureLine({
  record,
  signerName,
}: {
  record: SessionRecord;
  signerName?: string;
}) {
  if (record.noteStatus !== "SIGNED") return null;
  return (
    <p className="mt-2 flex items-center gap-1.5 text-xs text-muted-foreground">
      <LockIcon className="size-3.5 shrink-0" aria-hidden />
      <span>
        Signed by {signerName ?? "the treating psychologist"} · {formatDateTime(record.signedAt)}
      </span>
    </p>
  );
}

export function SignSessionNoteConfirmation({
  record,
  onConfirm,
}: {
  record: SessionRecord;
  onConfirm: () => Promise<void>;
}) {
  const [isSigning, setIsSigning] = useState(false);

  async function confirm() {
    setIsSigning(true);
    try {
      await onConfirm();
    } finally {
      setIsSigning(false);
    }
  }

  return (
    <div className="flex flex-col gap-5">
      <div className="rounded-xl border border-border/70 bg-surface-subtle px-4 py-3">
        <p className="type-label text-muted-foreground">{formatDateTime(record.sessionDate)}</p>
        <p className="mt-2 line-clamp-4 text-sm leading-6">{record.summary}</p>
      </div>
      <DialogFooter>
        <DialogClose render={<Button variant="outline" disabled={isSigning} />}>Cancel</DialogClose>
        <Button onClick={confirm} disabled={isSigning}>
          {isSigning ? "Signing" : "Sign note"}
        </Button>
      </DialogFooter>
    </div>
  );
}

export function SessionNoteDetails({
  record,
  signerName,
}: {
  record: SessionRecord;
  signerName?: string;
}) {
  const sections: [string, string | null][] = [
    ["Summary", record.summary],
    ["Focus areas", record.focusAreas],
    ["Interventions", record.interventions],
    ["Homework", record.homework],
    ["Next steps", record.nextSteps],
  ];

  return (
    <div className="flex max-h-[60dvh] flex-col gap-5 overflow-y-auto">
      <div className="flex flex-wrap items-center gap-2">
        <StatusBadge status={record.noteStatus} />
        <StatusBadge status={record.attendanceStatus} />
        <StatusBadge status={record.modality} />
      </div>
      <div className="grid overflow-hidden rounded-xl border border-border/70 bg-surface-subtle divide-y divide-border/70 sm:grid-cols-2 sm:divide-x sm:divide-y-0">
        <NoteMetadata label="Session" value={formatDateTime(record.sessionDate)} />
        <NoteMetadata label="Duration" value={`${record.durationMinutes} minutes · ${formatEnum(record.modality)}`} />
      </div>
      <dl className="flex flex-col gap-4">
        {sections.map(([label, value]) => (
          <div key={label}>
            <dt className="type-label text-muted-foreground">{label}</dt>
            <dd className="mt-1 whitespace-pre-wrap text-sm leading-6">{value ?? "Not recorded"}</dd>
          </div>
        ))}
      </dl>
      <SignatureLine record={record} signerName={signerName} />
    </div>
  );
}

function NoteMetadata({ label, value }: { label: string; value: string }) {
  return (
    <div className="min-w-0 px-3 py-3 sm:px-4">
      <p className="type-label text-muted-foreground">{label}</p>
      <p className="mt-2 text-sm font-semibold">{value}</p>
    </div>
  );
}
