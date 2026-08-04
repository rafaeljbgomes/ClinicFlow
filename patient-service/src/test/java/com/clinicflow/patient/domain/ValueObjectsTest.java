package com.clinicflow.patient.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValueObjectsTest {
    @Test
    void validatesAndNormalizesPatientValueObjects() {
        assertThat(new PatientId(UUID.randomUUID())).isNotNull();
        assertThat(new PsychologistId(UUID.randomUUID())).isNotNull();
        assertThat(new EmailAddress(" PATIENT@Example.COM ").value()).isEqualTo("patient@example.com");
        assertThat(new FullName("  Patient One ").value()).isEqualTo("Patient One");
        assertThat(new PhoneNumber("+351 912 345 678").value()).isEqualTo("+351912345678");
    }

    @Test
    void rejectsInvalidPatientValues() {
        assertThatThrownBy(() -> new PatientId(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PsychologistId(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EmailAddress(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EmailAddress("invalid")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FullName(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FullName(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PhoneNumber(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PhoneNumber("912345678")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PhoneNumber("+351123")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validatesAndNormalizesExtendedProfileValueObjects() {
        PhoneNumber phone = new PhoneNumber("+351 923 456 789");
        LocalDate today = LocalDate.now();

        assertThat(new PreferredName("  Pat ").value()).isEqualTo("Pat");
        assertThat(new BirthDate(LocalDate.of(1900, 1, 1)).value())
                .isEqualTo(LocalDate.of(1900, 1, 1));
        assertThat(new BirthDate(today).value()).isEqualTo(today);

        EmergencyContact contact = new EmergencyContact("  Contact One ", phone, "  Partner ");
        assertThat(contact.name()).isEqualTo("Contact One");
        assertThat(contact.relationship()).isEqualTo("Partner");
        assertThat(new EmergencyContact("Contact Two", phone, " ").relationship()).isNull();
        assertThat(new EmergencyContact("Contact Three", phone, null).relationship()).isNull();
    }

    @Test
    void rejectsInvalidExtendedProfileValueObjects() {
        PhoneNumber phone = new PhoneNumber("+351923456789");

        assertThatThrownBy(() -> new PreferredName(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PreferredName(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PreferredName("P".repeat(121)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BirthDate(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BirthDate(LocalDate.of(1899, 12, 31)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BirthDate(LocalDate.now().plusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EmergencyContact(null, phone, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EmergencyContact(" ", phone, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EmergencyContact("Contact", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EmergencyContact("C".repeat(161), phone, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EmergencyContact("Contact", phone, "R".repeat(81)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EmailAddress("a".repeat(310) + "@example.com"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FullName("N".repeat(161)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
