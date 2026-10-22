package com.clinicflow.clinical.adapters.outbound.persistence;

import com.clinicflow.clinical.application.exceptions.StaleResourceVersionException;
import com.clinicflow.clinical.application.ports.PracticeProfileRepository;
import com.clinicflow.clinical.domain.PracticeProfile;
import com.clinicflow.clinical.domain.PsychologistId;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
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
    public PracticeProfile save(PracticeProfile profile) {
        try {
            // Flush here so a concurrent write surfaces as a version conflict, not a commit failure.
            return mapper.toDomain(delegate.saveAndFlush(mapper.toJpa(profile)));
        } catch (OptimisticLockingFailureException ex) {
            throw new StaleResourceVersionException();
        } catch (DataIntegrityViolationException ex) {
            // The psychologist id is the key, so a concurrent first save of the same profile collides here.
            if (profile.version().isEmpty()) {
                throw new StaleResourceVersionException();
            }
            throw ex;
        }
    }
}
