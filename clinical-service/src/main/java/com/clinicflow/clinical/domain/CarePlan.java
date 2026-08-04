package com.clinicflow.clinical.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public final class CarePlan {
    private final CarePlanId id;
    private final ClinicalCaseId clinicalCaseId;
    private final PsychologistId psychologistId;
    private final PatientId patientId;
    private String therapeuticFocus;
    private String plannedFrequency;
    private LocalDate reviewDate;
    private List<CareGoal> goals;
    private final Instant createdAt;
    private Instant updatedAt;

    private CarePlan(CarePlanId id, ClinicalCaseId clinicalCaseId, PsychologistId psychologistId, PatientId patientId,
                     String therapeuticFocus, String plannedFrequency, LocalDate reviewDate, List<CareGoal> goals,
                     Instant createdAt, Instant updatedAt) {
        if (id == null || clinicalCaseId == null || psychologistId == null || patientId == null
                || reviewDate == null || goals == null || createdAt == null || updatedAt == null) {
            throw new IllegalArgumentException("Care plan required fields are missing");
        }
        if (goals.isEmpty()) {
            throw new IllegalArgumentException("Care plan requires at least one goal");
        }
        this.id = id;
        this.clinicalCaseId = clinicalCaseId;
        this.psychologistId = psychologistId;
        this.patientId = patientId;
        this.therapeuticFocus = TextFields.required(therapeuticFocus, 1000, "Therapeutic focus is invalid");
        this.plannedFrequency = TextFields.required(plannedFrequency, 120, "Planned frequency is invalid");
        this.reviewDate = reviewDate;
        this.goals = List.copyOf(goals);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static CarePlan create(CarePlanId id, ClinicalCase clinicalCase, String therapeuticFocus,
                                  String plannedFrequency, LocalDate reviewDate, List<CareGoal> goals, Instant now) {
        return new CarePlan(id, clinicalCase.id(), clinicalCase.psychologistId(), clinicalCase.patientId(),
                therapeuticFocus, plannedFrequency, reviewDate, goals, now, now);
    }

    public static CarePlan rehydrate(CarePlanId id, ClinicalCaseId clinicalCaseId, PsychologistId psychologistId,
                                     PatientId patientId, String therapeuticFocus, String plannedFrequency,
                                     LocalDate reviewDate, List<CareGoal> goals, Instant createdAt,
                                     Instant updatedAt) {
        return new CarePlan(id, clinicalCaseId, psychologistId, patientId, therapeuticFocus, plannedFrequency,
                reviewDate, goals, createdAt, updatedAt);
    }

    public CarePlan replace(String therapeuticFocus, String plannedFrequency, LocalDate reviewDate,
                            List<CareGoal> goals, Instant now) {
        if (now == null || now.isBefore(createdAt)) {
            throw new IllegalArgumentException("Care plan update time is invalid");
        }
        if (goals == null || goals.isEmpty() || reviewDate == null) {
            throw new IllegalArgumentException("Care plan update fields are missing");
        }
        this.therapeuticFocus = TextFields.required(therapeuticFocus, 1000, "Therapeutic focus is invalid");
        this.plannedFrequency = TextFields.required(plannedFrequency, 120, "Planned frequency is invalid");
        this.reviewDate = reviewDate;
        this.goals = List.copyOf(goals);
        this.updatedAt = now;
        return this;
    }

    public CarePlanId id() { return new CarePlanId(id.value()); }
    public ClinicalCaseId clinicalCaseId() { return new ClinicalCaseId(clinicalCaseId.value()); }
    public PsychologistId psychologistId() { return new PsychologistId(psychologistId.value()); }
    public PatientId patientId() { return new PatientId(patientId.value()); }
    public String therapeuticFocus() { return therapeuticFocus; }
    public String plannedFrequency() { return plannedFrequency; }
    public LocalDate reviewDate() { return reviewDate; }
    public List<CareGoal> goals() { return List.copyOf(goals); }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof CarePlan carePlan && id.equals(carePlan.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
