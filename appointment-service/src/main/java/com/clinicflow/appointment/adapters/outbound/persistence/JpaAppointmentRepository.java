package com.clinicflow.appointment.adapters.outbound.persistence;

import com.clinicflow.appointment.application.exceptions.StaleResourceVersionException;
import com.clinicflow.appointment.application.ports.AppointmentRepository;
import com.clinicflow.appointment.application.queries.ListAppointmentsQuery;
import com.clinicflow.appointment.application.queries.ScheduleOrder;
import com.clinicflow.appointment.application.results.PageResult;
import com.clinicflow.appointment.domain.Appointment;
import com.clinicflow.appointment.domain.AppointmentId;
import com.clinicflow.appointment.domain.AppointmentStatus;
import com.clinicflow.appointment.domain.PatientId;
import jakarta.persistence.criteria.Predicate;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public class JpaAppointmentRepository implements AppointmentRepository {
    private final SpringDataAppointmentJpaRepository delegate;
    private final AppointmentPersistenceMapper mapper;

    public JpaAppointmentRepository(SpringDataAppointmentJpaRepository delegate, AppointmentPersistenceMapper mapper) {
        this.delegate = delegate;
        this.mapper = mapper;
    }

    @Override public Appointment save(Appointment appointment) {
        try {
            // Flush here so a concurrent write surfaces as a version conflict, not a commit failure.
            return mapper.toDomain(delegate.saveAndFlush(mapper.toJpa(appointment)));
        } catch (OptimisticLockingFailureException ex) {
            throw new StaleResourceVersionException();
        }
    }
    @Override public Optional<Appointment> findById(AppointmentId id) {
        return delegate.findById(id.value()).map(mapper::toDomain);
    }
    @Override public PageResult<Appointment> findPage(ListAppointmentsQuery query) {
        Page<JpaAppointmentEntity> page = delegate.findAll(matching(query),
                PageRequest.of(query.page().number(), query.page().size(), scheduleOrder(query.order())));
        return new PageResult<>(page.getContent().stream().map(mapper::toDomain).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements());
    }

    // The id breaks scheduledAt ties so rows never repeat or vanish between pages.
    private static Sort scheduleOrder(ScheduleOrder order) {
        Sort.Direction direction = order == ScheduleOrder.DESC ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(direction, "scheduledAt", "id");
    }

    private static Specification<JpaAppointmentEntity> matching(ListAppointmentsQuery query) {
        UUID psychologistId = query.psychologistId().value();
        Instant from = query.from();
        Instant to = query.to();
        Set<AppointmentStatus> statuses = query.statuses();
        return (root, criteria, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("psychologistId"), psychologistId));
            if (from != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("scheduledAt"), from));
            }
            if (to != null) {
                predicates.add(builder.lessThan(root.get("scheduledAt"), to));
            }
            if (!statuses.isEmpty()) {
                predicates.add(root.get("status").in(statuses));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }
    @Override public List<Appointment> findByPatientId(PatientId patientId) {
        return delegate.findByPatientId(patientId.value()).stream().map(mapper::toDomain).toList();
    }
}
