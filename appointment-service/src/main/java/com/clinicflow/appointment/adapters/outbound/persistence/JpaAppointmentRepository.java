package com.clinicflow.appointment.adapters.outbound.persistence;

import com.clinicflow.appointment.application.ports.AppointmentRepository;
import com.clinicflow.appointment.domain.Appointment;
import com.clinicflow.appointment.domain.AppointmentId;
import com.clinicflow.appointment.domain.PatientId;
import com.clinicflow.appointment.domain.PsychologistId;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class JpaAppointmentRepository implements AppointmentRepository {
    private final SpringDataAppointmentJpaRepository delegate;
    private final AppointmentPersistenceMapper mapper;

    public JpaAppointmentRepository(SpringDataAppointmentJpaRepository delegate, AppointmentPersistenceMapper mapper) {
        this.delegate = delegate;
        this.mapper = mapper;
    }

    @Override public void save(Appointment appointment) { delegate.save(mapper.toJpa(appointment)); }
    @Override public Optional<Appointment> findById(AppointmentId id) {
        return delegate.findById(id.value()).map(mapper::toDomain);
    }
    @Override public List<Appointment> findByPsychologistId(PsychologistId psychologistId) {
        return delegate.findByPsychologistId(psychologistId.value()).stream().map(mapper::toDomain).toList();
    }
    @Override public List<Appointment> findByPatientId(PatientId patientId) {
        return delegate.findByPatientId(patientId.value()).stream().map(mapper::toDomain).toList();
    }
}
