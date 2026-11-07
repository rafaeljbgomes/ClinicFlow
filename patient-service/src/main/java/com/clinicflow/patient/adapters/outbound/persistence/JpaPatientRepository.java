package com.clinicflow.patient.adapters.outbound.persistence;

import com.clinicflow.patient.application.exceptions.StaleResourceVersionException;
import com.clinicflow.patient.application.ports.PatientRepository;
import com.clinicflow.patient.application.queries.ListPatientsQuery;
import com.clinicflow.patient.application.results.PageResult;
import com.clinicflow.patient.domain.Patient;
import com.clinicflow.patient.domain.PatientId;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Repository
public class JpaPatientRepository implements PatientRepository {
    // Case-insensitive name order; the id breaks ties so rows never repeat or vanish between pages. The V6 index
    // on (psychologist_id, lower(full_name), id) serves this order.
    private static final Sort NAME_ORDER = Sort.by(Sort.Order.asc("fullName").ignoreCase(), Sort.Order.asc("id"));
    private static final char LIKE_ESCAPE = '\\';

    private final SpringDataPatientJpaRepository delegate;
    private final PatientPersistenceMapper mapper;

    public JpaPatientRepository(SpringDataPatientJpaRepository delegate, PatientPersistenceMapper mapper) {
        this.delegate = delegate;
        this.mapper = mapper;
    }

    @Override
    public Patient save(Patient patient) {
        try {
            // Flush here so a concurrent write surfaces as a version conflict, not a commit failure.
            return mapper.toDomain(delegate.saveAndFlush(mapper.toJpa(patient)));
        } catch (OptimisticLockingFailureException ex) {
            throw new StaleResourceVersionException();
        }
    }

    @Override
    public Optional<Patient> findById(PatientId id) {
        return delegate.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public PageResult<Patient> findPage(ListPatientsQuery query) {
        Page<JpaPatientEntity> page = delegate.findAll(matching(query),
                PageRequest.of(query.page().number(), query.page().size(), NAME_ORDER));
        return new PageResult<>(page.getContent().stream().map(mapper::toDomain).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements());
    }

    private static Specification<JpaPatientEntity> matching(ListPatientsQuery query) {
        String pattern = query.search() == null ? null : containsPattern(query.search());
        return (root, criteria, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("psychologistId"), query.psychologistId().value()));
            if (pattern != null) {
                predicates.add(builder.or(
                        contains(builder, root.get("fullName"), pattern),
                        contains(builder, root.get("preferredName"), pattern),
                        contains(builder, root.get("email"), pattern)));
            }
            if (!query.statuses().isEmpty()) {
                predicates.add(root.get("status").in(query.statuses()));
            }
            if (!query.consentStatuses().isEmpty()) {
                predicates.add(root.get("consentStatus").in(query.consentStatuses()));
            }
            if (query.createdFrom() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"), query.createdFrom()));
            }
            if (!query.ids().isEmpty()) {
                predicates.add(root.get("id").in(query.ids().stream().map(PatientId::value).toList()));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Predicate contains(CriteriaBuilder builder, Expression<String> column, String pattern) {
        return builder.like(builder.lower(column), pattern, LIKE_ESCAPE);
    }

    // The search is matched literally: LIKE wildcards and the escape character in it are escaped.
    static String containsPattern(String search) {
        String escaped = search.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
