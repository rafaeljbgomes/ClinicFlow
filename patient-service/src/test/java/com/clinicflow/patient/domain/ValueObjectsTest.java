package com.clinicflow.patient.domain;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class ValueObjectsTest {
    private static final PhoneNumber CONTACT_PHONE = new PhoneNumber("+351923456789");

    @Test
    void patientIdWrapsTheSuppliedUuid() {
        // Arrange
        UUID id = UUID.randomUUID();

        // Act
        PatientId patientId = new PatientId(id);

        // Assert
        assertThat(patientId.value()).isEqualTo(id);
    }

    @Test
    void psychologistIdWrapsTheSuppliedUuid() {
        // Arrange
        UUID id = UUID.randomUUID();

        // Act
        PsychologistId psychologistId = new PsychologistId(id);

        // Assert
        assertThat(psychologistId.value()).isEqualTo(id);
    }

    @Test
    void emailIsTrimmedAndLowerCased() {
        // Act
        EmailAddress email = new EmailAddress(" PATIENT@Example.COM ");

        // Assert
        assertThat(email.value()).isEqualTo("patient@example.com");
    }

    @Test
    void fullNameIsTrimmed() {
        // Act
        FullName fullName = new FullName("  Patient One ");

        // Assert
        assertThat(fullName.value()).isEqualTo("Patient One");
    }

    @Test
    void phoneNumberIsNormalizedToE164() {
        // Act
        PhoneNumber phone = new PhoneNumber("+351 912 345 678");

        // Assert
        assertThat(phone.value()).isEqualTo("+351912345678");
    }

    @Test
    void preferredNameIsTrimmed() {
        // Act
        PreferredName preferredName = new PreferredName("  Pat ");

        // Assert
        assertThat(preferredName.value()).isEqualTo("Pat");
    }

    @Test
    void birthDateAcceptsTheEarliestSupportedDate() {
        // Act
        BirthDate birthDate = new BirthDate(LocalDate.of(1900, 1, 1));

        // Assert
        assertThat(birthDate.value()).isEqualTo(LocalDate.of(1900, 1, 1));
    }

    @Test
    void birthDateAcceptsToday() {
        // Arrange
        LocalDate today = LocalDate.now();

        // Act
        BirthDate birthDate = new BirthDate(today);

        // Assert
        assertThat(birthDate.value()).isEqualTo(today);
    }

    @Test
    void emergencyContactTrimsNameAndRelationship() {
        // Act
        EmergencyContact contact = new EmergencyContact("  Contact One ", CONTACT_PHONE, "  Partner ");

        // Assert
        assertThat(contact.name()).isEqualTo("Contact One");
        assertThat(contact.relationship()).isEqualTo("Partner");
    }

    @ParameterizedTest(name = "relationship [{0}] becomes null")
    @NullSource
    @ValueSource(strings = " ")
    void emergencyContactNormalizesMissingRelationshipToNull(String relationship) {
        // Act
        EmergencyContact contact = new EmergencyContact("Contact Two", CONTACT_PHONE, relationship);

        // Assert
        assertThat(contact.relationship()).isNull();
    }

    static Stream<Arguments> invalidValues() {
        return Stream.of(
                Arguments.of("missing patient id", (ThrowingCallable) () -> new PatientId(null)),
                Arguments.of("missing psychologist id", (ThrowingCallable) () -> new PsychologistId(null)),
                Arguments.of("missing email", (ThrowingCallable) () -> new EmailAddress(null)),
                Arguments.of("malformed email", (ThrowingCallable) () -> new EmailAddress("invalid")),
                Arguments.of("overlong email",
                        (ThrowingCallable) () -> new EmailAddress("a".repeat(310) + "@example.com")),
                Arguments.of("missing full name", (ThrowingCallable) () -> new FullName(null)),
                Arguments.of("blank full name", (ThrowingCallable) () -> new FullName(" ")),
                Arguments.of("overlong full name", (ThrowingCallable) () -> new FullName("N".repeat(161))),
                Arguments.of("missing phone", (ThrowingCallable) () -> new PhoneNumber(null)),
                Arguments.of("phone without country code", (ThrowingCallable) () -> new PhoneNumber("912345678")),
                Arguments.of("too short phone", (ThrowingCallable) () -> new PhoneNumber("+351123")),
                Arguments.of("missing preferred name", (ThrowingCallable) () -> new PreferredName(null)),
                Arguments.of("blank preferred name", (ThrowingCallable) () -> new PreferredName(" ")),
                Arguments.of("overlong preferred name",
                        (ThrowingCallable) () -> new PreferredName("P".repeat(121))),
                Arguments.of("missing birth date", (ThrowingCallable) () -> new BirthDate(null)),
                Arguments.of("birth date before 1900",
                        (ThrowingCallable) () -> new BirthDate(LocalDate.of(1899, 12, 31))),
                Arguments.of("future birth date",
                        (ThrowingCallable) () -> new BirthDate(LocalDate.now().plusDays(1))),
                Arguments.of("missing emergency contact name",
                        (ThrowingCallable) () -> new EmergencyContact(null, CONTACT_PHONE, null)),
                Arguments.of("blank emergency contact name",
                        (ThrowingCallable) () -> new EmergencyContact(" ", CONTACT_PHONE, null)),
                Arguments.of("missing emergency contact phone",
                        (ThrowingCallable) () -> new EmergencyContact("Contact", null, null)),
                Arguments.of("overlong emergency contact name",
                        (ThrowingCallable) () -> new EmergencyContact("C".repeat(161), CONTACT_PHONE, null)),
                Arguments.of("overlong emergency contact relationship",
                        (ThrowingCallable) () -> new EmergencyContact("Contact", CONTACT_PHONE, "R".repeat(81))));
    }

    @ParameterizedTest(name = "rejects {0}")
    @MethodSource("invalidValues")
    void rejectsInvalidValue(String scenario, ThrowingCallable construct) {
        // Act
        Throwable thrown = catchThrowable(construct);

        // Assert
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }
}
