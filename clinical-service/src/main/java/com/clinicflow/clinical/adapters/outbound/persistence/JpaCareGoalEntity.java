package com.clinicflow.clinical.adapters.outbound.persistence;

import com.clinicflow.clinical.domain.GoalStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "care_goals")
class JpaCareGoalEntity {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "care_plan_id", nullable = false)
    private JpaCarePlanEntity carePlan;
    @Column(nullable = false, length = 500) private String description;
    @Column(name = "target_date") private LocalDate targetDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private GoalStatus status;
    @Column(name = "progress_percentage", nullable = false) private int progressPercentage;

    protected JpaCareGoalEntity() {}

    JpaCareGoalEntity(UUID id, String description, LocalDate targetDate,
                      GoalStatus status, int progressPercentage) {
        this.id = id; this.description = description; this.targetDate = targetDate;
        this.status = status; this.progressPercentage = progressPercentage;
    }

    void setCarePlan(JpaCarePlanEntity carePlan) { this.carePlan = carePlan; }
    UUID id() { return id; }
    String description() { return description; }
    LocalDate targetDate() { return targetDate; }
    GoalStatus status() { return status; }
    int progressPercentage() { return progressPercentage; }
}
