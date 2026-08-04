"use client";

import { useState } from "react";
import useSWR from "swr";
import { BellIcon, RefreshCwIcon } from "lucide-react";
import { ErrorAlert } from "@/components/error-alert";
import { LoadingTable } from "@/components/loading-table";
import { SectionHeader } from "@/components/section-header";
import { StatusBadge } from "@/components/status-badge";
import { Button } from "@/components/ui/button";
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from "@/components/ui/sheet";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { swrFetcher } from "@/lib/api-client";
import { formatDateTime, formatEnum } from "@/lib/format";
import type { Notification, UserView } from "@/lib/types";

export default function NotificationsPage() {
  const [selected, setSelected] = useState<Notification | null>(null);
  const currentUser = useSWR<UserView>("/api/auth/me", swrFetcher);
  const { data, error, isLoading, isValidating, mutate } = useSWR<Notification[]>(
    "/api/notifications",
    swrFetcher,
    { refreshInterval: 10000 }
  );
  const isAdmin = currentUser.data?.role === "ADMIN";

  return (
    <>
      <SectionHeader
        title={isAdmin ? "Event history" : "Messages"}
        description={
          isAdmin
            ? "Technical notification delivery history for platform review."
            : "Practice updates shown without clinical detail."
        }
        actions={
          <Button variant="outline" onClick={() => mutate()} disabled={isValidating}>
            <RefreshCwIcon data-icon="inline-start" />
            Refresh
          </Button>
        }
      />

      {error ? <ErrorAlert message={error.message} /> : null}

      {isLoading || currentUser.isLoading ? (
        <LoadingTable />
      ) : !isAdmin ? (
        <Card>
          <CardHeader>
            <CardTitle>Recent messages</CardTitle>
            <CardDescription>Patient and appointment updates in practice-safe language.</CardDescription>
          </CardHeader>
          <CardContent className="flex flex-col gap-4">
            {(data ?? []).map((notification) => (
              <div key={notification.id} className="flex items-start gap-4 rounded-[22px] bg-background/45 p-4">
                <span className="flex size-10 shrink-0 items-center justify-center rounded-full bg-secondary text-secondary-foreground">
                  <BellIcon className="size-4" />
                </span>
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-medium">{notification.subject}</p>
                  <p className="text-xs text-muted-foreground">{formatDateTime(notification.createdAt)}</p>
                </div>
                <StatusBadge status={notification.status} />
              </div>
            ))}
            {(data ?? []).length === 0 ? (
              <p className="text-sm text-muted-foreground">No messages yet.</p>
            ) : null}
          </CardContent>
        </Card>
      ) : (
        <Card>
          <CardHeader>
            <CardTitle>Event delivery history</CardTitle>
            <CardDescription>
              Only frontend-safe event metadata is displayed.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Event</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Recipient</TableHead>
                  <TableHead>Created</TableHead>
                  <TableHead>Sent</TableHead>
                  <TableHead className="w-24">Details</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {(data ?? []).map((notification) => (
                  <TableRow key={notification.id}>
                    <TableCell>
                      <div className="flex min-w-0 items-center gap-2">
                        <BellIcon />
                        <div className="min-w-0">
                          <p className="truncate font-medium">
                            {notification.subject}
                          </p>
                          <p className="text-xs text-muted-foreground">
                            {formatEnum(notification.eventType)}
                          </p>
                        </div>
                      </div>
                    </TableCell>
                    <TableCell>
                      <StatusBadge status={notification.status} />
                    </TableCell>
                    <TableCell>{notification.recipient ?? "Not available"}</TableCell>
                    <TableCell>{formatDateTime(notification.createdAt)}</TableCell>
                    <TableCell>{formatDateTime(notification.sentAt)}</TableCell>
                    <TableCell><Button variant="ghost" size="sm" onClick={() => setSelected(notification)}>Inspect</Button></TableCell>
                  </TableRow>
                ))}
                {(data ?? []).length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={6} className="text-muted-foreground">
                      No notification events have been processed yet.
                    </TableCell>
                  </TableRow>
                ) : null}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      )}

      <Sheet open={!!selected} onOpenChange={(open) => !open && setSelected(null)}>
        <SheetContent className="overflow-y-auto">
          <SheetHeader>
            <SheetTitle>Delivery event</SheetTitle>
            <SheetDescription>Administrative metadata for tracing notification delivery.</SheetDescription>
          </SheetHeader>
          {selected ? (
            <dl className="grid gap-5 px-4 text-sm">
              <EventDetail label="Subject" value={selected.subject} />
              <EventDetail label="Event type" value={formatEnum(selected.eventType)} />
              <EventDetail label="Event ID" value={selected.eventId} mono />
              <EventDetail label="Owner ID" value={selected.psychologistId ?? "Legacy event"} mono />
              <EventDetail label="Recipient" value={selected.recipient ?? "Not available"} />
              <EventDetail label="Created" value={formatDateTime(selected.createdAt)} />
              <EventDetail label="Sent" value={formatDateTime(selected.sentAt)} />
            </dl>
          ) : null}
        </SheetContent>
      </Sheet>
    </>
  );
}

function EventDetail({ label, value, mono = false }: { label: string; value: string; mono?: boolean }) {
  return <div><dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{label}</dt><dd className={mono ? "mt-1 break-all font-mono text-xs" : "mt-1 font-medium"}>{value}</dd></div>;
}
