package com.clinicflow.clinical.adapters.outbound.persistence;

import com.clinicflow.clinical.application.ports.PracticeProfileRepository;
import com.clinicflow.clinical.domain.PracticeProfile;
import com.clinicflow.clinical.domain.PsychologistId;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class JpaPracticeProfileRepository implements PracticeProfileRepository {
    private final SpringDataPracticeProfileJpaRepository delegate;
    private final ClinicalPersistenceMapper mapper;

    public JpaPracticeProfileRepository(SpringDataPracticeProfileJpaRepository delegate,
                                        ClinicalPersistenceMapper mapper) {
        this.delegate = delegate;
        this.mapper = mapper;
    }

    @Override
    public Optional<PracticeProfile> findByPsychologistId(PsychologistId psychologistId) {
        return delegate.findById(psychologistId.value()).map(mapper::toDomain);
    }

    @Override
    public void save(PracticeProfile profile) {
        delegate.save(mapper.toJpa(profile));
    }
}
