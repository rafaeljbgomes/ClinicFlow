package com.clinicflow.appointment.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValueObjectsTest {
    @Test
    void validatesAppointmentValueObjects() {
        assertThat(new AppointmentId(UUID.randomUUID())).isNotNull();
        assertThat(new PsychologistId(UUID.randomUUID())).isNotNull();
        assertThat(new PatientId(UUID.randomUUID())).isNotNull();
        assertThat(new DomainEventId(UUID.randomUUID())).isNotNull();
        assertThat(new AppointmentDate(Instant.EPOCH).value()).isEqualTo(Instant.EPOCH);
        assertThat(new CancellationReason("  Reason  ").value()).isEqualTo("Reason");
        assertThat(new CancellationActor(" Psychologist ").value()).isEqualTo("psychologist");
    }

    @Test
    void rejectsInvalidAppointmentValues() {
        assertThatThrownBy(() -> new AppointmentId(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PsychologistId(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PatientId(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DomainEventId(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AppointmentDate(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CancellationReason(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CancellationReason(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CancellationActor(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CancellationActor(" ")).isInstanceOf(IllegalArgumentException.class);
    }
}
