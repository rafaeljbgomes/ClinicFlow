package com.clinicflow.clinical.adapters.outbound.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "care_plans")
class JpaCarePlanEntity {
    @Id private UUID id;
    @Column(name = "clinical_case_id", nullable = false, unique = true) private UUID clinicalCaseId;
    @Column(name = "psychologist_id", nullable = false) private UUID psychologistId;
    @Column(name = "patient_id", nullable = false) private UUID patientId;
    @Column(name = "therapeutic_focus", nullable = false, length = 1000) private String therapeuticFocus;
    @Column(name = "planned_frequency", nullable = false, length = 120) private String plannedFrequency;
    @Column(name = "review_date", nullable = false) private LocalDate reviewDate;
    @OneToMany(mappedBy = "carePlan", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<JpaCareGoalEntity> goals = new ArrayList<>();
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected JpaCarePlanEntity() {}

    JpaCarePlanEntity(UUID id, UUID clinicalCaseId, UUID psychologistId, UUID patientId,
                      String therapeuticFocus, String plannedFrequency, LocalDate reviewDate,
                      List<JpaCareGoalEntity> goals, Instant createdAt, Instant updatedAt) {
        this.id = id; this.clinicalCaseId = clinicalCaseId; this.psychologistId = psychologistId;
        this.patientId = patientId; this.therapeuticFocus = therapeuticFocus;
        this.plannedFrequency = plannedFrequency; this.reviewDate = reviewDate;
        this.createdAt = createdAt; this.updatedAt = updatedAt;
        goals.forEach(this::addGoal);
    }

    void addGoal(JpaCareGoalEntity goal) {
        goal.setCarePlan(this);
        goals.add(goal);
    }

    UUID id() { return id; }
    UUID clinicalCaseId() { return clinicalCaseId; }
    UUID psychologistId() { return psychologistId; }
    UUID patientId() { return patientId; }
    String therapeuticFocus() { return therapeuticFocus; }
    String plannedFrequency() { return plannedFrequency; }
    LocalDate reviewDate() { return reviewDate; }
    List<JpaCareGoalEntity> goals() { return List.copyOf(goals); }
    Instant createdAt() { return createdAt; }
    Instant updatedAt() { return updatedAt; }
}
