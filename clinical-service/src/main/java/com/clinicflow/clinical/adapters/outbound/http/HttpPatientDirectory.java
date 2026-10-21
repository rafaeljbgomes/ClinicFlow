package com.clinicflow.clinical.adapters.outbound.http;

import com.clinicflow.clinical.application.exceptions.OwnershipCheckUnavailableException;
import com.clinicflow.clinical.application.ports.PatientDirectory;
import com.clinicflow.clinical.domain.PatientId;
import com.clinicflow.clinical.domain.PsychologistId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Asks patient-service as the caller, so patient-service keeps sole authority over ownership. HEAD returns
 * only the status, which keeps patient details out of this service.
 */
@Component
public class HttpPatientDirectory implements PatientDirectory {
    private static final Logger log = LoggerFactory.getLogger(HttpPatientDirectory.class);

    private final RestClient patientService;

    public HttpPatientDirectory(@Qualifier("patientServiceClient") RestClient patientService) {
        this.patientService = patientService;
    }

    @Override
    public boolean isOwnedBy(PatientId patientId, PsychologistId psychologistId) {
        HttpStatusCode status;
        try {
            status = patientService.head()
                    .uri("/patients/{id}", patientId.value())
                    .headers(CallerRequest.headersFor(psychologistId))
                    .exchange((request, response) -> response.getStatusCode());
        } catch (RestClientException ex) {
            log.warn("patient_ownership_check_failed patientId={} reason={}", patientId.value(),
                    ex.getClass().getSimpleName());
            throw new OwnershipCheckUnavailableException("Patient ownership could not be verified", ex);
        }
        if (status.is2xxSuccessful()) {
            return true;
        }
        if (status.value() == HttpStatus.NOT_FOUND.value() || status.value() == HttpStatus.FORBIDDEN.value()) {
            log.info("patient_ownership_rejected patientId={} psychologistId={} status={}",
                    patientId.value(), psychologistId.value(), status.value());
            return false;
        }
        log.warn("patient_ownership_check_failed patientId={} status={}", patientId.value(), status.value());
        throw new OwnershipCheckUnavailableException("Patient ownership could not be verified");
    }
}
