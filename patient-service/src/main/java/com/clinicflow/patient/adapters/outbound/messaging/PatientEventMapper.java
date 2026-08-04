package com.clinicflow.patient.adapters.outbound.messaging;

import com.clinicflow.patient.application.mapping.MapperConfiguration;
import com.clinicflow.patient.domain.EmailAddress;
import com.clinicflow.patient.domain.PatientId;
import com.clinicflow.patient.domain.PsychologistId;
import com.clinicflow.patient.domain.events.PatientCreatedEvent;
import org.mapstruct.Mapper;

import java.util.UUID;

@Mapper(config = MapperConfiguration.class)
public interface PatientEventMapper {
    PatientCreatedPayload toPayload(PatientCreatedEvent event);

    default UUID map(PatientId id) { return id.value(); }
    default UUID map(PsychologistId id) { return id.value(); }
    default String map(EmailAddress email) { return email.value(); }
}
