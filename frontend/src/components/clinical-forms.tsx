"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import {
  Controller,
  useFieldArray,
  useForm,
  useWatch,
  type UseFormRegisterReturn,
} from "react-hook-form";
import { z } from "zod";
import { Button } from "@/components/ui/button";
import {
  Field,
  FieldDescription,
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
import { Textarea } from "@/components/ui/textarea";
import { formatEnum } from "@/lib/format";
import type {
  AttendanceStatus,
  CarePlan,
  GoalStatus,
  NoteStatus,
  Patient,
  PracticeProfile,
  SessionModality,
  SessionRecord,
} from "@/lib/types";

const clinicalCaseSchema = z.object({
  patientId: z.string().uuid("Select a patient"),
  presentingConcern: z.string().trim().min(3, "Presenting concern is required").max(1000),
});

const careGoalSchema = z.object({
  id: z.string().optional(),
  description: z.string().trim().min(3, "Goal description is required").max(500),
  targetDate: z.string().optional(),
  status: z.enum(["NOT_STARTED", "IN_PROGRESS", "ACHIEVED", "PAUSED", "DISCONTINUED"]),
  progressPercentage: z.number().min(0).max(100),
});

const carePlanSchema = z.object({
  therapeuticFocus: z.string().trim().min(3, "Therapeutic focus is required").max(1000),
  plannedFrequency: z.string().trim().min(2, "Planned frequency is required").max(120),
  reviewDate: z.string().min(1, "Review date is required"),
  goals: z.array(careGoalSchema).min(1, "At least one goal is required"),
});

const sessionRecordSchema = z.object({
  sessionDate: z.string().min(1, "Session date is required"),
  modality: z.enum(["IN_PERSON", "ONLINE"]),
  durationMinutes: z.number().min(0).max(480),
  attendanceStatus: z.enum(["ATTENDED", "NO_SHOW", "CANCELLED_LATE"]),
  noteStatus: z.enum(["PENDING_NOTE", "DRAFT", "SIGNED"]),
  summary: z.string().trim().max(2000).optional(),
  focusAreas: z.string().trim().max(1000).optional(),
  interventions: z.string().trim().max(1000).optional(),
  homework: z.string().trim().max(1000).optional(),
  nextSteps: z.string().trim().max(1000).optional(),
});

const practiceProfileSchema = z.object({
  professionalRegistration: z.string().trim().max(80).optional(),
  timezone: z.string().trim().min(1, "Timezone is required").max(80),
  defaultSessionDurationMinutes: z.number().min(15).max(240),
  primaryLocation: z.string().trim().max(160).optional(),
  telehealthEnabled: z.boolean(),
});

export type ClinicalCaseFormValues = z.infer<typeof clinicalCaseSchema>;
export type CarePlanFormValues = z.infer<typeof carePlanSchema>;
export type SessionRecordFormValues = z.infer<typeof sessionRecordSchema>;
export type PracticeProfileFormValues = z.infer<typeof practiceProfileSchema>;

export function ClinicalCaseForm({
  patients,
  onSubmit,
}: {
  patients: Patient[];
  onSubmit: (values: ClinicalCaseFormValues) => Promise<void>;
}) {
  const form = useForm<ClinicalCaseFormValues>({
    resolver: zodResolver(clinicalCaseSchema),
    defaultValues: {
      patientId: patients[0]?.id ?? "",
      presentingConcern: "",
    },
  });

  const selectedPatientId = useWatch({
    control: form.control,
    name: "patientId",
  });
  const selectedPatientName = patients.find(
    (patient) => patient.id === selectedPatientId
  )?.fullName;

  return (
    <form onSubmit={form.handleSubmit(onSubmit)}>
      <FieldGroup>
        <Controller
          control={form.control}
          name="patientId"
          render={({ field }) => (
            <Field data-invalid={!!form.formState.errors.patientId}>
              <FieldLabel>Patient</FieldLabel>
              <Select value={field.value} onValueChange={field.onChange}>
                <SelectTrigger className="w-full">
                  <SelectValue placeholder="Select patient">
                    {selectedPatientName}
                  </SelectValue>
                </SelectTrigger>
                <SelectContent>
                  <SelectGroup>
                    {patients.map((patient) => (
                      <SelectItem key={patient.id} value={patient.id}>
                        {patient.fullName}
                      </SelectItem>
                    ))}
                  </SelectGroup>
                </SelectContent>
              </Select>
              <FieldError>{form.formState.errors.patientId?.message}</FieldError>
            </Field>
          )}
        />

        <Field data-invalid={!!form.formState.errors.presentingConcern}>
          <FieldLabel htmlFor="presentingConcern">Presenting concern</FieldLabel>
          <Textarea
            id="presentingConcern"
            aria-invalid={!!form.formState.errors.presentingConcern}
            {...form.register("presentingConcern")}
          />
          <FieldError>
            {form.formState.errors.presentingConcern?.message}
          </FieldError>
        </Field>

        <Button type="submit" disabled={form.formState.isSubmitting}>
          {form.formState.isSubmitting ? "Creating" : "Create case"}
        </Button>
      </FieldGroup>
    </form>
  );
}

export function CarePlanForm({
  carePlan,
  onSubmit,
}: {
  carePlan?: CarePlan;
  onSubmit: (values: CarePlanFormValues) => Promise<void>;
}) {
  const form = useForm<CarePlanFormValues>({
    resolver: zodResolver(carePlanSchema),
    defaultValues: {
      therapeuticFocus: carePlan?.therapeuticFocus ?? "",
      plannedFrequency: carePlan?.plannedFrequency ?? "",
      reviewDate: carePlan?.reviewDate ?? "",
      goals: carePlan?.goals.length
        ? carePlan.goals.map((goal) => ({
            id: goal.id,
            description: goal.description,
            targetDate: goal.targetDate ?? "",
            status: goal.status,
            progressPercentage: goal.progressPercentage,
          }))
        : [emptyGoal()],
    },
  });
  const { fields, append, remove } = useFieldArray({
    control: form.control,
    name: "goals",
    keyName: "fieldId",
  });

  async function submit(values: CarePlanFormValues) {
    await onSubmit({
      ...values,
      goals: values.goals.map((goal) => ({
        ...goal,
        targetDate: emptyToUndefined(goal.targetDate),
      })),
    });
  }

  return (
    <form onSubmit={form.handleSubmit(submit)}>
      <FieldGroup>
        <Field data-invalid={!!form.formState.errors.therapeuticFocus}>
          <FieldLabel htmlFor="therapeuticFocus">Therapeutic focus</FieldLabel>
          <Textarea
            id="therapeuticFocus"
            aria-invalid={!!form.formState.errors.therapeuticFocus}
            {...form.register("therapeuticFocus")}
          />
          <FieldError>
            {form.formState.errors.therapeuticFocus?.message}
          </FieldError>
        </Field>

        <div className="grid gap-5 md:grid-cols-2">
          <Field data-invalid={!!form.formState.errors.plannedFrequency}>
            <FieldLabel htmlFor="plannedFrequency">Planned frequency</FieldLabel>
            <Input
              id="plannedFrequency"
              placeholder="Weekly, fortnightly, monthly"
              aria-invalid={!!form.formState.errors.plannedFrequency}
              {...form.register("plannedFrequency")}
            />
            <FieldError>
              {form.formState.errors.plannedFrequency?.message}
            </FieldError>
          </Field>

          <Field data-invalid={!!form.formState.errors.reviewDate}>
            <FieldLabel htmlFor="reviewDate">Review date</FieldLabel>
            <Input
              id="reviewDate"
              type="date"
              aria-invalid={!!form.formState.errors.reviewDate}
              {...form.register("reviewDate")}
            />
            <FieldError>{form.formState.errors.reviewDate?.message}</FieldError>
          </Field>
        </div>

        <div className="flex flex-col gap-4">
          <div className="flex items-center justify-between gap-4">
            <div>
              <p className="text-sm font-medium">Goals</p>
              <p className="text-xs text-muted-foreground">
                Track progress with concise, reviewable goals.
              </p>
            </div>
            <Button type="button" variant="glass" onClick={() => append(emptyGoal())}>
              Add goal
            </Button>
          </div>

          {fields.map((field, index) => (
            <div
              key={field.fieldId}
              className="grid gap-4 rounded-[22px] bg-background/45 p-4 md:grid-cols-[minmax(0,1fr)_9rem_9rem_auto]"
            >
              <Field data-invalid={!!form.formState.errors.goals?.[index]?.description}>
                <FieldLabel htmlFor={`goal-${index}`}>Goal description</FieldLabel>
                <Input
                  id={`goal-${index}`}
                  aria-invalid={!!form.formState.errors.goals?.[index]?.description}
                  {...form.register(`goals.${index}.description`)}
                />
                <FieldError>
                  {form.formState.errors.goals?.[index]?.description?.message}
                </FieldError>
              </Field>

              <Field data-invalid={!!form.formState.errors.goals?.[index]?.targetDate}>
                <FieldLabel htmlFor={`goal-target-${index}`}>Target</FieldLabel>
                <Input
                  id={`goal-target-${index}`}
                  type="date"
                  aria-invalid={!!form.formState.errors.goals?.[index]?.targetDate}
                  {...form.register(`goals.${index}.targetDate`)}
                />
              </Field>

              <Controller
                control={form.control}
                name={`goals.${index}.status`}
                render={({ field }) => (
                  <Field data-invalid={!!form.formState.errors.goals?.[index]?.status}>
                    <FieldLabel>Status</FieldLabel>
                    <Select
                      value={field.value}
                      onValueChange={(value) => field.onChange(value as GoalStatus)}
                    >
                      <SelectTrigger className="w-full">
                        <SelectValue>{formatEnum(field.value)}</SelectValue>
                      </SelectTrigger>
                      <SelectContent>
                        <SelectGroup>
                          {goalStatuses.map((status) => (
                            <SelectItem key={status} value={status}>
                              {formatEnum(status)}
                            </SelectItem>
                          ))}
                        </SelectGroup>
                      </SelectContent>
                    </Select>
                  </Field>
                )}
              />

              <div className="flex items-end gap-2">
                <Field data-invalid={!!form.formState.errors.goals?.[index]?.progressPercentage}>
                  <FieldLabel htmlFor={`goal-progress-${index}`}>Progress</FieldLabel>
                  <Input
                    id={`goal-progress-${index}`}
                    type="number"
                    min={0}
                    max={100}
                    aria-invalid={!!form.formState.errors.goals?.[index]?.progressPercentage}
                    {...form.register(`goals.${index}.progressPercentage`, {
                      valueAsNumber: true,
                    })}
                  />
                  <FieldError>
                    {form.formState.errors.goals?.[index]?.progressPercentage?.message}
                  </FieldError>
                </Field>
                <Button
                  type="button"
                  variant="ghost"
                  disabled={fields.length === 1}
                  onClick={() => remove(index)}
                >
                  Remove
                </Button>
              </div>
            </div>
          ))}
        </div>

        <Button type="submit" disabled={form.formState.isSubmitting}>
          {form.formState.isSubmitting ? "Saving" : "Save care plan"}
        </Button>
      </FieldGroup>
    </form>
  );
}

export function SessionRecordForm({
  defaultDuration,
  includeNoteStatus = false,
  record,
  submitLabel,
  onSubmit,
}: {
  defaultDuration?: number;
  includeNoteStatus?: boolean;
  record?: SessionRecord;
  submitLabel: string;
  onSubmit: (values: SessionRecordFormValues) => Promise<void>;
}) {
  const form = useForm<SessionRecordFormValues>({
    resolver: zodResolver(sessionRecordSchema),
    defaultValues: {
      sessionDate: toDateTimeLocal(record?.sessionDate) ?? "",
      modality: record?.modality ?? "ONLINE",
      durationMinutes: record?.durationMinutes ?? defaultDuration ?? 50,
      attendanceStatus: record?.attendanceStatus ?? "ATTENDED",
      noteStatus: record?.noteStatus ?? "DRAFT",
      summary: record?.summary ?? "",
      focusAreas: record?.focusAreas ?? "",
      interventions: record?.interventions ?? "",
      homework: record?.homework ?? "",
      nextSteps: record?.nextSteps ?? "",
    },
  });

  async function submit(values: SessionRecordFormValues) {
    await onSubmit({
      ...values,
      sessionDate: new Date(values.sessionDate).toISOString(),
      summary: emptyToUndefined(values.summary),
      focusAreas: emptyToUndefined(values.focusAreas),
      interventions: emptyToUndefined(values.interventions),
      homework: emptyToUndefined(values.homework),
      nextSteps: emptyToUndefined(values.nextSteps),
    });
  }

  return (
    <form onSubmit={form.handleSubmit(submit)}>
      <FieldGroup>
        <div className="grid gap-5 md:grid-cols-2">
          <Field data-invalid={!!form.formState.errors.sessionDate}>
            <FieldLabel htmlFor="sessionDate">Session date</FieldLabel>
            <Input
              id="sessionDate"
              type="datetime-local"
              aria-invalid={!!form.formState.errors.sessionDate}
              {...form.register("sessionDate")}
            />
            <FieldError>{form.formState.errors.sessionDate?.message}</FieldError>
          </Field>

          <Field data-invalid={!!form.formState.errors.durationMinutes}>
            <FieldLabel htmlFor="durationMinutes">Duration minutes</FieldLabel>
            <Input
              id="durationMinutes"
              type="number"
              min={0}
              max={480}
              aria-invalid={!!form.formState.errors.durationMinutes}
              {...form.register("durationMinutes", { valueAsNumber: true })}
            />
            <FieldError>
              {form.formState.errors.durationMinutes?.message}
            </FieldError>
          </Field>
        </div>

        <div className="grid gap-5 md:grid-cols-3">
          <Controller
            control={form.control}
            name="modality"
            render={({ field }) => (
              <Field data-invalid={!!form.formState.errors.modality}>
                <FieldLabel>Modality</FieldLabel>
                <Select
                  value={field.value}
                  onValueChange={(value) => field.onChange(value as SessionModality)}
                >
                  <SelectTrigger className="w-full">
                    <SelectValue>{formatEnum(field.value)}</SelectValue>
                  </SelectTrigger>
                  <SelectContent>
                    <SelectGroup>
                      <SelectItem value="ONLINE">Online</SelectItem>
                      <SelectItem value="IN_PERSON">In person</SelectItem>
                    </SelectGroup>
                  </SelectContent>
                </Select>
              </Field>
            )}
          />

          <Controller
            control={form.control}
            name="attendanceStatus"
            render={({ field }) => (
              <Field data-invalid={!!form.formState.errors.attendanceStatus}>
                <FieldLabel>Attendance</FieldLabel>
                <Select
                  value={field.value}
                  onValueChange={(value) =>
                    field.onChange(value as AttendanceStatus)
                  }
                >
                  <SelectTrigger className="w-full">
                    <SelectValue>{formatEnum(field.value)}</SelectValue>
                  </SelectTrigger>
                  <SelectContent>
                    <SelectGroup>
                      {attendanceStatuses.map((status) => (
                        <SelectItem key={status} value={status}>
                          {formatEnum(status)}
                        </SelectItem>
                      ))}
                    </SelectGroup>
                  </SelectContent>
                </Select>
              </Field>
            )}
          />

          {includeNoteStatus ? (
            <Controller
              control={form.control}
              name="noteStatus"
              render={({ field }) => (
                <Field data-invalid={!!form.formState.errors.noteStatus}>
                  <FieldLabel>Note status</FieldLabel>
                  <Select
                    value={field.value}
                    onValueChange={(value) => field.onChange(value as NoteStatus)}
                  >
                    <SelectTrigger className="w-full">
                      <SelectValue>{formatEnum(field.value)}</SelectValue>
                    </SelectTrigger>
                    <SelectContent>
                      <SelectGroup>
                        {noteStatuses.map((status) => (
                          <SelectItem key={status} value={status}>
                            {formatEnum(status)}
                          </SelectItem>
                        ))}
                      </SelectGroup>
                    </SelectContent>
                  </Select>
                </Field>
              )}
            />
          ) : null}
        </div>

        <ClinicalTextarea
          id="summary"
          label="Summary"
          error={form.formState.errors.summary?.message}
          register={form.register("summary")}
        />
        <ClinicalTextarea
          id="focusAreas"
          label="Focus areas"
          error={form.formState.errors.focusAreas?.message}
          register={form.register("focusAreas")}
        />
        <ClinicalTextarea
          id="interventions"
          label="Interventions"
          error={form.formState.errors.interventions?.message}
          register={form.register("interventions")}
        />
        <ClinicalTextarea
          id="homework"
          label="Homework"
          error={form.formState.errors.homework?.message}
          register={form.register("homework")}
        />
        <ClinicalTextarea
          id="nextSteps"
          label="Next steps"
          error={form.formState.errors.nextSteps?.message}
          register={form.register("nextSteps")}
        />

        <Button type="submit" disabled={form.formState.isSubmitting}>
          {form.formState.isSubmitting ? "Saving" : submitLabel}
        </Button>
      </FieldGroup>
    </form>
  );
}

export function PracticeProfileForm({
  profile,
  onSubmit,
}: {
  profile?: PracticeProfile;
  onSubmit: (values: PracticeProfileFormValues) => Promise<void>;
}) {
  const form = useForm<PracticeProfileFormValues>({
    resolver: zodResolver(practiceProfileSchema),
    values: {
      professionalRegistration: profile?.professionalRegistration ?? "",
      timezone: profile?.timezone ?? "UTC",
      defaultSessionDurationMinutes:
        profile?.defaultSessionDurationMinutes ?? 50,
      primaryLocation: profile?.primaryLocation ?? "",
      telehealthEnabled: profile?.telehealthEnabled ?? false,
    },
  });

  async function submit(values: PracticeProfileFormValues) {
    await onSubmit({
      ...values,
      professionalRegistration: emptyToUndefined(values.professionalRegistration),
      primaryLocation: emptyToUndefined(values.primaryLocation),
    });
  }

  return (
    <form onSubmit={form.handleSubmit(submit)}>
      <FieldGroup>
        <Field data-invalid={!!form.formState.errors.professionalRegistration}>
          <FieldLabel htmlFor="professionalRegistration">
            Professional registration
          </FieldLabel>
          <Input
            id="professionalRegistration"
            aria-invalid={!!form.formState.errors.professionalRegistration}
            {...form.register("professionalRegistration")}
          />
          <FieldError>
            {form.formState.errors.professionalRegistration?.message}
          </FieldError>
        </Field>

        <div className="grid gap-5 md:grid-cols-2">
          <Field data-invalid={!!form.formState.errors.timezone}>
            <FieldLabel htmlFor="timezone">Timezone</FieldLabel>
            <Input
              id="timezone"
              placeholder="Europe/Lisbon"
              aria-invalid={!!form.formState.errors.timezone}
              {...form.register("timezone")}
            />
            <FieldDescription>
              Use an IANA timezone such as Europe/Lisbon or UTC.
            </FieldDescription>
            <FieldError>{form.formState.errors.timezone?.message}</FieldError>
          </Field>

          <Field data-invalid={!!form.formState.errors.defaultSessionDurationMinutes}>
            <FieldLabel htmlFor="defaultSessionDurationMinutes">
              Default session duration
            </FieldLabel>
            <Input
              id="defaultSessionDurationMinutes"
              type="number"
              min={15}
              max={240}
              aria-invalid={
                !!form.formState.errors.defaultSessionDurationMinutes
              }
              {...form.register("defaultSessionDurationMinutes", {
                valueAsNumber: true,
              })}
            />
            <FieldError>
              {form.formState.errors.defaultSessionDurationMinutes?.message}
            </FieldError>
          </Field>
        </div>

        <Field data-invalid={!!form.formState.errors.primaryLocation}>
          <FieldLabel htmlFor="primaryLocation">Primary location</FieldLabel>
          <Input
            id="primaryLocation"
            aria-invalid={!!form.formState.errors.primaryLocation}
            {...form.register("primaryLocation")}
          />
          <FieldError>{form.formState.errors.primaryLocation?.message}</FieldError>
        </Field>

        <Field orientation="horizontal">
          <input
            id="telehealthEnabled"
            type="checkbox"
            className="size-4 rounded border-border accent-primary"
            {...form.register("telehealthEnabled")}
          />
          <div className="flex flex-col gap-1">
            <FieldLabel htmlFor="telehealthEnabled">
              Telehealth enabled
            </FieldLabel>
            <FieldDescription>
              Online sessions can be offered from this practice profile.
            </FieldDescription>
          </div>
        </Field>

        <Button type="submit" disabled={form.formState.isSubmitting}>
          {form.formState.isSubmitting ? "Saving" : "Save practice profile"}
        </Button>
      </FieldGroup>
    </form>
  );
}

function ClinicalTextarea({
  id,
  label,
  error,
  register,
}: {
  id: keyof SessionRecordFormValues;
  label: string;
  error?: string;
  register: UseFormRegisterReturn;
}) {
  return (
    <Field data-invalid={!!error}>
      <FieldLabel htmlFor={id}>{label}</FieldLabel>
      <Textarea id={id} aria-invalid={!!error} {...register} />
      <FieldError>{error}</FieldError>
    </Field>
  );
}

function emptyGoal() {
  return {
    description: "",
    targetDate: "",
    status: "NOT_STARTED" as const,
    progressPercentage: 0,
  };
}

function emptyToUndefined(value?: string) {
  const trimmed = value?.trim() ?? "";
  return trimmed.length > 0 ? trimmed : undefined;
}

function toDateTimeLocal(value?: string) {
  if (!value) {
    return undefined;
  }
  const date = new Date(value);
  date.setMinutes(date.getMinutes() - date.getTimezoneOffset());
  return date.toISOString().slice(0, 16);
}

const goalStatuses: GoalStatus[] = [
  "NOT_STARTED",
  "IN_PROGRESS",
  "ACHIEVED",
  "PAUSED",
  "DISCONTINUED",
];

const attendanceStatuses: AttendanceStatus[] = [
  "ATTENDED",
  "NO_SHOW",
  "CANCELLED_LATE",
];

const noteStatuses: NoteStatus[] = ["PENDING_NOTE", "DRAFT", "SIGNED"];
