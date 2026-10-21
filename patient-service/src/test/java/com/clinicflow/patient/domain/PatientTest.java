package com.clinicflow.patient.domain;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class PatientTest {
    private static final Instant CREATED = Instant.parse("2026-05-26T10:00:00Z");

    @Test
    void changeStatusReturnsTheSameEntityInstance() {
        // Arrange
        Patient patient = patient();

        // Act
        Patient changed = patient.changeStatus(PatientStatus.ARCHIVED, CREATED.plusSeconds(60));

        // Assert
        assertThat(changed).isSameAs(patient);
    }

    @Test
    void changeStatusRecordsNewStatusAndUpdateTime() {
        // Arrange
        Patient patient = patient();

        // Act
        patient.changeStatus(PatientStatus.ARCHIVED, CREATED.plusSeconds(60));

        // Assert
        assertThat(patient.status()).isEqualTo(PatientStatus.ARCHIVED);
        assertThat(patient.updatedAt()).isEqualTo(CREATED.plusSeconds(60));
    }

    @Test
    void changeStatusRejectsMissingStatus() {
        // Arrange
        Patient patient = patient();

        // Act
        Throwable thrown = catchThrowable(() -> patient.changeStatus(null, CREATED.plusSeconds(120)));

        // Assert
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void changeStatusRejectsTimeBeforeLastUpdate() {
        // Arrange
        Patient patient = patient();

        // Act
        Throwable thrown = catchThrowable(() -> patient.changeStatus(
                PatientStatus.ARCHIVED, CREATED.minusSeconds(1)));

        // Assert
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateWithoutContactPreferenceAndConsentIsRejected() {
        // Arrange
        Patient patient = patient();

        // Act
        Throwable thrown = catchThrowable(() -> patient.update(new FullName("Changed Name"), null, null, null,
                null, null, null, CREATED.plusSeconds(60)));

        // Assert
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectedUpdateDoesNotPartiallyMutateEntity() {
        // Arrange
        Patient patient = patient();

        // Act
        catchThrowable(() -> patient.update(new FullName("Changed Name"), null, null, null, null,
                null, null, CREATED.plusSeconds(60)));

        // Assert
        assertThat(patient.fullName()).isEqualTo(new FullName("Patient One"));
        assertThat(patient.consentStatus()).isEqualTo(ConsentStatus.GRANTED);
        assertThat(patient.updatedAt()).isEqualTo(CREATED);
    }

    @Test
    void updateRejectsMissingUpdateTime() {
        // Arrange
        Patient patient = patient();

        // Act
        Throwable thrown = catchThrowable(() -> patient.update(
                new FullName("Changed"), null, null, null, null, ContactPreference.EMAIL,
                ConsentStatus.GRANTED, null));

        // Assert
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    static Stream<Arguments> updatesMissingRequiredField() {
        Instant updateTime = CREATED.plusSeconds(60);
        return Stream.of(
                Arguments.of("full name", (Function<Patient, ThrowingCallable>) patient -> () -> patient.update(
                        null, null, null, null, null, ContactPreference.EMAIL,
                        ConsentStatus.GRANTED, updateTime)),
                Arguments.of("contact preference", (Function<Patient, ThrowingCallable>) patient -> () -> patient.update(
                        new FullName("Changed"), null, null, null, null, null,
                        ConsentStatus.GRANTED, updateTime)),
                Arguments.of("consent status", (Function<Patient, ThrowingCallable>) patient -> () -> patient.update(
                        new FullName("Changed"), null, null, null, null, ContactPreference.EMAIL,
                        null, updateTime)));
    }

    @ParameterizedTest(name = "rejects update without {0}")
    @MethodSource("updatesMissingRequiredField")
    void updateRejectsMissingRequiredField(String field, Function<Patient, ThrowingCallable> update) {
        // Arrange
        Patient patient = patient();

        // Act
        Throwable thrown = catchThrowable(update.apply(patient));

        // Assert
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    static Stream<Arguments> valueObjectAccessors() {
        return Stream.of(
                Arguments.of("id", (Function<Patient, Object>) Patient::id),
                Arguments.of("psychologist id", (Function<Patient, Object>) Patient::psychologistId),
                Arguments.of("email", (Function<Patient, Object>) Patient::email),
                Arguments.of("full name", (Function<Patient, Object>) Patient::fullName),
                Arguments.of("preferred name",
                        (Function<Patient, Object>) patient -> patient.preferredName().orElseThrow()),
                Arguments.of("birth date",
                        (Function<Patient, Object>) patient -> patient.birthDate().orElseThrow()),
                Arguments.of("phone",
                        (Function<Patient, Object>) patient -> patient.phone().orElseThrow()),
                Arguments.of("emergency contact",
                        (Function<Patient, Object>) patient -> patient.emergencyContact().orElseThrow()));
    }

    @ParameterizedTest(name = "{0} returns a defensive copy")
    @MethodSource("valueObjectAccessors")
    void accessorReturnsDefensiveValueObjectCopy(String accessor, Function<Patient, Object> read) {
        // Arrange
        Patient patient = patient();

        // Act
        Object firstRead = read.apply(patient);
        Object secondRead = read.apply(patient);

        // Assert
        assertThat(firstRead).isNotSameAs(secondRead);
    }

    @Test
    void patientIsEqualToItself() {
        // Arrange
        Patient patient = patient();

        // Act
        boolean equal = patient.equals(patient);

        // Assert
        assertThat(equal).isTrue();
    }

    @Test
    void rehydratedPatientWithTheSameIdIsEqual() {
        // Arrange
        Patient patient = patient();
        Patient rehydrated = Patient.rehydrate(
                patient.id(), patient.psychologistId(), patient.email(), patient.fullName(),
                patient.preferredName().orElseThrow(), patient.birthDate().orElseThrow(),
                patient.phone().orElseThrow(), patient.emergencyContact().orElseThrow(),
                patient.contactPreference(), patient.consentStatus(), patient.consentUpdatedAt(),
                patient.status(), patient.createdAt(), patient.updatedAt(), 5L);

        // Act
        boolean equal = patient.equals(rehydrated);

        // Assert
        assertThat(equal).isTrue();
    }

    @Test
    void patientsWithDifferentIdsAreNotEqual() {
        // Arrange
        Patient patient = patient();
        Patient other = patient();

        // Act
        boolean equal = patient.equals(other);

        // Assert
        assertThat(equal).isFalse();
    }

    @Test
    void patientIsNotEqualToAnotherType() {
        // Arrange
        Patient patient = patient();

        // Act
        boolean equal = patient.equals(new Object());

        // Assert
        assertThat(equal).isFalse();
    }

    @Test
    void rehydrateAcceptsMissingOptionalProfileFields() {
        // Arrange
        Object[] fields = validRehydrationFields();

        // Act
        Patient patient = rehydrate(fields);

        // Assert
        assertThat(patient.phone()).isEmpty();
    }

    @Test
    void newPatientHasNoVersion() {
        // Act
        Patient patient = patient();

        // Assert
        assertThat(patient.version()).isEmpty();
    }

    @Test
    void newPatientIsAtNoVersion() {
        // Arrange
        Patient patient = patient();

        // Act
        boolean atVersion = patient.isAtVersion(0);

        // Assert
        assertThat(atVersion).isFalse();
    }

    @Test
    void storedPatientIsAtItsVersion() {
        // Arrange
        Patient patient = rehydrate(validRehydrationFields());

        // Act
        boolean atVersion = patient.isAtVersion(5);

        // Assert
        assertThat(atVersion).isTrue();
    }

    @Test
    void storedPatientIsNotAtAnotherVersion() {
        // Arrange
        Patient patient = rehydrate(validRehydrationFields());

        // Act
        boolean atVersion = patient.isAtVersion(4);

        // Assert
        assertThat(atVersion).isFalse();
    }

    static Stream<Arguments> requiredRehydrationFields() {
        return Stream.of(
                Arguments.of("id", 0),
                Arguments.of("psychologist id", 1),
                Arguments.of("email", 2),
                Arguments.of("full name", 3),
                Arguments.of("contact preference", 8),
                Arguments.of("consent status", 9),
                Arguments.of("consent updated at", 10),
                Arguments.of("status", 11),
                Arguments.of("created at", 12),
                Arguments.of("updated at", 13));
    }

    @ParameterizedTest(name = "rejects missing {0}")
    @MethodSource("requiredRehydrationFields")
    void rehydrateRejectsMissingRequiredField(String field, int index) {
        // Arrange
        Object[] fields = validRehydrationFields();
        fields[index] = null;

        // Act
        Throwable thrown = catchThrowable(() -> rehydrate(fields));

        // Assert
        assertThat(thrown)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Patient required fields are missing");
    }

    private Patient patient() {
        return Patient.create(new PatientId(UUID.randomUUID()), new PsychologistId(UUID.randomUUID()),
                new EmailAddress("patient@example.com"), new FullName("Patient One"),
                new PreferredName("Pat"), new BirthDate(LocalDate.of(1990, 1, 1)),
                new PhoneNumber("+351912345678"),
                new EmergencyContact("Contact One", new PhoneNumber("+351923456789"), "Partner"),
                ContactPreference.EMAIL, ConsentStatus.GRANTED, CREATED);
    }

    private static Object[] validRehydrationFields() {
        return new Object[] {
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
                CREATED,
                PatientStatus.ACTIVE,
                CREATED,
                CREATED
        };
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
                (Instant) fields[13],
                5L);
    }
}
