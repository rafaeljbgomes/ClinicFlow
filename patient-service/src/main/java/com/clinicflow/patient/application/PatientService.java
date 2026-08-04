package com.clinicflow.patient.application;

import com.clinicflow.patient.application.commands.ChangePatientStatusCommand;
import com.clinicflow.patient.application.commands.CreatePatientCommand;
import com.clinicflow.patient.application.commands.UpdatePatientCommand;
import com.clinicflow.patient.application.exceptions.PatientAccessDeniedException;
import com.clinicflow.patient.application.exceptions.PatientNotFoundException;
import com.clinicflow.patient.application.mapping.PatientApplicationMapper;
import com.clinicflow.patient.application.ports.PatientEventPublisher;
import com.clinicflow.patient.application.ports.PatientRepository;
import com.clinicflow.patient.application.queries.GetPatientQuery;
import com.clinicflow.patient.application.queries.ListPatientsQuery;
import com.clinicflow.patient.application.results.PatientResult;
import com.clinicflow.patient.domain.Patient;
import com.clinicflow.patient.domain.PatientId;
import com.clinicflow.patient.domain.PsychologistId;
import com.clinicflow.patient.domain.ContactPreference;
import com.clinicflow.patient.domain.events.PatientCreatedEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class PatientService {
    private final PatientRepository patientRepository;
    private final PatientEventPublisher eventPublisher;
    private final PatientApplicationMapper mapper;

    public PatientService(PatientRepository patientRepository, PatientEventPublisher eventPublisher,
                          PatientApplicationMapper mapper) {
        this.patientRepository = patientRepository;
        this.eventPublisher = eventPublisher;
        this.mapper = mapper;
    }

    @Transactional
    public PatientResult create(CreatePatientCommand command) {
        Instant now = Instant.now();
        var contactPreference = command.contactPreference() == null
                ? ContactPreference.EMAIL : command.contactPreference();
        Patient patient = Patient.create(new PatientId(UUID.randomUUID()), command.psychologistId(),
                command.email(), command.fullName(), command.preferredName(), command.birthDate(),
                command.phone(), command.emergencyContact(), contactPreference, command.consentStatus(), now);
        patientRepository.save(patient);
        eventPublisher.publish(PatientCreatedEvent.of(patient.id(), patient.psychologistId(), patient.email()));
        return mapper.toResult(patient);
    }

    @Transactional(readOnly = true)
    public List<PatientResult> list(ListPatientsQuery query) {
        return patientRepository.findByPsychologistId(query.psychologistId()).stream()
                .map(mapper::toResult).toList();
    }

    @Transactional(readOnly = true)
    public PatientResult get(GetPatientQuery query) {
        return mapper.toResult(requireOwnedPatient(query.psychologistId(), query.patientId()));
    }

    @Transactional
    public PatientResult update(UpdatePatientCommand command) {
        Patient patient = requireOwnedPatient(command.psychologistId(), command.patientId());
        var contactPreference = command.contactPreference() == null
                ? patient.contactPreference() : command.contactPreference();
        patient.update(command.fullName(), command.preferredName(), command.birthDate(), command.phone(),
                command.emergencyContact(), contactPreference, command.consentStatus(), Instant.now());
        patientRepository.save(patient);
        return mapper.toResult(patient);
    }

    @Transactional
    public PatientResult changeStatus(ChangePatientStatusCommand command) {
        Patient patient = requireOwnedPatient(command.psychologistId(), command.patientId());
        patient.changeStatus(command.status(), Instant.now());
        patientRepository.save(patient);
        return mapper.toResult(patient);
    }

    private Patient requireOwnedPatient(PsychologistId psychologistId, PatientId patientId) {
        Patient patient = patientRepository.findById(patientId).orElseThrow(PatientNotFoundException::new);
        if (!patient.psychologistId().equals(psychologistId)) {
            throw new PatientAccessDeniedException();
        }
        return patient;
    }
}
