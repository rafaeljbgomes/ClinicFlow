package com.clinicflow.appointment.adapters.outbound.http;

import com.clinicflow.appointment.application.exceptions.OwnershipCheckUnavailableException;
import com.clinicflow.appointment.domain.PatientId;
import com.clinicflow.appointment.domain.PsychologistId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpPatientDirectoryTest {
    private static final String BASE_URL = "http://patient-service:8082";
    private static final PsychologistId PSYCHOLOGIST_ID =
            new PsychologistId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
    private static final PatientId PATIENT_ID =
            new PatientId(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
    private static final String PATIENT_URL = BASE_URL + "/patients/" + PATIENT_ID.value();

    private MockRestServiceServer patientService;
    private HttpPatientDirectory directory;

    @BeforeEach
    void bindClient() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        patientService = MockRestServiceServer.bindTo(builder).build();
        directory = new HttpPatientDirectory(builder.build());
    }

    @AfterEach
    void clearCallerContext() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @Test
    void confirmsPatientThatPatientServiceAccepts() {
        // Arrange
        authenticateAs(PSYCHOLOGIST_ID);
        patientService.expect(requestTo(PATIENT_URL)).andRespond(withSuccess());

        // Act
        boolean owned = directory.isOwnedBy(PATIENT_ID, PSYCHOLOGIST_ID);

        // Assert
        assertThat(owned).isTrue();
    }

    @Test
    void asksWithHeadSoNoPatientDetailsAreTransferred() {
        // Arrange
        authenticateAs(PSYCHOLOGIST_ID);
        patientService.expect(requestTo(PATIENT_URL)).andExpect(method(HttpMethod.HEAD)).andRespond(withSuccess());

        // Act
        directory.isOwnedBy(PATIENT_ID, PSYCHOLOGIST_ID);

        // Assert
        patientService.verify();
    }

    @Test
    void forwardsTheCallerBearerToken() {
        // Arrange
        authenticateAs(PSYCHOLOGIST_ID);
        patientService.expect(requestTo(PATIENT_URL))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer caller-token"))
                .andRespond(withSuccess());

        // Act
        directory.isOwnedBy(PATIENT_ID, PSYCHOLOGIST_ID);

        // Assert
        patientService.verify();
    }

    @Test
    void forwardsTheCurrentCorrelationId() {
        // Arrange
        authenticateAs(PSYCHOLOGIST_ID);
        MDC.put("correlation.id", "corr-123");
        patientService.expect(requestTo(PATIENT_URL))
                .andExpect(header("X-Correlation-Id", "corr-123"))
                .andRespond(withSuccess());

        // Act
        directory.isOwnedBy(PATIENT_ID, PSYCHOLOGIST_ID);

        // Assert
        patientService.verify();
    }

    @ParameterizedTest(name = "status {0} means not owned")
    @ValueSource(ints = {403, 404})
    void rejectsPatientThatPatientServiceRefuses(int status) {
        // Arrange
        authenticateAs(PSYCHOLOGIST_ID);
        patientService.expect(requestTo(PATIENT_URL)).andRespond(withStatus(HttpStatus.valueOf(status)));

        // Act
        boolean owned = directory.isOwnedBy(PATIENT_ID, PSYCHOLOGIST_ID);

        // Assert
        assertThat(owned).isFalse();
    }

    @ParameterizedTest(name = "status {0} fails closed")
    @ValueSource(ints = {401, 500, 503})
    void failsClosedWhenPatientServiceCannotDecide(int status) {
        // Arrange
        authenticateAs(PSYCHOLOGIST_ID);
        patientService.expect(requestTo(PATIENT_URL)).andRespond(withStatus(HttpStatus.valueOf(status)));

        // Act
        Throwable thrown = catchThrowable(() -> directory.isOwnedBy(PATIENT_ID, PSYCHOLOGIST_ID));

        // Assert
        assertThat(thrown).isInstanceOf(OwnershipCheckUnavailableException.class);
    }

    @Test
    void failsClosedWhenPatientServiceIsUnreachable() {
        // Arrange
        authenticateAs(PSYCHOLOGIST_ID);
        patientService.expect(requestTo(PATIENT_URL)).andRespond(withException(new IOException("connect timed out")));

        // Act
        Throwable thrown = catchThrowable(() -> directory.isOwnedBy(PATIENT_ID, PSYCHOLOGIST_ID));

        // Assert
        assertThat(thrown).isInstanceOf(OwnershipCheckUnavailableException.class);
    }

    @Test
    void failsClosedWithoutACallerToken() {
        // Act
        Throwable thrown = catchThrowable(() -> directory.isOwnedBy(PATIENT_ID, PSYCHOLOGIST_ID));

        // Assert
        assertThat(thrown).isInstanceOf(OwnershipCheckUnavailableException.class);
    }

    @Test
    void neverForwardsATokenThatBelongsToAnotherPsychologist() {
        // Arrange
        authenticateAs(new PsychologistId(UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc")));
        patientService.expect(never(), requestTo(PATIENT_URL));

        // Act
        catchThrowable(() -> directory.isOwnedBy(PATIENT_ID, PSYCHOLOGIST_ID));

        // Assert
        patientService.verify();
    }

    private static void authenticateAs(PsychologistId psychologistId) {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("caller-token")
                .header("alg", "none")
                .issuedAt(now).expiresAt(now.plusSeconds(300))
                .subject(psychologistId.value().toString())
                .claim("roles", List.of("PSYCHOLOGIST"))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
