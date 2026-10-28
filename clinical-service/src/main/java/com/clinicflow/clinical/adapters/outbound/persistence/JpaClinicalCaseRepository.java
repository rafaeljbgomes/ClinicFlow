package com.clinicflow.clinical.adapters.outbound.persistence;

import com.clinicflow.clinical.application.exceptions.StaleResourceVersionException;
import com.clinicflow.clinical.application.ports.ClinicalCaseRepository;
import com.clinicflow.clinical.application.queries.ListClinicalCasesQuery;
import com.clinicflow.clinical.application.results.PageResult;
import com.clinicflow.clinical.domain.ClinicalCase;
import com.clinicflow.clinical.domain.ClinicalCaseId;
import com.clinicflow.clinical.domain.ClinicalCaseStatus;
import com.clinicflow.clinical.domain.PatientId;
import com.clinicflow.clinical.domain.PsychologistId;
import jakarta.persistence.criteria.Predicate;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public class JpaClinicalCaseRepository implements ClinicalCaseRepository {
    // The id breaks openedAt ties so rows never repeat or vanish between pages.
    private static final Sort OPENED_ORDER = Sort.by(Sort.Direction.DESC, "openedAt", "id");

    private final SpringDataClinicalCaseJpaRepository delegate;
    private final ClinicalPersistenceMapper mapper;

    public JpaClinicalCaseRepository(SpringDataClinicalCaseJpaRepository delegate,
                                     ClinicalPersistenceMapper mapper) {
        this.delegate = delegate;
        this.mapper = mapper;
    }

    @Override
    public Optional<ClinicalCase> findById(ClinicalCaseId id) {
        return delegate.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public PageResult<ClinicalCase> findPage(ListClinicalCasesQuery query) {
        Page<JpaClinicalCaseEntity> page = delegate.findAll(matching(query),
                PageRequest.of(query.page().number(), query.page().size(), OPENED_ORDER));
        return new PageResult<>(page.getContent().stream().map(mapper::toDomain).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements());
    }

    private static Specification<JpaClinicalCaseEntity> matching(ListClinicalCasesQuery query) {
        UUID psychologistId = query.psychologistId().value();
        UUID patientId = query.patientId() == null ? null : query.patientId().value();
        Set<ClinicalCaseStatus> statuses = query.statuses();
        return (root, criteria, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("psychologistId"), psychologistId));
            if (patientId != null) {
                predicates.add(builder.equal(root.get("patientId"), patientId));
            }
            if (!statuses.isEmpty()) {
                predicates.add(root.get("status").in(statuses));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    @Override
    public List<ClinicalCase> findByPsychologistIdAndPatientId(PsychologistId psychologistId, PatientId patientId) {
        return delegate.findByPsychologistIdAndPatientId(psychologistId.value(), patientId.value())
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<ClinicalCase> findOpenByPsychologistIdAndPatientId(PsychologistId psychologistId,
                                                                       PatientId patientId) {
        return delegate.findFirstByPsychologistIdAndPatientIdAndStatusNot(
                psychologistId.value(), patientId.value(), ClinicalCaseStatus.DISCHARGED).map(mapper::toDomain);
    }

    @Override
    public boolean existsOpenByPsychologistIdAndPatientId(PsychologistId psychologistId, PatientId patientId) {
        return delegate.existsByPsychologistIdAndPatientIdAndStatusNot(
                psychologistId.value(), patientId.value(), ClinicalCaseStatus.DISCHARGED);
    }

    @Override
    public ClinicalCase save(ClinicalCase clinicalCase) {
        try {
            // Flush here so a concurrent write surfaces as a version conflict, not a commit failure.
            return mapper.toDomain(delegate.saveAndFlush(mapper.toJpa(clinicalCase)));
        } catch (OptimisticLockingFailureException ex) {
            throw new StaleResourceVersionException();
        }
    }
}
