package com.clinicflow.patient.application.ports;

import com.clinicflow.patient.application.queries.ListPatientsQuery;
import com.clinicflow.patient.application.results.PageResult;
import com.clinicflow.patient.domain.Patient;
import com.clinicflow.patient.domain.PatientId;

import java.util.Optional;

public interface PatientRepository {
    // Returns the stored patient, including the version persistence assigned.
    Patient save(Patient patient);
    Optional<Patient> findById(PatientId id);
    // One page of the query's psychologist's patients, ordered by name with the id as tie-breaker.
    PageResult<Patient> findPage(ListPatientsQuery query);
}
