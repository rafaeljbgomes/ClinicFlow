package com.clinicflow.appointment.infrastructure.http;

import com.clinicflow.appointment.support.StubHttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class ServiceClientConfigTest {
    private StubHttpServer patientService;
    private StubHttpServer elsewhere;

    @BeforeEach
    void startServers() {
        patientService = StubHttpServer.start();
        elsewhere = StubHttpServer.start();
    }

    @AfterEach
    void stopServers() {
        patientService.close();
        elsewhere.close();
    }

    @Test
    void doesNotFollowRedirectsThatWouldCarryTheCallerToken() {
        // Arrange
        patientService.reply("HEAD", "/patients/1", 302, Map.of("Location", elsewhere.baseUrl() + "/collect"), "");
        RestClient client = new ServiceClientConfig().patientServiceClient(RestClient.builder(), properties());

        // Act
        client.head().uri("/patients/1").headers(headers -> headers.setBearerAuth("caller-token"))
                .exchange((request, response) -> response.getStatusCode());

        // Assert
        assertThat(elsewhere.requests()).isEmpty();
    }

    @Test
    void returnsTheRedirectStatusInsteadOfFollowingIt() {
        // Arrange
        patientService.reply("HEAD", "/patients/1", 302, Map.of("Location", elsewhere.baseUrl() + "/collect"), "");
        RestClient client = new ServiceClientConfig().patientServiceClient(RestClient.builder(), properties());

        // Act
        HttpStatusCode status = client.head().uri("/patients/1")
                .exchange((request, response) -> response.getStatusCode());

        // Assert
        assertThat(status.value()).isEqualTo(302);
    }

    @Test
    void propertiesRequireThePatientServiceBaseUrl() {
        // Act
        Throwable thrown = catchThrowable(() -> new ServiceClientProperties(
                new ServiceClientProperties.Endpoint(" "), null, null));

        // Assert
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void propertiesDefaultToShortTimeouts() {
        // Act
        var properties = new ServiceClientProperties(new ServiceClientProperties.Endpoint("http://patient"), null, null);

        // Assert
        assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(1));
        assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(2));
    }

    private ServiceClientProperties properties() {
        return new ServiceClientProperties(new ServiceClientProperties.Endpoint(patientService.baseUrl()),
                Duration.ofSeconds(1), Duration.ofSeconds(2));
    }
}
