package com.clinicflow.clinical.adapters.outbound.http;

import com.clinicflow.clinical.application.exceptions.OwnershipCheckUnavailableException;
import com.clinicflow.clinical.application.ports.AppointmentDirectory;
import com.clinicflow.clinical.domain.AppointmentId;
import com.clinicflow.clinical.domain.PatientId;
import com.clinicflow.clinical.domain.PsychologistId;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;
import java.util.UUID;

/** Asks appointment-service as the caller, so appointment-service keeps sole authority over ownership. */
@Component
public class HttpAppointmentDirectory implements AppointmentDirectory {
    private static final Logger log = LoggerFactory.getLogger(HttpAppointmentDirectory.class);

    private final RestClient appointmentService;

    public HttpAppointmentDirectory(@Qualifier("appointmentServiceClient") RestClient appointmentService) {
        this.appointmentService = appointmentService;
    }

    @Override
    public Optional<PatientId> findPatientOfOwnedAppointment(AppointmentId appointmentId,
                                                             PsychologistId psychologistId) {
        Lookup lookup;
        try {
            lookup = appointmentService.get()
                    .uri("/appointments/{id}", appointmentId.value())
                    .headers(CallerRequest.headersFor(psychologistId))
                    .exchange((request, response) -> new Lookup(response.getStatusCode().value(),
                            response.getStatusCode().is2xxSuccessful()
                                    ? response.bodyTo(AppointmentReference.class) : null));
        } catch (RestClientException ex) {
            log.warn("appointment_ownership_check_failed appointmentId={} reason={}", appointmentId.value(),
                    ex.getClass().getSimpleName());
            throw new OwnershipCheckUnavailableException("Appointment ownership could not be verified", ex);
        }
        if (lookup.status() == HttpStatus.NOT_FOUND.value() || lookup.status() == HttpStatus.FORBIDDEN.value()) {
            log.info("appointment_ownership_rejected appointmentId={} psychologistId={} status={}",
                    appointmentId.value(), psychologistId.value(), lookup.status());
            return Optional.empty();
        }
        AppointmentReference appointment = lookup.appointment();
        if (appointment == null || appointment.psychologistId() == null || appointment.patientId() == null) {
            log.warn("appointment_ownership_check_failed appointmentId={} status={}", appointmentId.value(),
                    lookup.status());
            throw new OwnershipCheckUnavailableException("Appointment ownership could not be verified");
        }
        // appointment-service already scopes the lookup to the caller; this guards against a misrouted answer.
        if (!psychologistId.value().equals(appointment.psychologistId())) {
            log.warn("appointment_owner_mismatch appointmentId={} psychologistId={}", appointmentId.value(),
                    psychologistId.value());
            return Optional.empty();
        }
        return Optional.of(new PatientId(appointment.patientId()));
    }

    private record Lookup(int status, AppointmentReference appointment) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AppointmentReference(UUID psychologistId, UUID patientId) {
    }
}
