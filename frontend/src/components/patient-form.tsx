"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Controller, useForm } from "react-hook-form";
import { z } from "zod";
import { Button } from "@/components/ui/button";
import {
  Field,
  FieldError,
  FieldGroup,
  FieldLabel,
} from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import type { ConsentStatus, ContactPreference, Patient } from "@/lib/types";

const schema = z.object({
  email: z.email(),
  fullName: z.string().min(2, "Full name is required").max(160),
  preferredName: z.string().trim().max(120).optional(),
  birthDate: z.string().optional(),
  phone: z
    .string()
    .trim()
    .refine(
      (value) => value === "" || /^\+[1-9]\d{7,14}$/.test(value),
      "Use international E.164 format, for example +351912345678"
    )
    .optional(),
  emergencyContactName: z.string().trim().max(160).optional(),
  emergencyContactPhone: z
    .string()
    .trim()
    .refine(
      (value) => value === "" || /^\+[1-9]\d{7,14}$/.test(value),
      "Use international E.164 format, for example +351912345678"
    )
    .optional(),
  emergencyContactRelationship: z.string().trim().max(80).optional(),
  contactPreference: z.enum(["EMAIL", "PHONE", "SMS"]),
  consentStatus: z.enum(["GRANTED", "PENDING", "REVOKED"]),
});

export type PatientFormValues = z.infer<typeof schema>;

export function toPatientPayload(values: PatientFormValues, includeEmail = true) {
  const payload = {
    fullName: values.fullName,
    preferredName: emptyToNull(values.preferredName),
    birthDate: emptyToNull(values.birthDate),
    phone: emptyToNull(values.phone),
    emergencyContactName: emptyToNull(values.emergencyContactName),
    emergencyContactPhone: emptyToNull(values.emergencyContactPhone),
    emergencyContactRelationship: emptyToNull(values.emergencyContactRelationship),
    contactPreference: values.contactPreference,
    consentStatus: values.consentStatus,
  };

  return includeEmail ? { email: values.email, ...payload } : payload;
}

export function PatientForm({
  patient,
  submitLabel,
  onSubmit,
}: {
  patient?: Patient;
  submitLabel: string;
  onSubmit: (values: PatientFormValues) => Promise<void>;
}) {
  const form = useForm<PatientFormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      email: patient?.email ?? "",
      fullName: patient?.fullName ?? "",
      preferredName: patient?.preferredName ?? "",
      birthDate: patient?.birthDate ?? "",
      phone: patient?.phone ?? "",
      emergencyContactName: patient?.emergencyContactName ?? "",
      emergencyContactPhone: patient?.emergencyContactPhone ?? "",
      emergencyContactRelationship: patient?.emergencyContactRelationship ?? "",
      contactPreference: patient?.contactPreference ?? "EMAIL",
      consentStatus: patient?.consentStatus ?? "GRANTED",
    },
  });

  return (
    <form className="@container/patient-form" onSubmit={form.handleSubmit(onSubmit)}>
      <FieldGroup>
        <Field data-invalid={!!form.formState.errors.fullName}>
          <FieldLabel htmlFor="fullName">Full name</FieldLabel>
          <Input
            id="fullName"
            aria-invalid={!!form.formState.errors.fullName}
            {...form.register("fullName")}
          />
          <FieldError>{form.formState.errors.fullName?.message}</FieldError>
        </Field>

        <Field data-invalid={!!form.formState.errors.preferredName}>
          <FieldLabel htmlFor="preferredName">Preferred name</FieldLabel>
          <Input
            id="preferredName"
            aria-invalid={!!form.formState.errors.preferredName}
            {...form.register("preferredName")}
          />
          <FieldError>{form.formState.errors.preferredName?.message}</FieldError>
        </Field>

        <Field data-invalid={!!form.formState.errors.email}>
          <FieldLabel htmlFor="email">Email</FieldLabel>
          <Input
            id="email"
            type="email"
            disabled={!!patient}
            aria-invalid={!!form.formState.errors.email}
            {...form.register("email")}
          />
          <FieldError>{form.formState.errors.email?.message}</FieldError>
        </Field>

        <Field data-invalid={!!form.formState.errors.birthDate}>
          <FieldLabel htmlFor="birthDate">Birth date</FieldLabel>
          <Input
            id="birthDate"
            type="date"
            aria-invalid={!!form.formState.errors.birthDate}
            {...form.register("birthDate")}
          />
          <FieldError>{form.formState.errors.birthDate?.message}</FieldError>
        </Field>

        <Field data-invalid={!!form.formState.errors.phone}>
          <FieldLabel htmlFor="phone">Phone</FieldLabel>
          <Input
            id="phone"
            type="tel"
            inputMode="tel"
            autoComplete="tel"
            placeholder="+351912345678"
            aria-invalid={!!form.formState.errors.phone}
            {...form.register("phone")}
          />
          <FieldError>{form.formState.errors.phone?.message}</FieldError>
        </Field>

        <div className="grid gap-5 @lg/patient-form:grid-cols-3">
          <Field data-invalid={!!form.formState.errors.emergencyContactName}>
            <FieldLabel htmlFor="emergencyContactName">Emergency contact</FieldLabel>
            <Input
              id="emergencyContactName"
              aria-invalid={!!form.formState.errors.emergencyContactName}
              {...form.register("emergencyContactName")}
            />
            <FieldError>
              {form.formState.errors.emergencyContactName?.message}
            </FieldError>
          </Field>

          <Field data-invalid={!!form.formState.errors.emergencyContactPhone}>
            <FieldLabel htmlFor="emergencyContactPhone">Emergency phone</FieldLabel>
            <Input
              id="emergencyContactPhone"
              type="tel"
              inputMode="tel"
              placeholder="+351912345678"
              aria-invalid={!!form.formState.errors.emergencyContactPhone}
              {...form.register("emergencyContactPhone")}
            />
            <FieldError>
              {form.formState.errors.emergencyContactPhone?.message}
            </FieldError>
          </Field>

          <Field data-invalid={!!form.formState.errors.emergencyContactRelationship}>
            <FieldLabel htmlFor="emergencyContactRelationship">Relationship</FieldLabel>
            <Input
              id="emergencyContactRelationship"
              aria-invalid={!!form.formState.errors.emergencyContactRelationship}
              {...form.register("emergencyContactRelationship")}
            />
            <FieldError>
              {form.formState.errors.emergencyContactRelationship?.message}
            </FieldError>
          </Field>
        </div>

        <Controller
          control={form.control}
          name="contactPreference"
          render={({ field }) => (
            <Field data-invalid={!!form.formState.errors.contactPreference}>
              <FieldLabel>Contact preference</FieldLabel>
              <Select
                value={field.value}
                onValueChange={(value) =>
                  field.onChange(value as ContactPreference)
                }
              >
                <SelectTrigger className="w-full">
                  <SelectValue placeholder="Select preference" />
                </SelectTrigger>
                <SelectContent>
                  <SelectGroup>
                    <SelectItem value="EMAIL">Email</SelectItem>
                    <SelectItem value="PHONE">Phone</SelectItem>
                    <SelectItem value="SMS">SMS</SelectItem>
                  </SelectGroup>
                </SelectContent>
              </Select>
              <FieldError>
                {form.formState.errors.contactPreference?.message}
              </FieldError>
            </Field>
          )}
        />

        <Controller
          control={form.control}
          name="consentStatus"
          render={({ field }) => (
            <Field data-invalid={!!form.formState.errors.consentStatus}>
              <FieldLabel>Consent status</FieldLabel>
              <Select
                value={field.value}
                onValueChange={(value) => field.onChange(value as ConsentStatus)}
              >
                <SelectTrigger className="w-full">
                  <SelectValue placeholder="Select consent" />
                </SelectTrigger>
                <SelectContent>
                  <SelectGroup>
                    <SelectItem value="GRANTED">Granted</SelectItem>
                    <SelectItem value="PENDING">Pending</SelectItem>
                    <SelectItem value="REVOKED">Revoked</SelectItem>
                  </SelectGroup>
                </SelectContent>
              </Select>
              <FieldError>
                {form.formState.errors.consentStatus?.message}
              </FieldError>
            </Field>
          )}
        />

        <Button type="submit" disabled={form.formState.isSubmitting}>
          {form.formState.isSubmitting ? "Saving" : submitLabel}
        </Button>
      </FieldGroup>
    </form>
  );
}

function emptyToNull(value?: string) {
  const trimmed = value?.trim() ?? "";
  return trimmed.length > 0 ? trimmed : null;
}
