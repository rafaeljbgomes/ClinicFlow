"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { zodResolver } from "@hookform/resolvers/zod";
import { HeartPulseIcon, LockKeyholeIcon } from "lucide-react";
import { useForm } from "react-hook-form";
import { toast } from "sonner";
import { z } from "zod";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import {
  Field,
  FieldDescription,
  FieldError,
  FieldGroup,
  FieldLabel,
} from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { apiFetch, ApiClientError } from "@/lib/api-client";
import type { UserView } from "@/lib/types";

const loginSchema = z.object({
  email: z.email(),
  password: z.string().min(1, "Password is required"),
});

const registerSchema = loginSchema.extend({
  fullName: z.string().min(2, "Full name is required").max(160),
  password: z.string().min(12, "Use at least 12 characters"),
});

type AuthMode = "login" | "register";
type AuthFormValues = {
  email: string;
  password: string;
  fullName?: string;
};

export function AuthForm({ mode }: { mode: AuthMode }) {
  const router = useRouter();
  const isRegister = mode === "register";
  const schema = isRegister ? registerSchema : loginSchema;
  const form = useForm<AuthFormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      email: "",
      password: "",
      fullName: "",
    },
  });

  async function onSubmit(values: AuthFormValues) {
    try {
      if (isRegister) {
        await apiFetch("/api/auth/register", {
          method: "POST",
          body: {
            email: values.email,
            password: values.password,
            role: "PSYCHOLOGIST",
            fullName: values.fullName ?? "",
          },
        });
      }

      const session = await apiFetch<{ user: UserView }>("/api/auth/login", {
        method: "POST",
        body: {
          email: values.email,
          password: values.password,
        },
      });

      toast.success(isRegister ? "Account created" : "Signed in");
      const destination = session.user.role === "ADMIN"
        ? "/dashboard/system"
        : session.user.role === "PATIENT"
          ? "/dashboard/access-limited"
          : "/dashboard";
      router.replace(destination);
      router.refresh();
    } catch (error) {
      const message =
        error instanceof ApiClientError
          ? error.message
          : "Authentication failed";
      toast.error(message);
    }
  }

  return (
    <main className="flex min-h-screen items-center justify-center bg-surface-container-low px-4 py-10">
      <div className="grid w-full max-w-5xl gap-8 lg:grid-cols-[minmax(0,1fr)_27rem] lg:items-center">
        <section className="hidden max-w-xl flex-col gap-6 lg:flex">
          <div className="flex items-center gap-3">
            <span className="flex size-12 items-center justify-center rounded-full bg-primary text-primary-foreground">
              <HeartPulseIcon className="size-5" />
            </span>
            <div>
              <p className="text-lg font-semibold tracking-tight">ClinicFlow</p>
              <p className="text-sm text-muted-foreground">Practice workspace</p>
            </div>
          </div>
          <div className="flex flex-col gap-4">
            <h1 className="text-5xl font-semibold tracking-tight">
              Clinical operations without the noise.
            </h1>
            <p className="max-w-lg text-base leading-7 text-muted-foreground">
              A quiet workspace for managing patients, sessions, and practice messages with a secure BFF boundary.
            </p>
          </div>
        </section>

        <Card className="w-full">
        <CardHeader>
          <div className="mb-2 flex items-center gap-2 text-xs font-bold tracking-widest text-muted-foreground uppercase">
            <HeartPulseIcon className="size-4 text-primary" />
            ClinicFlow
          </div>
          <CardTitle>{isRegister ? "Create workspace access" : "Sign in"}</CardTitle>
          <CardDescription>
            {isRegister
              ? "Create a psychologist account for the prototype dashboard."
              : "Access the clinical operations dashboard."}
          </CardDescription>
        </CardHeader>
        <form onSubmit={form.handleSubmit(onSubmit)}>
          <CardContent>
            <FieldGroup>
              {isRegister && (
                <Field data-invalid={!!form.formState.errors.fullName}>
                  <FieldLabel htmlFor="fullName">Full name</FieldLabel>
                  <Input
                    id="fullName"
                    aria-invalid={!!form.formState.errors.fullName}
                    autoComplete="name"
                    {...form.register("fullName")}
                  />
                  <FieldError>
                    {form.formState.errors.fullName?.message as string}
                  </FieldError>
                </Field>
              )}

              <Field data-invalid={!!form.formState.errors.email}>
                <FieldLabel htmlFor="email">Email</FieldLabel>
                <Input
                  id="email"
                  type="email"
                  aria-invalid={!!form.formState.errors.email}
                  autoComplete="email"
                  {...form.register("email")}
                />
                <FieldError>{form.formState.errors.email?.message}</FieldError>
              </Field>

              <Field data-invalid={!!form.formState.errors.password}>
                <FieldLabel htmlFor="password">Password</FieldLabel>
                <Input
                  id="password"
                  type="password"
                  aria-invalid={!!form.formState.errors.password}
                  autoComplete={isRegister ? "new-password" : "current-password"}
                  {...form.register("password")}
                />
                {isRegister ? (
                  <FieldDescription>
                    Minimum 12 characters. Passwords are never stored in the browser.
                  </FieldDescription>
                ) : null}
                <FieldError>{form.formState.errors.password?.message}</FieldError>
              </Field>
            </FieldGroup>
          </CardContent>
          <CardFooter className="flex flex-col gap-3">
            <Button
              type="submit"
              className="w-full"
              disabled={form.formState.isSubmitting}
            >
              <LockKeyholeIcon data-icon="inline-start" />
              {form.formState.isSubmitting
                ? "Please wait"
                : isRegister
                  ? "Create account"
                  : "Sign in"}
            </Button>
            <p className="text-sm text-muted-foreground">
              {isRegister ? "Already have access?" : "Need an account?"}{" "}
              <Link
                href={isRegister ? "/login" : "/register"}
                className="font-medium text-foreground underline-offset-4 hover:underline"
              >
                {isRegister ? "Sign in" : "Register"}
              </Link>
            </p>
          </CardFooter>
        </form>
      </Card>
      </div>
    </main>
  );
}
