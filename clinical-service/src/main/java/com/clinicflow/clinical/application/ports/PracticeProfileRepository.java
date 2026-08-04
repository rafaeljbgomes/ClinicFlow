package com.clinicflow.clinical.application.ports;

import com.clinicflow.clinical.domain.PracticeProfile;
import com.clinicflow.clinical.domain.PsychologistId;

import java.util.Optional;

public interface PracticeProfileRepository {
    Optional<PracticeProfile> findByPsychologistId(PsychologistId psychologistId);
    void save(PracticeProfile profile);
}
