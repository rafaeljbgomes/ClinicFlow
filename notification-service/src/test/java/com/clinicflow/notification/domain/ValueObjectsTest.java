package com.clinicflow.notification.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValueObjectsTest {
    @Test
    void validatesAndNormalizesNotificationValueObjects() {
        assertThat(new NotificationId(UUID.randomUUID())).isNotNull();
        assertThat(new EventId(UUID.randomUUID())).isNotNull();
        assertThat(new EventType("Custom.Event-1").value()).isEqualTo("Custom.Event-1");
        assertThat(new RecipientAddress(" PATIENT@Example.COM ").value()).isEqualTo("patient@example.com");
        assertThat(new NotificationSubject(" Subject ").value()).isEqualTo("Subject");
        assertThat(new NotificationMessage(" Message ").value()).isEqualTo("Message");
        assertThat(new FailureReason(" Failure ").value()).isEqualTo("Failure");
    }

    @Test
    void rejectsInvalidNotificationValues() {
        assertThatThrownBy(() -> new NotificationId(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EventId(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EventType(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EventType("bad event")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RecipientAddress(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RecipientAddress("invalid")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NotificationSubject(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NotificationSubject(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NotificationMessage(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NotificationMessage(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FailureReason(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FailureReason(" ")).isInstanceOf(IllegalArgumentException.class);
    }
}
