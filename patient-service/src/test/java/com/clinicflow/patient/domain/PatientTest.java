package com.clinicflow.patient.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PatientTest {
    @Test
    void changesStateOnTheSameEntityInstance() {
        Instant created = Instant.parse("2026-05-26T10:00:00Z");
        Patient patient = patient(created);

        Patient changed = patient.changeStatus(PatientStatus.ARCHIVED, created.plusSeconds(60));

        assertThat(changed).isSameAs(patient);
        assertThat(patient.status()).isEqualTo(PatientStatus.ARCHIVED);
        assertThat(patient.updatedAt()).isEqualTo(created.plusSeconds(60));
        assertThatThrownBy(() -> patient.changeStatus(null, created.plusSeconds(120)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalidUpdateDoesNotPartiallyMutateEntity() {
        Instant created = Instant.parse("2026-05-26T10:00:00Z");
        Patient patient = patient(created);

        assertThatThrownBy(() -> patient.update(new FullName("Changed Name"), null, null, null, null,
                        null, null, created.plusSeconds(60)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(patient.fullName()).isEqualTo(new FullName("Patient One"));
        assertThat(patient.consentStatus()).isEqualTo(ConsentStatus.GRANTED);
        assertThat(patient.updatedAt()).isEqualTo(created);
    }

    @Test
    void returnsDefensiveValueObjectCopies() {
        Patient patient = patient(Instant.parse("2026-05-26T10:00:00Z"));

        assertThat(patient.id()).isNotSameAs(patient.id());
        assertThat(patient.psychologistId()).isNotSameAs(patient.psychologistId());
        assertThat(patient.email()).isNotSameAs(patient.email());
        assertThat(patient.fullName()).isNotSameAs(patient.fullName());
        assertThat(patient.preferredName().orElseThrow()).isNotSameAs(patient.preferredName().orElseThrow());
        assertThat(patient.birthDate().orElseThrow()).isNotSameAs(patient.birthDate().orElseThrow());
        assertThat(patient.phone().orElseThrow()).isNotSameAs(patient.phone().orElseThrow());
        assertThat(patient.emergencyContact().orElseThrow()).isNotSameAs(patient.emergencyContact().orElseThrow());
        assertThat(patient).isEqualTo(patient);
        assertThat(patient).isEqualTo(Patient.rehydrate(
                patient.id(), patient.psychologistId(), patient.email(), patient.fullName(),
                patient.preferredName().orElseThrow(), patient.birthDate().orElseThrow(),
                patient.phone().orElseThrow(), patient.emergencyContact().orElseThrow(),
                patient.contactPreference(), patient.consentStatus(), patient.consentUpdatedAt(),
                patient.status(), patient.createdAt(), patient.updatedAt()));
        assertThat(patient).isNotEqualTo(patient(Instant.parse("2026-05-26T10:00:00Z")));
        assertThat(patient).isNotEqualTo(new Object());
    }

    @Test
    void supportsOptionalPhoneAndRejectsEveryMissingRequiredField() {
        Instant now = Instant.parse("2026-05-26T10:00:00Z");
        Object[] fields = {
                new PatientId(UUID.randomUUID()),
                new PsychologistId(UUID.randomUUID()),
                new EmailAddress("patient@example.com"),
                new FullName("Patient One"),
                null,
                null,
                null,
                null,
                ContactPreference.EMAIL,
                ConsentStatus.GRANTED,
                now,
                PatientStatus.ACTIVE,
                now,
                now
        };

        Patient withoutPhone = rehydrate(fields);
        assertThat(withoutPhone.phone()).isEmpty();

        int[] requiredIndexes = {0, 1, 2, 3, 8, 9, 10, 11, 12, 13};
        for (int index : requiredIndexes) {
            Object[] invalid = fields.clone();
            invalid[index] = null;
            assertThatThrownBy(() -> rehydrate(invalid))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Patient required fields are missing");
        }
    }

    @Test
    void rejectsInvalidMutationTimes() {
        Instant created = Instant.parse("2026-05-26T10:00:00Z");
        Patient patient = patient(created);

        assertThatThrownBy(() -> patient.update(
                new FullName("Changed"), null, null, null, null, ContactPreference.EMAIL,
                ConsentStatus.GRANTED, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> patient.changeStatus(
                PatientStatus.ARCHIVED, created.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsEachMissingRequiredUpdateField() {
        Instant created = Instant.parse("2026-05-26T10:00:00Z");
        Patient patient = patient(created);
        Instant updateTime = created.plusSeconds(60);

        assertThatThrownBy(() -> patient.update(
                null, null, null, null, null, ContactPreference.EMAIL,
                ConsentStatus.GRANTED, updateTime))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> patient.update(
                new FullName("Changed"), null, null, null, null, null,
                ConsentStatus.GRANTED, updateTime))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> patient.update(
                new FullName("Changed"), null, null, null, null, ContactPreference.EMAIL,
                null, updateTime))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Patient patient(Instant created) {
        return Patient.create(new PatientId(UUID.randomUUID()), new PsychologistId(UUID.randomUUID()),
                new EmailAddress("patient@example.com"), new FullName("Patient One"),
                new PreferredName("Pat"), new BirthDate(LocalDate.of(1990, 1, 1)),
                new PhoneNumber("+351912345678"),
                new EmergencyContact("Contact One", new PhoneNumber("+351923456789"), "Partner"),
                ContactPreference.EMAIL, ConsentStatus.GRANTED, created);
    }

    private Patient rehydrate(Object[] fields) {
        return Patient.rehydrate(
                (PatientId) fields[0],
                (PsychologistId) fields[1],
                (EmailAddress) fields[2],
                (FullName) fields[3],
                (PreferredName) fields[4],
                (BirthDate) fields[5],
                (PhoneNumber) fields[6],
                (EmergencyContact) fields[7],
                (ContactPreference) fields[8],
                (ConsentStatus) fields[9],
                (Instant) fields[10],
                (PatientStatus) fields[11],
                (Instant) fields[12],
                (Instant) fields[13]);
    }
}
