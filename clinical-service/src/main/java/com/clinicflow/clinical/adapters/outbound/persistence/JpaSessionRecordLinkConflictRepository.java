package com.clinicflow.clinical.adapters.outbound.persistence;

import com.clinicflow.clinical.application.ports.SessionRecordLinkConflictRepository;
import com.clinicflow.clinical.domain.SessionRecordLinkConflict;
import org.springframework.stereotype.Repository;

@Repository
public class JpaSessionRecordLinkConflictRepository implements SessionRecordLinkConflictRepository {
    private final SpringDataSessionRecordLinkConflictJpaRepository delegate;
    private final ClinicalPersistenceMapper mapper;

    public JpaSessionRecordLinkConflictRepository(SpringDataSessionRecordLinkConflictJpaRepository delegate,
                                                  ClinicalPersistenceMapper mapper) {
        this.delegate = delegate;
        this.mapper = mapper;
    }

    @Override
    public void save(SessionRecordLinkConflict conflict) {
        // Flush now: the signed-record trigger looks for this row when the link is released afterwards.
        delegate.saveAndFlush(mapper.toJpa(conflict));
    }
}
