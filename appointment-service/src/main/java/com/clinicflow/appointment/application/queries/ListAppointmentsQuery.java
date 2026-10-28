package com.clinicflow.appointment.application.queries;

import com.clinicflow.appointment.domain.AppointmentStatus;
import com.clinicflow.appointment.domain.PsychologistId;

import java.time.Instant;
import java.util.Set;

// from and to bound scheduledAt as [from, to); either may be null. An empty status set means every status,
// and a null order means earliest first.
public record ListAppointmentsQuery(PsychologistId psychologistId, Instant from, Instant to,
                                   Set<AppointmentStatus> statuses, ScheduleOrder order, PageQuery page) {
    public ListAppointmentsQuery {
        if (psychologistId == null || page == null) {
            throw new IllegalArgumentException("psychologistId and page are required");
        }
        if (from != null && to != null && !from.isBefore(to)) {
            throw new IllegalArgumentException("from must be before to");
        }
        statuses = statuses == null ? Set.of() : Set.copyOf(statuses);
        order = order == null ? ScheduleOrder.ASC : order;
    }
}
