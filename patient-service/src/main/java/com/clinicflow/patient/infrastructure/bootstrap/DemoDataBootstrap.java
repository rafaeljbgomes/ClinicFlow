package com.clinicflow.patient.infrastructure.bootstrap;

import com.clinicflow.patient.application.ports.PatientRepository;
import com.clinicflow.patient.domain.BirthDate;
import com.clinicflow.patient.domain.ConsentStatus;
import com.clinicflow.patient.domain.ContactPreference;
import com.clinicflow.patient.domain.EmailAddress;
import com.clinicflow.patient.domain.EmergencyContact;
import com.clinicflow.patient.domain.FullName;
import com.clinicflow.patient.domain.Patient;
import com.clinicflow.patient.domain.PatientId;
import com.clinicflow.patient.domain.PatientStatus;
import com.clinicflow.patient.domain.PhoneNumber;
import com.clinicflow.patient.domain.PreferredName;
import com.clinicflow.patient.domain.PsychologistId;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "clinicflow.demo.bootstrap", name = "enabled", havingValue = "true")
public class DemoDataBootstrap implements ApplicationRunner {
    private static final UUID PSYCHOLOGIST_SOFIA = uuid("00000000-0000-4000-8000-000000000011");
    private static final UUID PSYCHOLOGIST_MIGUEL = uuid("00000000-0000-4000-8000-000000000012");

    private final PatientRepository patients;
    private final Clock clock;

    @Autowired
    public DemoDataBootstrap(PatientRepository patients) {
        this(patients, Clock.systemUTC());
    }

    DemoDataBootstrap(PatientRepository patients, Clock clock) {
        this.patients = patients;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.SECONDS);
        for (DemoPatient patient : demoPatients()) {
            patients.save(Patient.rehydrate(
                    new PatientId(patient.id()),
                    new PsychologistId(patient.psychologistId()),
                    new EmailAddress(patient.email()),
                    new FullName(patient.fullName()),
                    preferredName(patient.preferredName()),
                    birthDate(patient.birthDate()),
                    phone(patient.phone()),
                    emergencyContact(patient.emergencyContactName(), patient.emergencyContactPhone(),
                            patient.emergencyContactRelationship()),
                    patient.contactPreference(),
                    patient.consentStatus(),
                    now.minus(30, ChronoUnit.DAYS),
                    patient.status(),
                    now.minus(45, ChronoUnit.DAYS),
                    now
            ));
        }
    }

    private static List<DemoPatient> demoPatients() {
        return List.of(
                new DemoPatient(uuid("10000000-0000-4000-8000-000000000001"), PSYCHOLOGIST_SOFIA,
                        "ana.martins@demo.clinicflow.local", "Ana Martins", "Ana",
                        LocalDate.of(1992, 4, 12), "+351912345678", "Carla Martins",
                        "+351913456789", "Irma", ContactPreference.EMAIL, ConsentStatus.GRANTED,
                        PatientStatus.ACTIVE),
                new DemoPatient(uuid("10000000-0000-4000-8000-000000000002"), PSYCHOLOGIST_SOFIA,
                        "joao.ferreira@demo.clinicflow.local", "Joao Ferreira", null,
                        LocalDate.of(1985, 9, 2), "+351914567890", "Maria Ferreira",
                        "+351915678901", "Esposa", ContactPreference.SMS, ConsentStatus.PENDING,
                        PatientStatus.ACTIVE),
                new DemoPatient(uuid("10000000-0000-4000-8000-000000000003"), PSYCHOLOGIST_SOFIA,
                        "beatriz.costa@demo.clinicflow.local", "Beatriz Costa", "Bia",
                        LocalDate.of(2001, 1, 23), "+351916789012", null,
                        null, null, ContactPreference.PHONE, ConsentStatus.GRANTED,
                        PatientStatus.INACTIVE),
                new DemoPatient(uuid("10000000-0000-4000-8000-000000000004"), PSYCHOLOGIST_MIGUEL,
                        "rui.nunes@demo.clinicflow.local", "Rui Nunes", null,
                        LocalDate.of(1978, 11, 8), "+351917890123", "Helena Nunes",
                        "+351918901234", "Companheira", ContactPreference.EMAIL, ConsentStatus.GRANTED,
                        PatientStatus.ACTIVE),
                new DemoPatient(uuid("10000000-0000-4000-8000-000000000005"), PSYCHOLOGIST_MIGUEL,
                        "ines.rocha@demo.clinicflow.local", "Ines Rocha", "Ines",
                        LocalDate.of(1996, 7, 18), "+351919012345", "Paulo Rocha",
                        "+351911223344", "Pai", ContactPreference.SMS, ConsentStatus.REVOKED,
                        PatientStatus.ARCHIVED),
                new DemoPatient(uuid("10000000-0000-4000-8000-000000000006"), PSYCHOLOGIST_MIGUEL,
                        "tiago.lopes@demo.clinicflow.local", "Tiago Lopes", null,
                        LocalDate.of(1989, 3, 30), null, null,
                        null, null, ContactPreference.PHONE, ConsentStatus.PENDING,
                        PatientStatus.ACTIVE)
        );
    }

    private static PreferredName preferredName(String value) {
        return value == null ? null : new PreferredName(value);
    }

    private static BirthDate birthDate(LocalDate value) {
        return value == null ? null : new BirthDate(value);
    }

    private static PhoneNumber phone(String value) {
        return value == null ? null : new PhoneNumber(value);
    }

    private static EmergencyContact emergencyContact(String name, String phone, String relationship) {
        return name == null ? null : new EmergencyContact(name, new PhoneNumber(phone), relationship);
    }

    private static UUID uuid(String value) {
        return UUID.fromString(value);
    }

    private record DemoPatient(UUID id, UUID psychologistId, String email, String fullName, String preferredName,
                               LocalDate birthDate, String phone, String emergencyContactName,
                               String emergencyContactPhone, String emergencyContactRelationship,
                               ContactPreference contactPreference, ConsentStatus consentStatus,
                               PatientStatus status) {
    }
}
