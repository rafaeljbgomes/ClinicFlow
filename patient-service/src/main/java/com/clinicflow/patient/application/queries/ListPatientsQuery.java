package com.clinicflow.patient.application.queries;

import com.clinicflow.patient.domain.ConsentStatus;
import com.clinicflow.patient.domain.PatientId;
import com.clinicflow.patient.domain.PatientStatus;
import com.clinicflow.patient.domain.PsychologistId;

import java.time.Instant;
import java.util.Set;

// One page of a psychologist's patients (ADR-029). Every filter is optional: a null search or createdFrom and an
// empty set mean "no filter". The search matches the full name, the preferred name or the email; ids look up
// specific patients, at most one page's worth.
public record ListPatientsQuery(PsychologistId psychologistId, String search, Set<PatientStatus> statuses,
                                Set<ConsentStatus> consentStatuses, Instant createdFrom, Set<PatientId> ids,
                                PageQuery page) {
    public static final int MAX_SEARCH_LENGTH = 100;

    public ListPatientsQuery {
        if (psychologistId == null || page == null) {
            throw new IllegalArgumentException("psychologistId and page are required");
        }
        search = search == null || search.isBlank() ? null : search.strip();
        if (search != null && search.length() > MAX_SEARCH_LENGTH) {
            throw new IllegalArgumentException("q must be at most " + MAX_SEARCH_LENGTH + " characters");
        }
        statuses = statuses == null ? Set.of() : Set.copyOf(statuses);
        consentStatuses = consentStatuses == null ? Set.of() : Set.copyOf(consentStatuses);
        ids = ids == null ? Set.of() : Set.copyOf(ids);
        if (ids.size() > PageQuery.MAX_SIZE) {
            throw new IllegalArgumentException("at most " + PageQuery.MAX_SIZE + " ids can be looked up at once");
        }
    }
}
