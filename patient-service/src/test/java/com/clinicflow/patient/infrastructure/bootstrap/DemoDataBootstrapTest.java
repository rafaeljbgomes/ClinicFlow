package com.clinicflow.patient.infrastructure.bootstrap;

import com.clinicflow.patient.application.ports.PatientRepository;
import com.clinicflow.patient.domain.ConsentStatus;
import com.clinicflow.patient.domain.ContactPreference;
import com.clinicflow.patient.domain.Patient;
import com.clinicflow.patient.domain.PatientId;
import com.clinicflow.patient.domain.PatientStatus;
import com.clinicflow.patient.domain.PsychologistId;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DemoDataBootstrapTest {
    @Test
    void canBeConstructedBySpring() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(demoBootstrapProperty());
            context.registerBean(PatientRepository.class, InMemoryPatientRepository::new);
            context.registerBean(DemoDataBootstrap.class);

            context.refresh();

            assertThat(context.getBean(DemoDataBootstrap.class)).isNotNull();
        }
    }

    private static MapPropertySource demoBootstrapProperty() {
        return new MapPropertySource("test", Map.of("clinicflow.demo.bootstrap.enabled", "true"));
    }

    @Test
    void seedsDemoPatientsIdempotently() throws Exception {
        InMemoryPatientRepository patients = new InMemoryPatientRepository();
        DemoDataBootstrap bootstrap = new DemoDataBootstrap(
                patients,
                Clock.fixed(Instant.parse("2026-07-05T10:00:00Z"), ZoneOffset.UTC)
        );

        bootstrap.run(null);
        bootstrap.run(null);

        assertThat(patients.records()).hasSize(6);
        assertThat(patients.records().values()).extracting(Patient::status)
                .contains(PatientStatus.ACTIVE, PatientStatus.INACTIVE, PatientStatus.ARCHIVED);
        assertThat(patients.records().values()).extracting(Patient::consentStatus)
                .contains(ConsentStatus.GRANTED, ConsentStatus.PENDING, ConsentStatus.REVOKED);
        assertThat(patients.records().values()).extracting(Patient::contactPreference)
                .contains(ContactPreference.EMAIL, ContactPreference.PHONE, ContactPreference.SMS);
    }

    private static final class InMemoryPatientRepository implements PatientRepository {
        private final Map<PatientId, Patient> records = new LinkedHashMap<>();

        @Override
        public void save(Patient patient) {
            records.put(patient.id(), patient);
        }

        @Override
        public Optional<Patient> findById(PatientId id) {
            return Optional.ofNullable(records.get(id));
        }

        @Override
        public List<Patient> findByPsychologistId(PsychologistId psychologistId) {
            return records.values().stream()
                    .filter(patient -> patient.psychologistId().equals(psychologistId))
                    .toList();
        }

        Map<PatientId, Patient> records() {
            return records;
        }
    }
}
