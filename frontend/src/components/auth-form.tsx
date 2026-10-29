import Link from "next/link";
import { HeartPulseIcon, LockKeyholeIcon, UserPlusIcon } from "lucide-react";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";

type AuthMode = "login" | "register";

const signInErrors: Record<string, string> = {
  cancelled: "Sign-in was cancelled.",
  expired: "The sign-in took too long. Please try again.",
  failed: "We could not sign you in. Please try again.",
  unavailable: "Sign-in is temporarily unavailable. Please try again shortly.",
};

// Credentials are entered on Keycloak's pages only; ClinicFlow never receives a password (ADR-027).
export function AuthForm({ mode, error }: { mode: AuthMode; error?: string }) {
  const isRegister = mode === "register";
  const errorMessage = error ? (signInErrors[error] ?? signInErrors.failed) : null;

  return (
    <main className="flex min-h-screen items-center justify-center bg-surface-container-low px-5 py-6 sm:px-8 sm:py-10">
      <div className="grid w-full max-w-6xl gap-6 lg:grid-cols-[minmax(0,1.15fr)_27rem] lg:items-stretch">
        <section className="hidden min-h-[34rem] flex-col justify-between rounded-[28px] bg-primary p-10 text-primary-foreground shadow-[0_20px_60px_rgb(23_75_58/20%)] lg:flex">
          <div className="flex items-center gap-3">
            <span className="flex size-12 items-center justify-center rounded-full bg-white/12 text-white ring-1 ring-white/20">
              <HeartPulseIcon className="size-5" />
            </span>
            <div>
              <p className="text-lg font-semibold tracking-tight">ClinicFlow</p>
              <p className="text-sm text-white/70">Practice workspace</p>
            </div>
          </div>
          <div className="flex max-w-xl flex-col gap-5">
            <p className="text-sm font-semibold tracking-[0.14em] text-white/65 uppercase">A clearer clinical day</p>
            <h1 className="font-display text-6xl leading-[0.92] tracking-[-0.045em]">
              Care, brought into focus.
            </h1>
            <p className="max-w-md text-base leading-7 text-white/75">
              A calm, considered workspace for patients, appointments, care plans, and the work around them.
            </p>
          </div>
          <p className="max-w-sm text-sm leading-6 text-white/65">Built for the everyday rhythm of an independent mental health practice.</p>
        </section>

        <Card className="w-full self-center py-8 shadow-[0_20px_48px_rgb(23_27_25/0.08)]">
        <CardHeader>
          <div className="mb-2 flex items-center gap-2 text-xs font-bold tracking-widest text-muted-foreground uppercase">
            <HeartPulseIcon className="size-4 text-primary" />
            ClinicFlow
          </div>
          <CardTitle>{isRegister ? "Create a patient account" : "Sign in"}</CardTitle>
          <CardDescription>
            {isRegister
              ? "Patient accounts are created on the ClinicFlow sign-in page. Psychologist workspaces are set up by an administrator."
              : "Sign in to manage your practice with clarity."}
          </CardDescription>
        </CardHeader>
        <CardContent>
          {errorMessage ? (
            <Alert variant="destructive">
              <AlertDescription>{errorMessage}</AlertDescription>
            </Alert>
          ) : (
            <p className="text-sm leading-6 text-muted-foreground">
              You will continue to the ClinicFlow sign-in page and come back here when you are done.
            </p>
          )}
        </CardContent>
        <CardFooter className="flex flex-col gap-3">
          <Button className="w-full" render={<a href={isRegister ? "/api/auth/register" : "/api/auth/login"} />} nativeButton={false}>
            {isRegister ? <UserPlusIcon data-icon="inline-start" /> : <LockKeyholeIcon data-icon="inline-start" />}
            {isRegister ? "Create account" : "Sign in"}
          </Button>
          <p className="text-sm text-muted-foreground">
            {isRegister ? "Already have access?" : "Need a patient account?"}{" "}
            <Link
              href={isRegister ? "/login" : "/register"}
              className="font-medium text-foreground underline-offset-4 hover:underline"
            >
              {isRegister ? "Sign in" : "Register"}
            </Link>
          </p>
        </CardFooter>
      </Card>
      </div>
    </main>
  );
}
