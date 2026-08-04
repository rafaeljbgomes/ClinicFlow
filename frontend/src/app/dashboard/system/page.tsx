"use client";

import { useState } from "react";
import useSWR from "swr";
import { ActivityIcon, RefreshCwIcon, SearchIcon, ServerIcon, UserRoundIcon } from "lucide-react";
import { ErrorAlert } from "@/components/error-alert";
import { SectionHeader } from "@/components/section-header";
import { StatusBadge } from "@/components/status-badge";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { apiFetch, swrFetcher } from "@/lib/api-client";
import type { SystemHealth, UserView } from "@/lib/types";

export default function SystemPage() {
  const [userId, setUserId] = useState("");
  const [inspectedUser, setInspectedUser] = useState<UserView | null>(null);
  const [lookupError, setLookupError] = useState<string | null>(null);
  const currentUser = useSWR<UserView>("/api/auth/me", swrFetcher);
  const { data, error, isLoading, isValidating, mutate } = useSWR<SystemHealth>(
    "/api/system/health",
    swrFetcher,
    { refreshInterval: 15000 }
  );

  async function inspectUser() {
    setLookupError(null);
    setInspectedUser(null);
    try {
      setInspectedUser(await apiFetch<UserView>(`/api/users/${encodeURIComponent(userId.trim())}`));
    } catch (lookupFailure) {
      setLookupError(lookupFailure instanceof Error ? lookupFailure.message : "User lookup failed");
    }
  }

  if (currentUser.isLoading) {
    return <Skeleton className="h-48 rounded-[28px]" />;
  }

  if (currentUser.data?.role !== "ADMIN") {
    return (
      <>
        <SectionHeader
          title="Settings"
          description="System telemetry is reserved for admin users."
        />
        <Card>
          <CardHeader>
            <CardTitle>Workspace settings</CardTitle>
            <CardDescription>
              Your workspace is focused on patients, sessions, and messages.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <p className="text-sm text-muted-foreground">
              No platform health information is shown for this role.
            </p>
          </CardContent>
        </Card>
      </>
    );
  }

  return (
    <>
      <SectionHeader
        title="System"
        description="Admin-only platform health checked server-side through the BFF."
        actions={
          <Button variant="outline" onClick={() => mutate()} disabled={isValidating}>
            <RefreshCwIcon data-icon="inline-start" />
            Refresh
          </Button>
        }
      />

      {error ? <ErrorAlert message={error.message} /> : null}

      <Card>
        <CardHeader>
          <CardTitle>Service status</CardTitle>
          <CardDescription>Operational health for the deployed backend services.</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col">
          {isLoading
            ? Array.from({ length: 4 }).map((_, index) => (
                <Skeleton key={index} className="mb-3 h-16 rounded-[20px]" />
              ))
            : (data?.services ?? []).map((service, index, services) => (
                <div key={service.service} className="flex flex-col">
                  <div className="flex items-center justify-between gap-4 py-4">
                    <div className="flex min-w-0 items-center gap-3">
                      <div className="flex size-10 items-center justify-center rounded-full bg-secondary text-secondary-foreground">
                        <ServerIcon />
                      </div>
                      <div className="min-w-0">
                        <p className="font-medium">{service.label}</p>
                        <p className="text-sm text-muted-foreground">{service.service}-service</p>
                      </div>
                    </div>
                    <div className="flex items-center gap-4">
                      <div className="hidden items-center gap-2 text-sm text-muted-foreground sm:flex">
                        <ActivityIcon />
                        <span>{service.latencyMs ?? 0} ms via BFF</span>
                      </div>
                      <StatusBadge status={service.status} />
                    </div>
                  </div>
                  {index < services.length - 1 ? <Separator /> : null}
                </div>
              ))}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>User access lookup</CardTitle>
          <CardDescription>Inspect an account by UUID without exposing the full user directory.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-5">
          <form className="flex flex-col gap-3 sm:flex-row" onSubmit={(event) => { event.preventDefault(); void inspectUser(); }}>
            <Input value={userId} onChange={(event) => setUserId(event.target.value)} placeholder="User UUID" className="font-mono text-xs" />
            <Button type="submit" disabled={!userId.trim()}><SearchIcon data-icon="inline-start" />Inspect</Button>
          </form>
          {lookupError ? <ErrorAlert message={lookupError} /> : null}
          {inspectedUser ? (
            <div className="flex flex-col gap-4 rounded-[20px] bg-muted/55 p-4 sm:flex-row sm:items-center">
              <span className="flex size-11 items-center justify-center rounded-full bg-secondary text-secondary-foreground"><UserRoundIcon /></span>
              <div className="min-w-0 flex-1"><p className="font-medium">{inspectedUser.fullName}</p><p className="truncate text-sm text-muted-foreground">{inspectedUser.email}</p></div>
              <div className="flex items-center gap-2"><StatusBadge status={inspectedUser.role} /><StatusBadge status={inspectedUser.enabled ? "ACTIVE" : "INACTIVE"} /></div>
            </div>
          ) : null}
        </CardContent>
      </Card>
    </>
  );
}
