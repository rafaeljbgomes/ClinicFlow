"use client";

import { useState } from "react";
import useSWR from "swr";
import { LockKeyholeIcon, ShieldCheckIcon, UserRoundIcon } from "lucide-react";
import { toast } from "sonner";
import { ErrorAlert } from "@/components/error-alert";
import { ThemeToggle } from "@/components/theme-toggle";
import {
  PracticeProfileForm,
  type PracticeProfileFormValues,
} from "@/components/clinical-forms";
import { SectionHeader } from "@/components/section-header";
import { StaleVersionAlert } from "@/components/stale-version-alert";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";
import { Skeleton } from "@/components/ui/skeleton";
import { apiFetch, isStaleVersion, swrFetcher } from "@/lib/api-client";
import type { PracticeProfile, UserView } from "@/lib/types";

export default function SettingsPage() {
  const {
    data: user,
    error: userError,
    isLoading: isUserLoading,
  } = useSWR<UserView>("/api/auth/me", swrFetcher);
  // The form follows this data, so it only refreshes on an explicit save or reload: a background
  // revalidation must not swap the profile, and its version, under an edit in progress.
  const practiceProfile = useSWR<PracticeProfile>(
    user?.role === "PSYCHOLOGIST" ? "/api/practice-profile" : null,
    swrFetcher,
    { revalidateOnFocus: false, revalidateOnReconnect: false }
  );
  const [profileConflict, setProfileConflict] = useState(false);

  // The first save creates the profile; later saves replace the version the form shows.
  async function savePracticeProfile(values: PracticeProfileFormValues) {
    try {
      await apiFetch<PracticeProfile>("/api/practice-profile", {
        method: "PUT",
        body: values,
        expectedVersion: practiceProfile.data?.version ?? null,
      });
    } catch (failure) {
      if (!isStaleVersion(failure)) throw failure;
      setProfileConflict(true);
      return;
    }
    toast.success("Practice profile saved");
    setProfileConflict(false);
    await practiceProfile.mutate();
  }

  async function reloadPracticeProfile() {
    await practiceProfile.mutate();
    setProfileConflict(false);
  }

  const error = userError ?? practiceProfile.error;

  return (
    <>
      <SectionHeader
        title="Settings"
        description="Manage your account, practice defaults, and the appearance of your workspace."
      />

      {error ? <ErrorAlert message={error.message} /> : null}

      {isUserLoading ? (
        <Skeleton className="h-72 rounded-[28px]" />
      ) : (
        <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_24rem]">
          <div className="flex flex-col gap-6">
            <Card tone="quiet" className="border border-border/60 bg-card">
              <CardHeader>
                <CardTitle>Account</CardTitle>
                <CardDescription>Your ClinicFlow account details.</CardDescription>
              </CardHeader>
              <CardContent className="flex flex-col">
                <SettingRow icon={<UserRoundIcon />} label="Name" value={user?.fullName ?? "Not available"} />
                <Separator />
                <SettingRow icon={<ShieldCheckIcon />} label="Role" value={user?.role ?? "Not available"} />
                <Separator />
                <SettingRow icon={<LockKeyholeIcon />} label="Status" value={user?.enabled ? "Enabled" : "Disabled"} />
              </CardContent>
            </Card>

            {user?.role === "PSYCHOLOGIST" ? (
              <Card>
                <CardHeader>
                  <CardTitle>Practice profile</CardTitle>
                  <CardDescription>Clinical defaults used when creating session records.</CardDescription>
                </CardHeader>
                <CardContent>
                  {practiceProfile.isLoading ? (
                    <Skeleton className="h-80 rounded-[24px]" />
                  ) : (
                    <>
                      {profileConflict ? <StaleVersionAlert onReload={reloadPracticeProfile} /> : null}
                      <PracticeProfileForm profile={practiceProfile.data} onSubmit={savePracticeProfile} />
                    </>
                  )}
                </CardContent>
              </Card>
            ) : null}
          </div>

          <div className="flex flex-col gap-6">
          <Card size="sm" tone="quiet" className="border border-border/60 bg-card">
            <CardHeader>
              <CardTitle>Appearance</CardTitle>
              <CardDescription>Choose the view that feels best for your day.</CardDescription>
            </CardHeader>
            <CardContent className="flex items-center justify-between gap-4">
              <span className="text-sm text-muted-foreground">Light or dark theme</span>
              <ThemeToggle />
            </CardContent>
          </Card>
          </div>
        </div>
      )}
    </>
  );
}

function SettingRow({
  icon,
  label,
  value,
}: {
  icon: React.ReactNode;
  label: string;
  value: string;
}) {
  return (
    <div className="flex items-center gap-4 py-4">
      <span className="flex size-10 items-center justify-center rounded-full bg-secondary text-secondary-foreground">
        {icon}
      </span>
      <div className="min-w-0">
        <p className="text-sm text-muted-foreground">{label}</p>
        <p className="break-words font-semibold">{value}</p>
      </div>
    </div>
  );
}
